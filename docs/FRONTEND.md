# Frontend Web (React + Tailwind)

## Objectif
Fournir une interface moderne et responsive pour visualiser les flux publiés par le Diary.

## Fonctionnalités
- Dashboard de flux vidéo en temps réel.
- Filtrage par titre/hôte/port.
- Configuration dynamique de l'URL API.
- Auto-refresh (5s) activable/désactivable.
- Ouverture directe d'un flux (`http://host:port`).
- Affichage de miniatures réelles (`thumbnailUrl`) extraites depuis le film.

## Prérequis
- Node.js 20+
- API Web Java démarrée (`./start-web-api.sh`)

## Lancer en développement
```bash
./start-web-frontend.sh
```

## Build production
```bash
cd web
npm run build
npm run preview
```

## Design system
- Typographies: `Space Grotesk`, `Manrope`, `IBM Plex Mono`.
- Palette: `ink`, `ocean`, `sand`, `ember`.
- UI responsive desktop/mobile avec animations légères.

## API attendue
- `GET /api/health`
- `GET /api/videos` -> `{ count, videos: [{ title, host, port, url, thumbnailUrl }] }`
