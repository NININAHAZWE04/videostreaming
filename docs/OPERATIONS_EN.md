# Operations Guide

## Local Startup (Development)

### Prerequisites
```bash
java -version    # Java 21+
node -v          # Node 20+
ffmpeg -version  # for thumbnails (optional)
ffprobe -version # for auto metadata (optional)
```

### Installation
```bash
git clone https://github.com/YOUR_USER/videostreaming.git
cd videostreaming
./setup.sh   # checks dependencies, creates data/ videos/ lib/
```

### Startup Order (6 terminals)

```bash
# Terminal 1 — Diary
./start-diary.sh

# Terminal 2 — Streaming Server (Swing GUI admin desktop)
./start-streaming-server.sh

# Terminal 3 — Admin API (auth + REST + SSE)
./start-admin-api.sh

# Terminal 4 — Legacy public API (optional)
./start-web-api.sh

# Terminal 5 — React Web Client
./start-web-frontend.sh 8081   # → http://localhost:5173

# Terminal 6 — React Admin Panel
./start-admin-frontend.sh 8081   # → http://localhost:5174
```

### Quick Check
```bash
# API health
curl http://localhost:18081/api/health

# List active videos
curl http://localhost:18081/api/videos

# Admin token (replace with your secret)
curl -H "Authorization: Bearer changeme-admin-secret" \
     http://localhost:18081/api/admin/stats
```

---

## Docker Startup (Production / Integrated Test)

### Prerequisites
- Docker 24+ and Docker Compose V2
- Free ports: 12999, 55173, 55174, 18080, 18081, 18082

### Launch

```bash
# 1. Configure secrets
cp .env.example .env
nano .env
# → change ADMIN_SECRET and JWT_SECRET (mandatory)

# 2. Build and start (first launch: ~3min)
docker compose up -d --build

# 3. Check status
docker compose ps
docker compose logs -f backend

# 4. Wait for healthcheck
docker compose ps
# STATUS should switch to "healthy"
```

### Useful Commands

```bash
# Stop without losing data
docker compose down

# Stop + delete volumes (FULL RESET)
docker compose down -v

# Rebuild a single service after modification
docker compose build backend
docker compose up -d backend

# View real-time logs
docker compose logs -f backend
docker compose logs -f client-web

# Access the backend container
docker exec -it vs-backend sh
```

### Data Persistence

Data is stored in two Docker volumes:
- `db-data` → H2 database (`data/videostreaming.mv.db`)
- `video-files` → video files (`videos/`)

```bash
# Locate volumes
docker volume inspect videostreaming_db-data
docker volume inspect videostreaming_video-files

# Backup the H2 database
docker run --rm -v videostreaming_db-data:/data -v $(pwd):/backup alpine \
  tar czf /backup/backup-h2-$(date +%Y%m%d).tar.gz /data
```

### Adding Videos in Docker

```bash
# Copy videos into the volume
docker cp /local/path/movie.mp4 vs-backend:/app/videos/

# OR mount a local folder in docker-compose.yml:
# volumes:
#   - /home/USER/my-videos:/app/videos
```

---

## Configuration

### application.properties

```properties
# RMI Directory
diary.host=localhost
diary.port=12999

# APIs
api.port=18080           # Public API (read-only)
admin.api.port=18081     # Admin API (auth + admin + SSE)

# Security — CHANGE in production!
admin.secret=your-strong-secret-here
jwt.secret=your-jwt-secret-64-chars-min-here

# Plans & pricing
plan.monthly.price=9.99
plan.annual.price=79.99
plan.trial.days=14
plan.currency=USD

# Database
db.url=jdbc:h2:./data/videostreaming;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1
db.user=sa
db.password=

# H2 Web Console (disable in production)
h2.console.enabled=true
h2.console.port=18082

# Streaming
streaming.max.connections.per.ip=5
streaming.max.concurrent.clients=150

# Logging: DEBUG | INFO | WARN | ERROR
log.level=INFO
```

---

## First Access

### Create an Admin Account via API
```bash
# 1. Create a regular account
curl -X POST http://localhost:18081/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@example.com","username":"Admin","password":"password123"}'

# 2. Elevate to admin (using the admin secret)
curl -X PUT http://localhost:18081/api/admin/users/1 \
  -H "Authorization: Bearer changeme-admin-secret" \
  -H "Content-Type: application/json" \
  -d '{"role":"admin"}'
```

### H2 Console (Development Only)
URL: http://localhost:18082
- JDBC URL: `jdbc:h2:./data/videostreaming`
- Username: `sa` / Password: *(empty)*

---

## Troubleshooting

### Backend Won't Start
```bash
# Check the logs
docker compose logs backend

# Common issues:
# - Port 12999 already in use → change PORT_DIARY_RMI in .env
# - Port 18081 already in use → change PORT_API_ADMIN in .env
# - Missing application.properties file → ./setup.sh
```

### "Diary unavailable" in the API
```bash
# Check that Diary is started
curl -sf http://localhost:18081/api/health | python3 -m json.tool
# → activeStreams, totalVideos, etc.
```

### Thumbnails Not Displayed
- Check that `ffmpeg` is installed in the container: `docker exec vs-backend ffmpeg -version`
- The thumbnail endpoint is also exposed via API: `http://localhost:18081/api/media/{id}/thumbnail`

### Payment Approved but Subscription Not Activated
- Check the logs: `docker compose logs -f backend | grep PaymentRepository`
- Approval automatically calls `SubscriptionRepository.activatePaidPlan()`

### Invalid JWT After Changing jwt.secret
- All existing tokens are invalidated
- Users must log in again

### Reset a Password (via Admin API)
```bash
# No direct endpoint — delete the account and recreate it
# OR modify directly via H2 Console (dev only)
```

---

## Production Monitoring

```bash
# Permanent healthcheck
watch -n 5 'curl -s http://localhost:18081/api/health | python3 -m json.tool'

# Structured real-time logs
docker compose logs -f backend | grep -E "\[INFO\]|\[WARN\]|\[ERROR\]"

# Dashboard stats
curl -s -H "Authorization: Bearer $ADMIN_SECRET" \
  http://localhost:18081/api/admin/stats | python3 -m json.tool
```
