# Contributing

Merci de votre intérêt pour ce projet.

## Principe
**Toute contribution est la bienvenue**: bug report, correction, doc, idée produit, refactor ou nouvelle fonctionnalité.

## Comment contribuer
1. Forker le repository.
2. Créer une branche claire (`feature/...`, `fix/...`, `docs/...`).
3. Implémenter la modification avec un scope précis.
4. Vérifier le build local:
```bash
./build.sh
cd web && npm run build
```
5. Ouvrir une Pull Request avec:
- contexte,
- changements réalisés,
- capture(s) si UI,
- impacts potentiels.

## Standards attendus
- Code lisible, simple, sans dette inutile.
- Messages de commit explicites.
- Documentation mise à jour si comportement modifié.
- Pas de secret/token dans le code.

## Signalement de bugs
Inclure au minimum:
- OS et versions (Java/Node),
- commande exécutée,
- résultat attendu vs observé,
- logs d'erreur.
