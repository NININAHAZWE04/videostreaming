**🇫🇷 FR** | [🇬🇧 EN](API_EN.md)


# API Reference

Base URL : `http://localhost:18081`

---

## Public (sans authentification)

### `GET /api/health`
État de l'application.
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

### `GET /api/videos[?q=titre&category=1]`
Liste des vidéos actives avec toutes les métadonnées.
```json
{
  "count": 3,
  "videos": [{
    "id": 1,
    "title": "Mon Film",
    "host": "192.168.1.10",
    "port": 5000,
    "fileSize": 1234567890,
    "durationSec": 5820,
    "formattedDuration": "1:37:00",
    "formattedSize": "1.1 Go",
    "resolution": "1920x1080",
    "codec": "h264",
    "fps": 24.0,
    "qualityLabel": "1080p",
    "synopsis": "Un film passionnant...",
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
Stream d'événements temps réel.
```
event: video_added
data: {"id":5,"title":"Nouveau Film"}

event: stream_started
data: {"title":"Film","url":"http://host:5001"}

event: stream_stopped
data: {"title":"Film"}
```

### `GET /api/videos/highlights`
Sections éditoriales pour le client (nouveautés, tendances, coming soon).
```json
{
  "newest": [...],
  "trendingWeek": [...],
  "comingSoon": [...]
}
```

---

## Auth Client (JWT requis)

Header : `Authorization: Bearer <jwt_token>`

### `POST /api/auth/register`
```json
// Request
{"email":"user@example.com","username":"Jean","password":"motdepasse123"}

// Response 201
{"token":"eyJ...","user":{...},"canStartTrial":true,"message":"..."}
```

### `POST /api/auth/login`
```json
// Request
{"email":"user@example.com","password":"motdepasse123"}

// Response 200
{"token":"eyJ...","user":{...}}
```

### `GET /api/auth/me`
```json
{
  "id": 1,
  "email": "user@example.com",
  "username": "Jean",
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
Démarre le trial 14j. Erreur 409 si déjà utilisé.
```json
// Response 200
{"token":"eyJ...","user":{...},"message":"Essai de 14 jours activé !"}
```

### `POST /api/auth/payment/request`
```json
// Request
{"plan":"monthly","proofNote":"Payé à Jean le 16/03"}

// Response 201
{"paymentId":3,"status":"pending","message":"Demande envoyée..."}
```

> Le montant et la durée sont désormais calculés côté serveur depuis `subscription_plans`.

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
{"token":"abc123...","downloadUrl":"http://localhost:18081/api/download?token=abc123","expiresIn":"2 heures"}
```

### `GET /api/download?token=<token>`
Télécharge le fichier vidéo. Token à usage unique, 2h d'expiration.
- Header `Content-Disposition: attachment; filename="Mon_Film.mp4"`
- Stream binaire du fichier

---

## Admin (Bearer admin.secret requis)

Header : `Authorization: Bearer <admin.secret>`

### Vidéos

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| GET | `/api/admin/videos` | Toutes les vidéos (actives + inactives) |
| POST | `/api/admin/videos` | Créer une vidéo |
| PUT | `/api/admin/videos/{id}` | Modifier (synopsis, catégorie, tags, isFree) |
| DELETE | `/api/admin/videos/{id}` | Supprimer |

Toggle accès gratuit :
```json
PUT /api/admin/videos/5
{"isFree":"true"}
```

### Catégories

| Méthode | Endpoint | |
|---------|----------|---|
| GET | `/api/admin/categories` | Liste + compteurs |
| POST | `/api/admin/categories` | `{"name":"Thriller","color":"#8b5cf6","icon":"eye"}` |
| PUT | `/api/admin/categories/{id}` | Modifier |
| DELETE | `/api/admin/categories/{id}` | Supprimer (videos → category_id=null) |

### Stats

| Endpoint | Description |
|----------|-------------|
| `GET /api/admin/stats` | Dashboard (totaux, top vidéos) |
| `GET /api/admin/stats/hourly` | Vues par heure sur 24h |

### Utilisateurs

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| GET | `/api/admin/users` | Liste complète avec statut abonnement |
| PUT | `/api/admin/users/{id}` | `{"role":"admin"}` ou `{"active":"false"}` |
| DELETE | `/api/admin/users/{id}` | Supprimer le compte |
| POST | `/api/admin/users/{id}/subscription` | `{"plan":"monthly","days":"30"}` |
| POST | `/api/admin/users/{id}/revoke` | Révoquer l'abonnement |

### Abonnements

```
GET /api/admin/subscriptions[?status=active|expired|cancelled]
```

### Paiements

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| GET | `/api/admin/payments[?status=pending]` | Liste des paiements |
| POST | `/api/admin/payments` | Saisie manuelle de paiement |
| POST | `/api/admin/payments/{id}/approve` | Approuver (active abonnement) |
| POST | `/api/admin/payments/{id}/reject` | Rejeter |

```json
POST /api/admin/payments/3/approve
{"adminNote":"Cash reçu","approvedBy":"admin"}
```

### Paramètres runtime

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| GET | `/api/admin/settings` | Limites runtime + devise |
| PUT | `/api/admin/settings` | Met à jour les limites serveur |

### Plans (gestion des prix)

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| GET | `/api/admin/plans` | Liste des plans/prix/durées |
| PUT | `/api/admin/plans/{plan}` | Met à jour un plan |

---

## Codes d'erreur

| Code | Signification |
|------|---------------|
| 400 | Paramètre manquant ou invalide |
| 401 | Token JWT absent ou invalide |
| 403 | Abonnement requis ou token expiré |
| 404 | Ressource introuvable |
| 405 | Méthode HTTP non supportée |
| 409 | Conflit (email déjà utilisé, trial déjà utilisé) |
| 429 | Limite de connexions atteinte (IP ou globale) |
| 500 | Erreur serveur interne |

Toutes les erreurs retournent `{"error":"message explicite"}`.
