# Contribuer au projet

Merci de votre intérêt ! Toute contribution est la bienvenue.

## Types de contributions acceptées

- 🐛 **Bug fixes** — corrections de comportements incorrects
- ✨ **Features** — nouvelles fonctionnalités discutées en issue au préalable
- 📚 **Documentation** — améliorations, corrections, traductions
- ♻️ **Refactoring** — amélioration du code sans changement de comportement
- 🧪 **Tests** — ajout de tests unitaires ou d'intégration

## Processus

1. **Ouvrir une issue** avant de commencer un travail important
2. **Forker** le repository
3. **Créer une branche** depuis `main` :
   ```bash
   git checkout -b feature/mon-amelioration
   git checkout -b fix/bug-description
   git checkout -b docs/mise-a-jour-readme
   ```
4. **Implémenter** en respectant les standards ci-dessous
5. **Vérifier le build** :
   ```bash
   # Backend Java
   ./build.sh

   # Frontends
   cd web && npm run build
   cd admin && npm run build
   ```
6. **Ouvrir une Pull Request** avec :
   - Description claire du changement
   - Screenshots si modification UI
   - Référence à l'issue liée (`Closes #42`)

## Standards de code

### Java
- Java 21 — utiliser les Text Blocks, Records, Switch expressions
- Pas de dépendances externes — tout doit compiler avec le JDK uniquement + h2.jar
- Chaque nouveau comportement → `AppLogger.info/warn/error` (pas `System.out.println`)
- Les secrets ne sont jamais codés en dur → passer par `AppConfig`
- Tests : vérifier manuellement via les endpoints API

### React / JavaScript
- Composants fonctionnels avec hooks
- Tailwind pour le style, CSS variables (`var(--text)`, `var(--brand)`, etc.)
- `authStore.js` pour tout ce qui touche à l'authentification
- Pas de bibliothèques supplémentaires sans discussion préalable

### Git
- Messages de commit en français ou anglais, explicites :
  ```
  feat: ajouter endpoint GET /api/auth/me
  fix: corriger l'expiration des tokens de téléchargement
  docs: mettre à jour ARCHITECTURE.md avec le flux SSE
  ```
- Un commit = un changement cohérent
- Pas de secrets ou de données personnelles dans les commits

## Ce qu'il ne faut pas committer

- `.env` (fichier de secrets)
- `data/` (base H2)
- `videos/` (fichiers vidéo)
- `node_modules/`
- `bin/*.class`
- Clés JWT, mots de passe, tokens

## Signalement de bugs

Inclure dans l'issue :
- OS et versions (Java/Node/Docker)
- Commande ou action qui reproduit le bug
- Comportement attendu vs observé
- Logs d'erreur (`docker compose logs backend`)

## Questions ?

Ouvrir une issue de type **Question** ou **Discussion**.
