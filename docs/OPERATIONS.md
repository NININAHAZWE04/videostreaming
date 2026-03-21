# Guide d'exploitation

## Démarrage local (développement)

### Prérequis
```bash
java -version    # Java 21+
node -v          # Node 20+
ffmpeg -version  # pour thumbnails (optionnel)
ffprobe -version # pour métadonnées auto (optionnel)
```

### Installation
```bash
git clone https://github.com/VOTRE_USER/videostreaming.git
cd videostreaming
./setup.sh   # vérifie les dépendances, crée data/ videos/ lib/
```

### Ordre de démarrage (6 terminaux)

```bash
# Terminal 1 — Diary
./start-diary.sh

# Terminal 2 — Streaming Server (GUI Swing admin desktop)
./start-streaming-server.sh

# Terminal 3 — Admin API (auth + REST + SSE)
./start-admin-api.sh

# Terminal 4 — API publique legacy (optionnel)
./start-web-api.sh

# Terminal 5 — Client Web React
./start-web-frontend.sh 8081   # → http://localhost:5173

# Terminal 6 — Panel Admin React
./start-admin-frontend.sh 8081   # → http://localhost:5174
```

### Vérification rapide
```bash
# Santé de l'API
curl http://localhost:18081/api/health

# Liste des vidéos actives
curl http://localhost:18081/api/videos

# Token admin (remplacer par votre secret)
curl -H "Authorization: Bearer changeme-admin-secret" \
     http://localhost:18081/api/admin/stats
```

---

## Démarrage Docker (production / test intégré)

### Prérequis
- Docker 24+ et Docker Compose V2
- Ports libres : 12999, 55173, 55174, 18080, 18081, 18082

### Lancement

```bash
# 1. Configurer les secrets
cp .env.example .env
nano .env
# → changer ADMIN_SECRET et JWT_SECRET obligatoirement

# 2. Construire et démarrer (premier lancement : ~3min)
docker compose up -d --build

# 3. Vérifier l'état
docker compose ps
docker compose logs -f backend

# 4. Attendre le healthcheck
docker compose ps
# STATUS doit passer à "healthy"
```

### Commandes utiles

```bash
# Arrêt sans perdre les données
docker compose down

# Arrêt + suppression des volumes (RESET total)
docker compose down -v

# Rebuild d'un seul service après modification
docker compose build backend
docker compose up -d backend

# Voir les logs en temps réel
docker compose logs -f backend
docker compose logs -f client-web

# Accéder au conteneur backend
docker exec -it vs-backend sh
```

### Persistance des données

Les données sont dans deux volumes Docker :
- `db-data` → base H2 (`data/videostreaming.mv.db`)
- `video-files` → fichiers vidéo (`videos/`)

```bash
# Localiser les volumes
docker volume inspect videostreaming_db-data
docker volume inspect videostreaming_video-files

# Sauvegarder la base H2
docker run --rm -v videostreaming_db-data:/data -v $(pwd):/backup alpine \
  tar czf /backup/backup-h2-$(date +%Y%m%d).tar.gz /data
```

### Ajouter des vidéos dans Docker

```bash
# Copier des vidéos dans le volume
docker cp /chemin/local/film.mp4 vs-backend:/app/videos/

# OU monter un dossier local dans docker-compose.yml :
# volumes:
#   - /home/USER/mes-videos:/app/videos
```

---

## Configuration

### application.properties

```properties
# Annuaire RMI
diary.host=localhost
diary.port=12999

# APIs
api.port=18080           # Public API (lecture seule)
admin.api.port=18081     # Admin API (auth + admin + SSE)

# Sécurité — CHANGER en production !
admin.secret=votre-secret-fort-ici
jwt.secret=votre-secret-jwt-64-chars-min-ici

# Plans & tarifs
plan.monthly.price=9.99
plan.annual.price=79.99
plan.trial.days=14
plan.currency=USD

# Base de données
db.url=jdbc:h2:./data/videostreaming;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1
db.user=sa
db.password=

# H2 Console web (désactiver en production)
h2.console.enabled=true
h2.console.port=18082

# Streaming
streaming.max.connections.per.ip=5
streaming.max.concurrent.clients=150

# Logging : DEBUG | INFO | WARN | ERROR
log.level=INFO
```

---

## Premier accès

### Créer un compte admin via API
```bash
# 1. Créer un compte normal
curl -X POST http://localhost:18081/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@example.com","username":"Admin","password":"motdepasse123"}'

# 2. L'élever en admin (avec le secret admin)
curl -X PUT http://localhost:18081/api/admin/users/1 \
  -H "Authorization: Bearer changeme-admin-secret" \
  -H "Content-Type: application/json" \
  -d '{"role":"admin"}'
```

### Console H2 (développement uniquement)
URL : http://localhost:18082
- JDBC URL : `jdbc:h2:./data/videostreaming`
- Utilisateur : `sa` / Mot de passe : *(vide)*

---

## Résolution de problèmes

### Le backend ne démarre pas
```bash
# Vérifier les logs
docker compose logs backend

# Problèmes fréquents :
# - Port 12999 déjà utilisé → changer PORT_DIARY_RMI dans .env
# - Port 18081 déjà utilisé → changer PORT_API_ADMIN dans .env
# - Fichier application.properties manquant → ./setup.sh
```

### "Diary unavailable" dans l'API
```bash
# Vérifier que le Diary est démarré
curl -sf http://localhost:18081/api/health | python3 -m json.tool
# → activeStreams, totalVideos, etc.
```

### Thumbnails non affichés
- Vérifier que `ffmpeg` est installé dans le conteneur : `docker exec vs-backend ffmpeg -version`
- L'endpoint thumbnail est aussi exposé via API : `http://localhost:18081/api/media/{id}/thumbnail`

### Paiement approuvé mais abonnement non activé
- Vérifier les logs : `docker compose logs -f backend | grep PaymentRepository`
- L'approbation appelle automatiquement `SubscriptionRepository.activatePaidPlan()`

### JWT invalide après changement de jwt.secret
- Tous les tokens existants sont invalidés
- Les utilisateurs doivent se reconnecter

### Réinitialiser un mot de passe (via API admin)
```bash
# Pas d'endpoint direct — supprimer le compte et le recréer
# OU modifier directement via H2 Console (dev uniquement)
```

---

## Monitoring en production

```bash
# Healthcheck permanent
watch -n 5 'curl -s http://localhost:18081/api/health | python3 -m json.tool'

# Logs structurés en temps réel
docker compose logs -f backend | grep -E "\[INFO\]|\[WARN\]|\[ERROR\]"

# Stats dashboard
curl -s -H "Authorization: Bearer $ADMIN_SECRET" \
  http://localhost:18081/api/admin/stats | python3 -m json.tool
```
