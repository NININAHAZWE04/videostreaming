# Technical Architecture

## Overview

```
┌─────────────────────────────────────────────────────────────┐
│                     CLIENT LAYER                            │
│  ┌───────────────┐  ┌───────────────┐  ┌────────────────┐  │
│  │ Web Client    │  │  Admin Panel  │  │  Swing Client  │  │
│  │ React :5173   │  │  React :5174  │  │  (VLC)         │  │
│  └───────┬───────┘  └───────┬───────┘  └───────┬────────┘  │
└──────────┼───────────────────┼──────────────────┼───────────┘
           │ HTTP              │ HTTP+Bearer       │ RMI
┌──────────▼───────────────────▼──────────────────▼───────────┐
│                     API LAYER                               │
│  ┌─────────────────────────────────────────────────────┐   │
│  │             AdminApiServer (:8081)                  │   │
│  │  /api/videos   /api/categories  /api/health         │   │
│  │  /api/events   /api/logs        /api/download        │   │
│  │  /api/auth/*   /api/admin/*                          │   │
│  └───────────────────────┬─────────────────────────────┘   │
│  ┌────────────────────────▼─────────────────────────────┐  │
│  │             DiaryApiServer (:8080)                   │  │
│  │  GET /api/videos  GET /api/categories  GET /api/health│  │
│  └────────────────────────┬──────────────────────────────┘  │
└───────────────────────────┼──────────────────────────────────┘
                            │ RMI
┌───────────────────────────▼──────────────────────────────────┐
│                     CORE LAYER                              │
│  ┌───────────────────────────────┐                          │
│  │   DiaryServer (RMI :1099)     │                          │
│  │   DiaryImpl + H2 Database     │                          │
│  └───────────────────────────────┘                          │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐                  │
│  │ Stream   │  │ Stream   │  │ Stream   │  (1 per video)   │
│  │ :5000    │  │ :5001    │  │ :5002    │                  │
│  └──────────┘  └──────────┘  └──────────┘                  │
└──────────────────────────────────────────────────────────────┘
```

## Components

### DiaryServer (`src/diary/`)

Central RMI directory. Since v2, persisted in H2 instead of a `ConcurrentHashMap`.

| Class | Role |
|-------|------|
| `Diary.java` | RMI interface (network contract) |
| `DiaryImpl.java` | Implementation with H2 persistence |
| `DiaryServer.java` | Entry point — starts the RMI registry |
| `VideoInfo.java` | Serializable RMI DTO — enriched with all metadata |

### StreamingServer (`src/server/`)

One process per video being streamed.

- **HTTP Range**: statuses 200/206/416, smooth seek
- **ffprobe**: automatic extraction of duration/resolution/codec/fps/bitrate at startup
- **Thumbnails**: extraction via ffmpeg, disk cache
- **Rate limiting**: max simultaneous connections per IP
- **Stats**: served bytes tracked, recorded in the database via `view_events`
- **SSE**: notifies `stream_started` / `stream_stopped`

### H2 Database (`src/db/`)

Schema auto-created on first startup. Migration via `ALTER TABLE IF NOT EXISTS`.

```sql
users          -- accounts (email, password PBKDF2, role, avatar)
subscriptions  -- plans (trial 14d, monthly, annual, free)
payments       -- cash payments (pending → approved/rejected)
download_tokens -- single-use tokens, 2h expiration
categories     -- video categories with color
videos         -- complete metadata + is_free + download_count
view_events    -- anonymized viewing logs (SHA-256 hashed IP)
schema_version -- schema version number
```

### Auth (`src/server/auth/`)

| Class | Role |
|-------|------|
| `JwtUtil` | JWT HS256 generation/verification, no dependency |
| `PasswordUtil` | PBKDF2-HmacSHA256, 310k iterations, 32-byte salt |
| `AuthApiServer` | register/login/me/trial/payment/download-token endpoints |
| `DownloadHandler` | Serves the file with Content-Disposition, consumes the token |

### AdminApiServer (`src/server/api/`)

Unified REST API on port 8081. All admin endpoints require `Authorization: Bearer <admin.secret>`.

**Public endpoints:**
- `GET /api/videos` — list of active streams with all metadata
- `GET /api/categories` — categories
- `GET /api/health` — JVM + DB + SSE clients status
- `GET /api/events` — SSE stream (video_added, stream_started, etc.)
- `GET /api/logs` — SSE log terminal

**Client endpoints (JWT required):**
- `POST /api/auth/register` — create an account (auto-trial offered)
- `POST /api/auth/login` — login → JWT
- `GET /api/auth/me` — profile + subscription status
- `POST /api/auth/trial` — start 14-day trial
- `POST /api/auth/payment/request` — submit cash payment request
- `GET /api/auth/payment/status` — status of my payments
- `POST /api/auth/download-token` — get a download token
- `GET /api/download?token=...` — download the file

**Admin endpoints (Bearer secret required):**
- `/api/admin/videos` — CRUD videos, toggle is_free
- `/api/admin/categories` — CRUD categories
- `/api/admin/stats` + `/api/admin/stats/hourly` — dashboard
- `/api/admin/users` — list, update role/status, grant/revoke subscription
- `/api/admin/subscriptions` — chronological view with filters
- `/api/admin/payments` — cash payment approval/rejection

### SSE Event Bus (`src/server/sse/`)

Publish/subscribe event bus. Replaces client-side polling (formerly `setInterval(5000)`).

Events emitted: `video_added`, `video_removed`, `stream_started`, `stream_stopped`, `payment_approved`, `log_entry`, `stats_update`.

Automatic keepalive every 20s to prevent proxy timeouts.

## Main Data Flows

### Adding a stream
```
StreamingServerGui
  → StreamingServer.start()
    → FfprobeExtractor.extract()   # auto metadata
    → VideoRepository.upsert()     # persists to H2
    → DiaryImpl.registerVideo()    # registers via RMI
    → SseEventBus.publishStreamStarted()  # notifies SSE clients
```

### Client registration
```
Web Client POST /api/auth/register
  → PasswordUtil.hash()            # PBKDF2
  → UserRepository.create()        # insert users
  → JwtUtil.generate()             # JWT 7d
  → startTrial() auto              # 14-day trial subscription
  → response {token, user}
```

### Secure download
```
Client POST /api/auth/download-token {videoId}
  → verify JWT + active subscription
  → generate random 32-byte token
  → insert download_tokens (expires in 2h)
  → response {downloadUrl}

Client GET /api/download?token=xxx
  → validate token (not expired, not used)
  → mark token used=TRUE
  → increment download_count
  → stream the file with Content-Disposition: attachment
```

### Payment approval
```
Admin POST /api/admin/payments/{id}/approve
  → PaymentRepository.approve()
    → SubscriptionRepository.activatePaidPlan()  # activates the subscription
    → UPDATE payments SET status='approved'      # marks the payment
  → SseEventBus.publish("payment_approved")      # notifies the admin panel
```

## Technical Choices

| Decision | Reason |
|----------|--------|
| Custom JWT (HmacSHA256) | Zero external dependency, native Java |
| PBKDF2-HmacSHA256 | Native Java, NIST recommended, 310k iterations |
| Embedded H2 | No installation, zero configuration, easy SQL migration |
| One HTTP port per video | Perfect isolation, simple seek, no multiplexing to manage |
| SSE vs WebSocket | SSE is sufficient (server → client push), simpler to implement |
| com.sun.net.httpserver | Zero dependency, sufficient for the project |
| SHA-256 hashed IP | GDPR compliance — tracking views without storing IPs |
