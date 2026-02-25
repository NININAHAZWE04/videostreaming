# Architecture

## Composants
1. `DiaryServer`
- Expose un objet RMI `Diary`.
- Stocke les entrées `title -> (host, port)` dans une `ConcurrentHashMap`.

2. `StreamingServer`
- Sert un fichier vidéo via HTTP.
- Gère les requêtes `GET` avec ou sans en-tête `Range`.
- S'enregistre/désenregistre automatiquement dans le Diary.

3. `ClientGui`
- Se connecte au Diary via RMI.
- Liste les flux disponibles.
- Ouvre VLC sur `http://<host>:<port>`.

## Flux logique
1. Le Diary démarre (`host`, `port`).
2. Un provider démarre un `StreamingServer` pour une vidéo.
3. Le `StreamingServer` publie `{title, host, port}` dans le Diary.
4. Le client interroge `listAllVideos()`.
5. Le client lance la lecture du flux choisi.

## Contrats réseau
- RMI Diary: `registerVideo`, `unregisterVideo`, `getVideoInfo`, `listAllVideos`
- HTTP streaming: `GET /` et `Range: bytes=...`

## Décisions clés
- Un serveur HTTP par vidéo: isolation simple par port.
- Arrêt propre: fermeture socket + arrêt thread pool + désenregistrement Diary.
- Validation centralisée: ports (1..65535), chaînes non vides, fichier vidéo lisible.
