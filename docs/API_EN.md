[🇫🇷 FR](API.md) | **🇬🇧 EN**


# API Reference

Base URL: `http://localhost:18081`

---

## Public (No Authentication)

### `GET /api/health`
Application status.
```json
{
  "status": "ok",
  "timestamp": "2024-03-16T18:30:00",
  "totalVideos": 12,
  "activeStreams": 3,
  "activeClients": 24,
  "maxClients": 150,
  "maxPerIp": 5,
  "jvmUsedMb": 128,
  "jvmTotalMb": 512,
  "sseClients": 2
}
```

### `GET /api/videos[?q=title&category=1]`
List of active videos with all metadata.
```json
{
  "count": 3,
  "videos": [{
    "id": 1,
    "title": "My Movie",
    "host": "192.168.1.10",
    "port": 5000,
    "fileSize": 1234567890,
    "durationSec": 5820,
    "formattedDuration": "1:37:00",
    "formattedSize": "1.1 GB",
    "resolution": "1920x1080",
    "codec": "h264",
    "fps": 24.0,
    "qualityLabel": "1080p",
    "synopsis": "An exciting film...",
    "categoryId": 1,
    "categoryName": "Action",
    "categoryColor": "#ef4444",
    "free": false,
    "viewCount": 42,
    "streamUrl": "http://192.168.1.10:5000",
    "thumbnailUrl": "http://192.168.1.10:5000/thumbnail"
  }]
}
```

### `GET /api/categories`
```json
[{"id":1,"name":"Action","color":"#ef4444","icon":"zap","videoCount":3}]
```

### `GET /api/events` (SSE)
Real-time event stream.
```
event: video_added
data: {"id":5,"title":"New Movie"}

event: stream_started
data: {"title":"Movie","url":"http://host:5001"}

event: stream_stopped
data: {"title":"Movie"}
```

### `GET /api/videos/highlights`
Editorial sections for the client (newest, trending, coming soon).
```json
{
  "newest": [...],
  "trendingWeek": [...],
  "comingSoon": [...]
}
```

---

## Client Auth (JWT Required)

Header: `Authorization: Bearer <jwt_token>`

### `POST /api/auth/register`
```json
// Request
{"email":"user@example.com","username":"John","password":"password123"}

// Response 201
{"token":"eyJ...","user":{...},"canStartTrial":true,"message":"..."}
```

### `POST /api/auth/login`
```json
// Request
{"email":"user@example.com","password":"password123"}

// Response 200
{"token":"eyJ...","user":{...}}
```

### `GET /api/auth/me`
```json
{
  "id": 1,
  "email": "user@example.com",
  "username": "John",
  "role": "user",
  "hasSubscription": true,
  "subPlan": "trial",
  "daysRemaining": 12,
  "trialUsed": true,
  "canStartTrial": false,
  "avatarColor": "#38bdf8",
  "initials": "J"
}
```

### `POST /api/auth/trial`
Starts the 14-day trial. Returns 409 error if already used.
```json
// Response 200
{"token":"eyJ...","user":{...},"message":"14-day trial activated!"}
```

### `POST /api/auth/payment/request`
```json
// Request
{"plan":"monthly","proofNote":"Paid to John on 03/16"}

// Response 201
{"paymentId":3,"status":"pending","message":"Request sent..."}
```

> The amount and duration are now calculated server-side from `subscription_plans`.

### `GET /api/auth/plans`
```json
[
  {"id":"monthly","price":9.99,"durationDays":30,"currency":"USD"},
  {"id":"annual","price":79.99,"durationDays":365,"currency":"USD"}
]
```

### `GET /api/auth/payment/status`
```json
[{"id":3,"plan":"monthly","amount":9.99,"status":"pending","createdAt":"2024-03-16 18:30"}]
```

### `POST /api/auth/download-token`
```json
// Request
{"videoId":"5"}

// Response 200
{"token":"abc123...","downloadUrl":"http://localhost:18081/api/download?token=abc123","expiresIn":"2 hours"}
```

### `GET /api/download?token=<token>`
Downloads the video file. Single-use token, 2-hour expiration.
- Header `Content-Disposition: attachment; filename="My_Movie.mp4"`
- Binary file stream

---

## Admin (Bearer admin.secret Required)

Header: `Authorization: Bearer <admin.secret>`

### Videos

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/admin/videos` | All videos (active + inactive) |
| POST | `/api/admin/videos` | Create a video |
| PUT | `/api/admin/videos/{id}` | Update (synopsis, category, tags, isFree) |
| DELETE | `/api/admin/videos/{id}` | Delete |

Toggle free access:
```json
PUT /api/admin/videos/5
{"isFree":"true"}
```

### Categories

| Method | Endpoint | |
|--------|----------|---|
| GET | `/api/admin/categories` | List + counters |
| POST | `/api/admin/categories` | `{"name":"Thriller","color":"#8b5cf6","icon":"eye"}` |
| PUT | `/api/admin/categories/{id}` | Update |
| DELETE | `/api/admin/categories/{id}` | Delete (videos → category_id=null) |

### Stats

| Endpoint | Description |
|----------|-------------|
| `GET /api/admin/stats` | Dashboard (totals, top videos) |
| `GET /api/admin/stats/hourly` | Views per hour over 24h |

### Users

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/admin/users` | Full list with subscription status |
| PUT | `/api/admin/users/{id}` | `{"role":"admin"}` or `{"active":"false"}` |
| DELETE | `/api/admin/users/{id}` | Delete the account |
| POST | `/api/admin/users/{id}/subscription` | `{"plan":"monthly","days":"30"}` |
| POST | `/api/admin/users/{id}/revoke` | Revoke the subscription |

### Subscriptions

```
GET /api/admin/subscriptions[?status=active|expired|cancelled]
```

### Payments

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/admin/payments[?status=pending]` | List of payments |
| POST | `/api/admin/payments` | Manual payment entry |
| POST | `/api/admin/payments/{id}/approve` | Approve (activates subscription) |
| POST | `/api/admin/payments/{id}/reject` | Reject |

```json
POST /api/admin/payments/3/approve
{"adminNote":"Cash received","approvedBy":"admin"}
```

### Runtime Settings

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/admin/settings` | Runtime limits + currency |
| PUT | `/api/admin/settings` | Update server limits |

### Plans (Pricing Management)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/admin/plans` | List of plans/prices/durations |
| PUT | `/api/admin/plans/{plan}` | Update a plan |

---

## Error Codes

| Code | Meaning |
|------|---------|
| 400 | Missing or invalid parameter |
| 401 | JWT token missing or invalid |
| 403 | Subscription required or token expired |
| 404 | Resource not found |
| 405 | HTTP method not supported |
| 409 | Conflict (email already used, trial already used) |
| 429 | Connection limit reached (IP or global) |
| 500 | Internal server error |

All errors return `{"error":"explicit message"}`.
