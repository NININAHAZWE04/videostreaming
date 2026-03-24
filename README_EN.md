<div align="center">

# 🎬 VideoStreaming Platform

**Distributed video streaming platform in Java — featuring authentication, subscriptions, secure downloads, and a comprehensive admin panel.**

![Java](https://img.shields.io/badge/Java-21-orange?style=flat-square)
![React](https://img.shields.io/badge/React-18-blue?style=flat-square)
![H2](https://img.shields.io/badge/H2-Database-green?style=flat-square)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square)
![License](https://img.shields.io/badge/License-MIT-lightgrey?style=flat-square)

</div>

---

## 🏗️ Architecture

Three essential components:

| Component | Role | Port |
|-----------|------|------|
| **Diary Server** | RMI Directory — registry of all active streams | 12999 |
| **Streaming Server** | HTTP Server per video — Range requests, stats, thumbnails | 5000+ |
| **Admin API** | REST + Auth + SSE — full management | 18081 |

```text
DiaryServer (RMI:12999)
       │
       ├── StreamingServer (HTTP:5000+)  ←→  Swing / VLC Client
       │
AdminApiServer (HTTP:18081)
       ├── /api/videos        ← React Web Client
       ├── /api/admin/*       ← React Admin Panel
       ├── /api/auth/*        ← JWT Authentication
       └── /api/download      ← Secure Download
```

## ✨ Features

### Java Backend
- **Diary RMI** — distributed registry, persisted in an H2 database
- **HTTP Streaming** — Range requests (seek), ffmpeg thumbnails, IP-based rate limiting
- **H2 Database** — auto-created schema, automatic migration, built-in web console
- **JWT HS256** — stateless authentication, no external dependencies
- **PBKDF2-HmacSHA256** — password hashing with random salt
- **SSE (Server-Sent Events)** — real-time without polling (video added, stream started…)
- **Secure Download** — single-use tokens, 2-hour expiration

### React Admin Panel (`/admin`)
- **Dashboard** — KPIs, 24h view charts, top videos, JVM health
- **Video Management** — CRUD, auto-metadata via ffprobe, free/premium access toggle
- **Category Management** — colors, icons, CRUD
- **Monitoring** — real-time active streams, live thumbnails
- **Users** — list, grant/revoke subscriptions, suspend accounts
- **Subscriptions** — chronological view, imminent expiration alerts
- **Cash Payments** — approval/rejection with automatic subscription activation
- **Manual Payment Entry** — back-office encoding + immediate status update
- **Pricing Management** — dynamic plans from the database (monthly/annual/trial/free)
- **Runtime Parameters** — IP and concurrent client limits configurable from admin
- **Live Logs** — SSE terminal with level-based syntax highlighting

### React Web Client (`/web`)
- Netflix-style responsive interface
- **Authentication** — signup/login, colored avatars
- **14-day Free Trial** — automatically activated upon registration
- **Subscription** — Monthly/Annual plans, cash payment with proof notes
- **Locked Content** — premium overlay, "Free" row always visible
- **Download** — button in the player, server-side signed tokens
- **History & Progress** — "Resume" section, progress bar
- **SSE** — replaces polling, instant updates
- **Filters** — colored categories, full-text search

---

## 🚀 Quick Start

### Option A — Local Development

**Prerequisites:** Java 21+, Node.js 20+, ffmpeg (for thumbnails)

```bash
# 1. Setup (checks dependencies, creates directories)
./setup.sh

# 2. Terminal 1 — Diary (RMI directory)
./start-diary.sh

# 3. Terminal 2 — Streaming Provider (GUI admin desktop)
./start-streaming-server.sh

# 4. Terminal 3 — Admin API (auth + REST)
./start-admin-api.sh

# 5. Terminal 4 — Legacy public API (optional)
./start-web-api.sh

# 6. Terminal 5 — React Web Client
./start-web-frontend.sh 18081
# → http://localhost:5173

# 7. Terminal 6 — React Admin Panel
./start-admin-frontend.sh 18081
# → http://localhost:5174
```

### Option B — Docker Compose (Recommended)

```bash
# 1. Copy and configure variables
cp .env.example .env
nano .env  # change ADMIN_SECRET and JWT_SECRET

# 2. Start the whole stack
docker compose up -d

# 3. Check if everything is running
docker compose ps
docker compose logs -f backend
```

Access:
| URL | Service |
|-----|---------|
| http://localhost:55173 | Web Client (docker) |
| http://localhost:55174 | Admin Panel (docker) |
| http://localhost:18081/api/health | Health API |
| http://localhost:18082 | H2 Console (dev) |

---

## 🗂️ Project Structure

```text
videostreaming/
├── src/
│   ├── common/          # AppConfig, AppLogger
│   ├── db/              # H2 — User, Video, Subscription, Payment repositories
│   ├── diary/           # RMI — Diary interface + DiaryImpl (H2)
│   ├── server/
│   │   ├── api/         # AdminApiServer, DiaryApiServer, JsonBuilder
│   │   ├── auth/        # JwtUtil, PasswordUtil, AuthApiServer, DownloadHandler
│   │   ├── gui/         # StreamingServerGui (Swing)
│   │   └── sse/         # SseEventBus
│   └── client/gui/      # ClientGui (Swing + VLC)
├── web/                 # React Client — Netflix-style
├── admin/               # React Admin Panel
├── docker/              # Dockerfiles + nginx.conf
├── docs/                # Architecture, Operations, Frontend, API
├── lib/                 # h2.jar
├── application.properties
├── docker-compose.yml
├── .env.example
└── build.sh / setup.sh / start-*.sh
```

---

## ⚙️ Configuration

All configuration is done in `application.properties`:

```properties
admin.secret=your-admin-secret
jwt.secret=your-jwt-secret-min-64-chars
plan.monthly.price=9.99
plan.annual.price=79.99
streaming.max.connections.per.ip=5
streaming.max.concurrent.clients=150
log.level=INFO
```

For Docker, use `.env` (copied from `.env.example`).

---

## 📚 Documentation

| Document | Content |
|----------|---------|
| [Architecture](docs/ARCHITECTURE_EN.md) | Schemas, data flows, technical choices |
| [Operations Guide](docs/OPERATIONS_EN.md) | Startup, scripts, troubleshooting |
| [Frontend](docs/FRONTEND_EN.md) | Web client + Admin panel |
| [API Reference](docs/API_EN.md) | All REST endpoints |
| [Contributing](CONTRIBUTING_EN.md) | Contribution guidelines |

---

## 🔒 Security

- Passwords: PBKDF2-HmacSHA256, 310,000 iterations, 32-byte random salt
- JWT: HS256, 7 days, server-side HMAC signature
- Downloads: Single-use tokens (1 use only, 2-hour expiration)
- Client IPs: SHA-256 hashed before storage (GDPR anonymization)
- Rate limiting: Max concurrent connections per IP configurable
- **⚠️ In production**: Change `admin.secret` and `jwt.secret` in `.env`

---

## 📄 License

MIT — see [LICENSE](LICENSE) for details.
