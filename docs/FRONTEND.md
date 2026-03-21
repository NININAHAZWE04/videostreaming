# Documentation Frontend

Deux applications React distinctes : **Client Web** (pour les spectateurs) et **Panel Admin** (pour l'administrateur).

---

## Client Web (`web/`)

Interface Netflix-style pour les abonnés.

### Stack
- React 18 + Vite 8
- Tailwind CSS 3
- `authStore.js` — gestion JWT localStorage

### Pages & Fonctionnalités

**Navbar**
- Recherche full-text (titre, synopsis, tags, catégorie)
- Indicateur connexion API (point vert/rouge)
- Menu utilisateur (profil, statut abonnement, déconnexion)
- Boutons Connexion / S'inscrire si non connecté

**Hero Banner**
- Première vidéo en hero plein écran
- Badge qualité (4K / 1080p / etc.)
- Badge "GRATUIT" si contenu libre
- CTA différent selon l'état (Lire / S'abonner / Connexion requise)

**Lignes de contenu**
- "Contenu gratuit" — toujours visible sans connexion
- "Reprendre" — vidéos avec progression sauvegardée
- "Nouveautes" — tri recentes (base id desc)
- "Tendances" — tri par vues
- "Premium du moment" — suggestions premium
- Par catégorie — lignes séparées par genre (couleur de catégorie)
- Scroll horizontal avec chevrons

**Carte vidéo**
- Thumbnail avec fallback icône
- Badge qualité + durée
- Overlay verrouillé (premium lock) avec CTA "S'abonner"
- Barre de progression si lecture en cours
- Hover : boutons Lire / Plus d'infos

**Modales**
- **AuthModal** : login/register en une modale, auto-trial à l'inscription
- **SubscriptionModal** : plans visual (trial gratuit / mensuel / annuel), tunnel paiement cash
- **PlayerModal** : player natif HTML5, bouton téléchargement avec lock, sauvegarde progression
- **InfoModal** : toutes les métadonnées + bouton lire ou s'abonner
- **SettingsPanel** : changer l'URL de l'API

**Trial banner**
- Bandeau fixe en bas pour les comptes sans abonnement avec trial disponible

### Variables d'environnement
```bash
VITE_WEB_API_URL=http://localhost:8081   # URL API client web
VITE_API_URL=http://localhost:8081       # fallback legacy
```

### Lancement développement
```bash
cd web
npm install
npm run dev    # → http://localhost:5173
```

### Build production
```bash
cd web
npm run build  # → web/dist/
```

---

## Panel Admin (`admin/`)

Dashboard d'administration complet.

### Stack
- React 18 + Vite 8
- Tailwind CSS 3
- Recharts (graphiques)

### Pages

#### Dashboard
- KPIs : vidéos totales, streams actifs, vues aujourd'hui, bande passante
- Graphique vues par heure (24h glissantes) via Recharts
- Top 5 vidéos les plus regardées
- Santé JVM (mémoire, SSE clients connectés)

#### Vidéos
- Tableau avec thumbnail, métadonnées auto (qualité, durée, taille, résolution)
- Filtres : recherche texte, catégorie, statut (actif/inactif)
- Sélection multiple + actions groupées
- Formulaire : titre, catégorie, synopsis, tags
- **Toggle "Accès gratuit"** — visible sans abonnement
- CRUD complet + SSE auto-refresh

#### Catégories
- Cards avec couleur et nombre de vidéos
- Color picker avec presets + sélecteur natif
- Aperçu en temps réel dans le formulaire

#### Monitoring
- Streams actifs en temps réel (SSE)
- Thumbnail live + qualité badge
- Infos : durée, taille, résolution, URL du stream
- Auto-refresh toutes les 10s + SSE

#### Utilisateurs
- Tableau : avatar coloré, email, plan, rôle, statut
- KPIs : total / abonnés / essais
- Filtres par statut (tous / abonnés / essai / sans abonnement / admin)
- Actions : accorder abonnement (modal plan + durée), révoquer, suspendre, supprimer
- Modal "Accorder" : choix du plan + durée personnalisable

#### Abonnements
- Vue chronologique tous plans confondus
- Alerte visuelle si expiration ≤ 5 jours (badge jaune)
- Filtres : actifs / essais / expirés / annulés
- Répartition par plan (Essai / Mensuel / Annuel)

#### Paiements
- KPIs : en attente / approuvés / revenu total USD
- Liste complète avec note du client
- Actions rapides : **Approuver** (active l'abonnement immédiatement) / **Rejeter**
- Modal de confirmation avec note admin
- Badge orange en sidebar si paiements en attente
- SSE auto-refresh sur `payment_approved`

#### Logs live
- Terminal SSE en temps réel
- Historique des 500 dernières lignes
- Filtres par niveau (ALL / DEBUG / INFO / WARN / ERROR)
- Recherche dans les messages
- Auto-scroll (toggle)

#### Paramètres
- Changer le token Bearer admin (persiste en localStorage)
- Infos stack technique

### Variables d'environnement
```bash
VITE_ADMIN_API_URL=http://localhost:8081
VITE_ADMIN_SECRET=changeme-admin-secret
VITE_API_URL=http://localhost:8081       # fallback legacy
```

### Lancement développement
```bash
cd admin
npm install
npm run dev    # → http://localhost:5174
```

### Build production
```bash
cd admin
npm run build  # → admin/dist/
```

---

## Authentification client

Le client web utilise `authStore.js` pour gérer le JWT :

```javascript
import * as auth from './authStore.js'

// Connexion
await auth.login(email, password)

// Inscription (propose trial automatiquement)
const res = await auth.register(email, username, password)
if (res.canStartTrial) await auth.startTrial()

// Vérifier l'accès premium
auth.userHasAccess(user)    // true si abonnement actif
auth.userCanDownload(user)  // true si plan != free

// Télécharger une vidéo
const { downloadUrl } = await auth.getDownloadToken(videoId)
window.location.href = downloadUrl
```

Le JWT est stocké dans `localStorage` sous la clé `vs.token`. Il est envoyé automatiquement dans le header `Authorization: Bearer <token>` par toutes les requêtes `authStore`.
