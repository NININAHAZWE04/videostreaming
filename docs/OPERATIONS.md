# Guide d'exploitation

## Build
```bash
./build.sh
```

## Nettoyage
```bash
./clean.sh
```

## Lancement standard
Terminal 1:
```bash
./start-diary.sh localhost 1099
```

Terminal 2:
```bash
./start-streaming-server.sh
```

Terminal 3:
```bash
./start-client.sh
```

## Vérifications rapides
- Le Diary affiche `DIARY SERVICE STARTED`.
- Le provider affiche l'écoute du port de streaming.
- Le client peut se connecter, actualiser, puis lire une vidéo.

## Améliorations GUI (février 2026)
- Validation en temps réel des champs (hôtes, ports, titre, sélection fichier).
- États visuels plus explicites (`Prêt`, compteurs de flux/vidéos, boutons contextuels).
- Opérations réseau non bloquantes côté GUI (`SwingWorker`) pour éviter les freezes.
- Journal d'activité avec action `Effacer les logs`.
- Tableau enrichi avec URL de flux.

## Erreurs fréquentes
1. `Erreur: port ... invalide`
- Vérifier que le port est numérique et dans `1..65535`.

2. `Impossible de contacter le Diary`
- Vérifier que `start-diary.sh` est lancé et que l'hôte/port correspondent.

3. `Le fichier vidéo n'existe pas` / `illisible`
- Vérifier le chemin et les permissions du fichier source.

4. VLC ne se lance pas
- Installer VLC et vérifier que `vlc` est présent dans le `PATH`.

## Bonnes pratiques
- Utiliser des titres de vidéos uniques.
- Réserver une plage de ports de streaming (ex: `5000+`).
- Sur machine distante, publier une IP atteignable dans le champ `Adresse IP de streaming`.
