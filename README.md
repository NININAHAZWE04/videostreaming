# Video Streaming (Java RMI + HTTP)

Projet de streaming vidéo local basé sur:
- un **service d'annuaire RMI** (`Diary`) qui publie les vidéos disponibles,
- un ou plusieurs **serveurs HTTP de streaming** (un par vidéo),
- un **client GUI** qui découvre les flux et lance la lecture via VLC.

## Prérequis
- Linux/macOS (scripts Bash)
- JDK 21+
- VLC installé et disponible via la commande `vlc`

## Démarrage rapide
1. Rendre les scripts exécutables:
```bash
./setup.sh
```

2. Lancer le Diary:
```bash
./start-diary.sh localhost 1099
```

3. Lancer le serveur de streaming (GUI):
```bash
./start-streaming-server.sh
```

4. Lancer le client (GUI):
```bash
./start-client.sh
```

## Scripts
- `./build.sh`: compile toutes les sources Java dans `bin/`
- `./clean.sh`: supprime les `.class`
- `./start-diary.sh [host] [port]`: démarre le registre et le service RMI
- `./start-streaming-server.sh`: ouvre l'interface provider
- `./start-client.sh`: ouvre l'interface client

## Structure du projet
- `src/diary`: API et implémentation RMI
- `src/server`: serveur HTTP de streaming
- `src/server/gui`: GUI provider
- `src/client/gui`: GUI client
- `docs/`: documentation d'architecture et d'exploitation

## Documentation
- [Architecture](docs/ARCHITECTURE.md)
- [Guide d'exploitation](docs/OPERATIONS.md)

## Notes techniques
- Le serveur HTTP supporte les requêtes `Range` (`206 Partial Content`) pour le seeking.
- Les validations d'entrée (ports, hôtes, titres, accessibilité fichier) sont renforcées.
- Le service Diary retourne la liste des vidéos de manière triée et stable.
