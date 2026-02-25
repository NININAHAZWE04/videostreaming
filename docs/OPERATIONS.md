# Guide d'exploitation

## Build backend
```bash
./build.sh
```

## Nettoyage backend
```bash
./clean.sh
```

## Lancement complet (desktop + web)
Terminal 1 - Diary:
```bash
./start-diary.sh localhost 1099
```

Terminal 2 - Provider GUI:
```bash
./start-streaming-server.sh
```

Terminal 3 - Client GUI:
```bash
./start-client.sh
```

Terminal 4 - API Web:
```bash
./start-web-api.sh localhost 1099 8080
```

Terminal 5 - Frontend:
```bash
./start-web-frontend.sh
```

## Vérifications rapides
- Diary affiche `DIARY SERVICE STARTED`.
- Provider affiche l'écoute des ports de streaming.
- API web affiche `API web démarrée`.
- Frontend web accessible sur `http://localhost:5173`.

## Logs
- Les GUI Swing affichent désormais leurs logs dans la fenêtre et dans le terminal.
- L'API web journalise ses événements dans le terminal.

## Erreurs fréquentes
1. `port invalide`
- Vérifier que le port est numérique dans `1..65535`.

2. `Diary unavailable` côté API
- Vérifier que le Diary est démarré et joignable.

3. Frontend ne charge pas les vidéos
- Vérifier que l'API est accessible (`http://localhost:8080/api/videos`).
- Vérifier l'URL API configurée dans le dashboard.

4. VLC ne se lance pas
- Installer VLC et vérifier la commande `vlc`.

## Bonnes pratiques
- Utiliser des titres vidéo uniques.
- Réserver une plage de ports de streaming (ex: `5000+`).
- Ouvrir les composants dans l'ordre: Diary -> Provider -> API -> Frontend/Client.
