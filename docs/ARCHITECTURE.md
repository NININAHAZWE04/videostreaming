

# Architecture technique

## Vue d'ensemble

```
┌─────────────────────────────────────────────────────────────┐
│                     CLIENT LAYER                            │
│  ┌───────────────┐  ┌───────────────┐  ┌────────────────┐   │
│  │ Client Web    │  │  Panel Admin  │  │  Client Swing  │   │
│  │ React :5173   │  │  React :5174  │  │  (VLC)         │   │
│  └───────┬───────┘  └───────┬───────┘  └───────┬────────┘   │
└──────────┼───────────────────┼──────────────────┼───────────┘
           │ HTTP              │ HTTP+Bearer       │ RMI
┌──────────▼───────────────────▼──────────────────▼───────────┐
│                     API LAYER                               │
│  ┌─────────────────────────────────────────────────────┐    │
│  │             AdminApiServer (:8081)                  │    │
│  │  /api/videos   /api/categories  /api/health         │    │
│  │  /api/events   /api/logs        /api/download       │    │
│  │  /api/auth/*   /api/admin/*                         │    │
│  └────────────────────────┬────────────────────────────┘    │
│  ┌────────────────────────▼──────────────────────────────┐  │
│  │             DiaryApiServer (:8080)                    │  │
│  │  GET /api/videos  GET /api/categories  GET /api/health│  │
│  └────────────────────────┬──────────────────────────────┘  │
└───────────────────────────┼─────────────────────────────────┘
                            │ RMI
┌───────────────────────────▼──────────────────────────────────┐
│                     CORE LAYER                               │
│  ┌───────────────────────────────┐                           │
│  │   DiaryServer (RMI :1099)     │                           │
│  │   DiaryImpl + H2 Database     │                           │
│  └───────────────────────────────┘                           │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐                    │
│  │ Stream   │  │ Stream   │  │ Stream   │  (1 par vidéo)     │
│  │ :5000    │  │ :5001    │  │ :5002    │                    │
│  └──────────┘  └──────────┘  └──────────┘                    │
└──────────────────────────────────────────────────────────────┘
```

## Composants

### DiaryServer (`src/diary/`)

Annuaire RMI central. Depuis v2, persisté en H2 au lieu d'un `ConcurrentHashMap`.

| Classe | Rôle |
|--------|------|
| `Diary.java` | Interface RMI (contrat réseau) |
| `DiaryImpl.java` | Implémentation avec persistance H2 |
| `DiaryServer.java` | Point d'entrée — démarre le registre RMI |
| `VideoInfo.java` | DTO sérialisable RMI — enrichi avec toutes les métadonnées |

### StreamingServer (`src/server/`)

Un processus par vidéo en cours de streaming.

- **HTTP Range** : statuts 200/206/416, seek fluide
- **ffprobe** : extraction automatique durée/résolution/codec/fps/bitrate au démarrage
- **Thumbnails** : extraction via ffmpeg, cache sur disque
- **Rate limiting** : max connexions simultanées par IP
- **Stats** : bytes servis trackés, enregistrés en base via `view_events`
- **SSE** : notifie `stream_started` / `stream_stopped`

### Base de données H2 (`src/db/`)

Schéma auto-créé au premier démarrage. Migration par `ALTER TABLE IF NOT EXISTS`.

```sql
users          -- comptes (email, password PBKDF2, rôle, avatar)
subscriptions  -- plans (trial 14j, monthly, annual, free)
payments       -- paiements cash (pending → approved/rejected)
download_tokens -- tokens usage unique, expiration 2h
categories     -- catégories vidéo avec couleur
videos         -- métadonnées complètes + is_free + download_count
view_events    -- logs de visionnage anonymisés (IP hashée SHA-256)
schema_version -- numéro de version du schéma
```

### Auth (`src/server/auth/`)

| Classe | Rôle |
|--------|------|
| `JwtUtil` | Génération/vérification JWT HS256, sans dépendance |
| `PasswordUtil` | PBKDF2-HmacSHA256, 310k itérations, sel 32 bytes |
| `AuthApiServer` | Endpoints register/login/me/trial/payment/download-token |
| `DownloadHandler` | Sert le fichier avec Content-Disposition, consomme le token |

