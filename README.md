<div align="center">
[FR] | [EN](README_EN.md)
# 🎬 VideoStreaming Platform

**Plateforme de streaming vidéo distribuée en Java — avec authentification, abonnements, téléchargement sécurisé et panel d'administration complet.**

![Java](https://img.shields.io/badge/Java-21-orange?style=flat-square)
![React](https://img.shields.io/badge/React-18-blue?style=flat-square)
![H2](https://img.shields.io/badge/H2-Database-green?style=flat-square)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square)
![License](https://img.shields.io/badge/License-MIT-lightgrey?style=flat-square)

</div>

---

## 🏗️ Architecture

Trois composants essentiels :

| Composant | Rôle | Port |
|-----------|------|------|
| **Diary Server** | Annuaire RMI — répertoire de tous les streams actifs | 12999 |
| **Streaming Server** | Serveur HTTP par vidéo — Range requests, stats, thumbnails | 5000+ |
| **Admin API** | REST + Auth + SSE — gestion complète | 18081 |

```
DiaryServer (RMI:12999)
       │
       ├── StreamingServer (HTTP:5000+)  ←→  Client Swing / VLC
       │
AdminApiServer (HTTP:18081)
       ├── /api/videos        ← Client Web React
       ├── /api/admin/*       ← Panel Admin React
       ├── /api/auth/*        ← Authentification JWT
       └── /api/download      ← Téléchargement sécurisé
```

## ✨ Fonctionnalités

### Backend Java
- **Diary RMI** — registre distribué, persisté en base H2
- **Streaming HTTP** — Range requests (seek), thumbnails ffmpeg, rate limiting par IP
- **H2 Database** — schéma auto-créé, migration automatique, console web intégrée
- **JWT HS256** — authentification stateless, sans dépendance externe
- **PBKDF2-HmacSHA256** — hachage des mots de passe, sel aléatoire
- **SSE (Server-Sent Events)** — temps réel sans polling (vidéo ajoutée, stream démarré…)
- **Téléchargement sécurisé** — tokens à usage unique, 2h d'expiration

### Panel Admin React (`/admin`)
- **Dashboard** — KPIs, graphique vues 24h, top vidéos, santé JVM
- **Gestion vidéos** — CRUD, métadonnées auto via ffprobe, toggle accès libre/premium
- **Gestion catégories** — couleur, icône, CRUD
- **Monitoring** — streams actifs en temps réel, thumbnails live
- **Utilisateurs** — liste, accorder/révoquer abonnement, suspendre compte
- **Abonnements** — vue chronologique, alertes expiration imminente
- **Paiements cash** — approbation/rejet avec activation automatique de l'abonnement
- **Saisie manuelle de paiements** — encodage back-office + statut immédiat
- **Gestion des prix** — plans dynamiques depuis la base (monthly/annual/trial/free)
- **Paramètres runtime** — limites IP et clients simultanés configurables depuis l'admin
- **Logs live** — terminal SSE avec colorisation par niveau

### Client Web React (`/web`)
- Interface Netflix-style responsive
- **Authentification** — inscription/connexion, avatar coloré
- **Essai gratuit 14j** — activé automatiquement à l'inscription
- **Abonnement** — plans Mensuel/Annuel, paiement cash avec note de preuve
- **Contenu verrouillé** — overlay premium, row "Gratuit" toujours visible
- **Téléchargement** — bouton dans le player, token signé côté serveur
- **Historique & progression** — section "Reprendre", barre de progression
- **SSE** — remplace le polling, mises à jour instantanées
- **Filtres** — catégories colorées, recherche full-text

---

## 🚀 Démarrage rapide

### Option A — Développement local

**Prérequis :** Java 21+, Node.js 20+, ffmpeg (pour thumbnails)

```bash
# 1. Setup (vérifie les dépendances, crée les dossiers)
./setup.sh

# 2. Terminal 1 — Diary (annuaire RMI)
./start-diary.sh

# 3. Terminal 2 — Streaming Provider (GUI admin desktop)
./start-streaming-server.sh

# 4. Terminal 3 — Admin API (auth + REST)
./start-admin-api.sh

# 5. Terminal 4 — API publique legacy (optionnel)
./start-web-api.sh

# 6. Terminal 5 — Client web React
./start-web-frontend.sh 18081
# → http://localhost:5173

# 7. Terminal 6 — Panel Admin React
./start-admin-frontend.sh 18081
# → http://localhost:5174
```

### Option B — Docker Compose (recommandé)

```bash
# 1. Copier et configurer les variables
cp .env.example .env
nano .env  # changer ADMIN_SECRET et JWT_SECRET

# 2. Lancer toute la stack
docker compose up -d

# 3. Vérifier que tout tourne
docker compose ps
docker compose logs -f backend
```

Accès :
| URL | Service |
|-----|---------|
| http://localhost:55173 | Client Web (docker) |
| http://localhost:55174 | Panel Admin (docker) |
| http://localhost:18081/api/health | API Health |
| http://localhost:18082 | H2 Console (dev) |

---

## 🗂️ Structure du projet

```
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
├── web/                 # Client React — Netflix-style
├── admin/               # Panel Admin React
├── docker/              # Dockerfiles + nginx.conf
├── docs/                # Architecture, Opérations, Frontend, API
├── lib/                 # h2.jar
├── application.properties
├── docker-compose.yml
├── .env.example
└── build.sh / setup.sh / start-*.sh
```

---

## ⚙️ Configuration

Toute la configuration se fait dans `application.properties` :

```properties
admin.secret=votre-secret-admin
jwt.secret=votre-secret-jwt-64-chars-min
plan.monthly.price=9.99
plan.annual.price=79.99
streaming.max.connections.per.ip=5
streaming.max.concurrent.clients=150
log.level=INFO
```

Pour Docker, utiliser `.env` (copié depuis `.env.example`).

---

## 📚 Documentation

| Document | Contenu |
|----------|---------|
| [Architecture](docs/ARCHITECTURE.md) | Schémas, flux de données, choix techniques |
| [Guide d'exploitation](docs/OPERATIONS.md) | Démarrage, scripts, dépannage |
| [Frontend](docs/FRONTEND.md) | Client web + Panel admin |
| [API Reference](docs/API.md) | Tous les endpoints REST |
| [Contribuer](CONTRIBUTING.md) | Guidelines de contribution |

---

## 🔒 Sécurité

- Mots de passe : PBKDF2-HmacSHA256, 310 000 itérations, sel aléatoire 32 bytes
- JWT : HS256, 7 jours, signature HMAC côté serveur
- Téléchargement : tokens à usage unique (1 seul usage, 2h d'expiration)
- IPs client : hashées SHA-256 avant stockage (anonymisation RGPD)
- Rate limiting : max connexions simultanées par IP configurable
- **⚠️ En production** : changer `admin.secret` et `jwt.secret` dans `.env`

---

## 📄 License

MIT — voir [LICENSE](LICENSE) pour les détails.
