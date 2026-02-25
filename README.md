# VideoStreaming Platform

Plateforme de streaming vidéo en Java avec une expérience complète:
- backend RMI + HTTP pour publier des flux vidéo,
- applications Swing Provider/Client,
- frontend web moderne React + Tailwind pour la visibilité et le monitoring.

## Pourquoi ce projet
- Découvrir une architecture distribuée simple (RMI + HTTP).
- Publier des vidéos localement et les consommer en réseau.
- Offrir deux UX: Desktop (Swing) et Web (Dashboard moderne).

## Stack technique
- Java 21 (RMI, sockets HTTP, Swing)
- Bash scripts pour build/run
- API HTTP locale Java (`DiaryApiServer`)
- React 18 + Vite + Tailwind CSS (frontend web)

## Architecture rapide
1. `DiaryServer`: annuaire RMI des vidéos disponibles.
2. `StreamingServer`: un serveur HTTP par vidéo.
3. `ClientGui`: consomme le Diary et ouvre VLC.
4. `DiaryApiServer`: expose `/api/videos` pour le frontend web.
5. `web/`: dashboard moderne pour visualiser les flux.

## Prérequis
- Linux/macOS
- JDK 21+
- VLC (`vlc` dans le `PATH`)
- Node.js 20+ et npm (pour le frontend)

## Démarrage backend (desktop)
1. Préparer le projet:
```bash
./setup.sh
```

2. Terminal 1 - Diary:
```bash
./start-diary.sh localhost 1099
```

3. Terminal 2 - Provider GUI:
```bash
./start-streaming-server.sh
```

4. Terminal 3 - Client GUI:
```bash
./start-client.sh
```

## Démarrage web moderne
1. Terminal 4 - API Web Java:
```bash
./start-web-api.sh localhost 1099 8080
```

2. Terminal 5 - Frontend:
```bash
./start-web-frontend.sh
```

3. Ouvrir `http://localhost:5173`.

## Scripts backend
- `./build.sh`: compile toutes les classes Java
- `./clean.sh`: supprime les classes compilées
- `./start-diary.sh [host] [port]`
- `./start-streaming-server.sh`
- `./start-client.sh`
- `./start-web-api.sh [diaryHost] [diaryPort] [apiPort]`
- `./start-web-frontend.sh`

## Structure du repository
- `src/diary/`: contrat et implémentation RMI
- `src/server/`: serveur HTTP streaming
- `src/server/api/`: API HTTP pour frontend
- `src/server/gui/`: interface provider Swing
- `src/client/gui/`: interface client Swing
- `web/`: frontend React + Tailwind
- `docs/`: documentation technique et exploitation

## Documentation
- [Architecture](docs/ARCHITECTURE.md)
- [Guide d'exploitation](docs/OPERATIONS.md)
- [Frontend Web](docs/FRONTEND.md)
- [Contribuer](CONTRIBUTING.md)

## Contribution
Les issues, idées d'amélioration, correctifs et pull requests sont les bienvenus.
**Toute contribution est la bienvenue.**

## Roadmap courte
- API write (`POST`) pour piloter certaines actions depuis le web.
- Authentification simple pour un usage réseau élargi.
- Packaging Docker pour déploiement rapide.