### AdminApiServer (`src/server/api/`)

API REST unifiée sur le port 8081. Tous les endpoints admin requièrent `Authorization: Bearer <admin.secret>`.

**Endpoints publics :**
- `GET /api/videos` — liste des streams actifs avec toutes les métadonnées
- `GET /api/categories` — catégories
- `GET /api/health` — état JVM + DB + SSE clients
- `GET /api/events` — SSE stream (video_added, stream_started, etc.)
- `GET /api/logs` — SSE terminal de logs

**Endpoints client (JWT requis) :**
- `POST /api/auth/register` — créer un compte (trial auto-proposé)
- `POST /api/auth/login` — connexion → JWT
- `GET /api/auth/me` — profil + statut abonnement
- `POST /api/auth/trial` — démarrer l'essai 14j
- `POST /api/auth/payment/request` — soumettre demande paiement cash
- `GET /api/auth/payment/status` — statut de mes paiements
- `POST /api/auth/download-token` — obtenir un token de téléchargement
- `GET /api/download?token=...` — télécharger le fichier

**Endpoints admin (Bearer secret requis) :**
- `/api/admin/videos` — CRUD vidéos, toggle is_free
- `/api/admin/categories` — CRUD catégories
- `/api/admin/stats` + `/api/admin/stats/hourly` — dashboard
- `/api/admin/users` — liste, mise à jour rôle/statut, grant/revoke abonnement
- `/api/admin/subscriptions` — vue chronologique avec filtres
- `/api/admin/payments` — approbation/rejet paiements cash

### SSE Event Bus (`src/server/sse/`)

Bus d'événements publié/souscrit. Remplace le polling côté client (anciennement `setInterval(5000)`).

Événements émis : `video_added`, `video_removed`, `stream_started`, `stream_stopped`, `payment_approved`, `log_entry`, `stats_update`.

Keepalive automatique toutes les 20s pour éviter les timeouts proxy.

## Flux de données principaux

### Ajout d'un stream
```
StreamingServerGui
  → StreamingServer.start()
    → FfprobeExtractor.extract()   # métadonnées auto
    → VideoRepository.upsert()     # persiste en H2
    → DiaryImpl.registerVideo()    # enregistre via RMI
    → SseEventBus.publishStreamStarted()  # notifie clients SSE
```

### Inscription client
```
Client Web POST /api/auth/register
  → PasswordUtil.hash()            # PBKDF2
  → UserRepository.create()        # insert users
  → JwtUtil.generate()             # JWT 7j
  → startTrial() auto              # abonnement trial 14j
  → réponse {token, user}
```

### Téléchargement sécurisé
```
Client POST /api/auth/download-token {videoId}
  → vérifier JWT + abonnement actif
  → générer token aléatoire 32 bytes
  → insérer download_tokens (expire dans 2h)
  → réponse {downloadUrl}

Client GET /api/download?token=xxx
  → valider token (non expiré, non utilisé)
  → marquer token used=TRUE
  → incrémenter download_count
  → streamer le fichier avec Content-Disposition: attachment
```

### Approbation paiement
```
Admin POST /api/admin/payments/{id}/approve
  → PaymentRepository.approve()
    → SubscriptionRepository.activatePaidPlan()  # active l'abonnement
    → UPDATE payments SET status='approved'      # marque le paiement
  → SseEventBus.publish("payment_approved")      # notifie le panel admin
```

## Choix techniques

| Décision | Raison |
|----------|--------|
| JWT maison (HmacSHA256) | Zéro dépendance externe, Java natif |
| PBKDF2-HmacSHA256 | Java natif, NIST recommandé, 310k itérations |
| H2 embedded | Aucune installation, 0 configuration, migration SQL facile |
| Un port HTTP par vidéo | Isolation parfaite, seek simple, pas de multiplexage à gérer |
| SSE vs WebSocket | SSE suffit (push serveur → client), plus simple à implémenter |
| com.sun.net.httpserver | Zéro dépendance, suffisant pour le projet |
| IP hashée SHA-256 | Conformité RGPD — on trackle les vues sans stocker les IPs |
