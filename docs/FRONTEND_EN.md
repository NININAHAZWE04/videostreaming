# Frontend Documentation

Two distinct React applications: **Web Client** (for viewers) and **Admin Panel** (for the administrator).

---

## Web Client (`web/`)

Netflix-style interface for subscribers.

### Stack
- React 18 + Vite 8
- Tailwind CSS 3
- `authStore.js` — JWT localStorage management

### Pages & Features

**Navbar**
- Full-text search (title, synopsis, tags, category)
- API connection indicator (green/red dot)
- User menu (profile, subscription status, logout)
- Login / Register buttons when not logged in

**Hero Banner**
- First video as full-screen hero
- Quality badge (4K / 1080p / etc.)
- "FREE" badge if free content
- Different CTA depending on state (Watch / Subscribe / Login required)

**Content Rows**
- "Free content" — always visible without login
- "Resume" — videos with saved progress
- "New releases" — recent sort (base id desc)
- "Trending" — sorted by views
- "Premium picks" — premium suggestions
- By category — separate rows by genre (category color)
- Horizontal scroll with chevrons

**Video Card**
- Thumbnail with icon fallback
- Quality badge + duration
- Locked overlay (premium lock) with "Subscribe" CTA
- Progress bar if currently watching
- Hover: Watch / More Info buttons

**Modals**
- **AuthModal**: login/register in one modal, auto-trial on registration
- **SubscriptionModal**: visual plans (free trial / monthly / annual), cash payment funnel
- **PlayerModal**: native HTML5 player, download button with lock, saves progress
- **InfoModal**: all metadata + watch or subscribe button
- **SettingsPanel**: change the API URL

**Trial banner**
- Fixed bottom banner for accounts without a subscription with trial available

### Environment Variables
```bash
VITE_WEB_API_URL=http://localhost:8081   # Web client API URL
VITE_API_URL=http://localhost:8081       # legacy fallback
```

### Development Launch
```bash
cd web
npm install
npm run dev    # → http://localhost:5173
```

### Production Build
```bash
cd web
npm run build  # → web/dist/
```

---

## Admin Panel (`admin/`)

Full-featured administration dashboard.

### Stack
- React 18 + Vite 8
- Tailwind CSS 3
- Recharts (charts)

### Pages

#### Dashboard
- KPIs: total videos, active streams, views today, bandwidth
- Hourly views chart (rolling 24h) via Recharts
- Top 5 most-watched videos
- JVM health (memory, connected SSE clients)

#### Videos
- Table with thumbnail, auto-metadata (quality, duration, size, resolution)
- Filters: text search, category, status (active/inactive)
- Multi-select + bulk actions
- Form: title, category, synopsis, tags
- **"Free Access" toggle** — visible without subscription
- Full CRUD + SSE auto-refresh

#### Categories
- Cards with color and video count
- Color picker with presets + native selector
- Real-time preview in the form

#### Monitoring
- Active streams in real time (SSE)
- Live thumbnail + quality badge
- Info: duration, size, resolution, stream URL
- Auto-refresh every 10s + SSE

#### Users
- Table: colored avatar, email, plan, role, status
- KPIs: total / subscribers / trials
- Filters by status (all / subscribers / trial / no subscription / admin)
- Actions: grant subscription (plan + duration modal), revoke, suspend, delete
- "Grant" modal: plan choice + customizable duration

#### Subscriptions
- Chronological view across all plans
- Visual alert if expiration ≤ 5 days (yellow badge)
- Filters: active / trial / expired / cancelled
- Breakdown by plan (Trial / Monthly / Annual)

#### Payments
- KPIs: pending / approved / total revenue USD
- Full list with client note
- Quick actions: **Approve** (activates subscription immediately) / **Reject**
- Confirmation modal with admin note
- Orange sidebar badge if payments pending
- SSE auto-refresh on `payment_approved`

#### Live Logs
- Real-time SSE terminal
- History of last 500 lines
- Filters by level (ALL / DEBUG / INFO / WARN / ERROR)
- Search in messages
- Auto-scroll (toggle)

#### Settings
- Change the admin Bearer token (persists in localStorage)
- Technical stack info

### Environment Variables
```bash
VITE_ADMIN_API_URL=http://localhost:8081
VITE_ADMIN_SECRET=changeme-admin-secret
VITE_API_URL=http://localhost:8081       # legacy fallback
```

### Development Launch
```bash
cd admin
npm install
npm run dev    # → http://localhost:5174
```

### Production Build
```bash
cd admin
npm run build  # → admin/dist/
```

---

## Client Authentication

The web client uses `authStore.js` to manage the JWT:

```javascript
import * as auth from './authStore.js'

// Login
await auth.login(email, password)

// Register (automatically offers trial)
const res = await auth.register(email, username, password)
if (res.canStartTrial) await auth.startTrial()

// Check premium access
auth.userHasAccess(user)    // true if active subscription
auth.userCanDownload(user)  // true if plan != free

// Download a video
const { downloadUrl } = await auth.getDownloadToken(videoId)
window.location.href = downloadUrl
```

The JWT is stored in `localStorage` under the key `vs.token`. It is automatically sent in the `Authorization: Bearer <token>` header by all `authStore` requests.
