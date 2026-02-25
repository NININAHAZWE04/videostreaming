# Architecture

## Vue d'ensemble
Le système combine une découverte de flux via RMI et un streaming vidéo via HTTP.
Le frontend web s'appuie sur une API Java pour consulter l'état de la plateforme.

## Composants
1. `DiaryServer` (`src/diary/DiaryServer.java`)
- Démarre le registre RMI.
- Expose le service `Diary`.

2. `DiaryImpl` (`src/diary/DiaryImpl.java`)
- Stocke les entrées vidéo en mémoire (`ConcurrentHashMap`).
- Valide et normalise les données publiées.

3. `StreamingServer` (`src/server/StreamingServer.java`)
- Sert un fichier vidéo via HTTP.
- Gère les requêtes `Range` (seek) et les statuts `200/206/416`.

4. `StreamingServerGui` (`src/server/gui/StreamingServerGui.java`)
- Interface Provider pour publier/arrêter des flux.

5. `ClientGui` (`src/client/gui/ClientGui.java`)
- Interface Viewer pour découvrir et lire les flux.

6. `DiaryApiServer` (`src/server/api/DiaryApiServer.java`)
- API REST légère pour le web.
- Endpoints: `/api/health`, `/api/videos`.

7. `Frontend Web` (`web/`)
- Dashboard React + Tailwind.
- Affichage des flux, recherche, auto-refresh.

## Flux principal
1. Le Diary démarre et attend les enregistrements.
2. Le Provider démarre un `StreamingServer` pour un fichier vidéo.
3. Le `StreamingServer` s'enregistre dans le Diary.
4. Le Client Swing et le Frontend Web lisent la liste des vidéos.
5. Le consommateur ouvre l'URL HTTP du flux.

## Contrats réseau
- RMI:
  - `registerVideo(title, host, port)`
  - `unregisterVideo(title)`
  - `getVideoInfo(title)`
  - `listAllVideos()`
- HTTP Streaming:
  - `GET /`
  - `Range: bytes=...`
- HTTP API:
  - `GET /api/health`
  - `GET /api/videos`

## Choix techniques
- Un port HTTP par flux vidéo: isolation et simplicité.
- API web séparée: découplage entre frontend et RMI natif.
- Validation stricte des entrées pour limiter les erreurs d'exploitation.
