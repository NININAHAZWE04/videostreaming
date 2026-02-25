#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WEB_DIR="$ROOT_DIR/web"

if ! command -v npm >/dev/null 2>&1; then
  echo "Erreur: npm introuvable. Installez Node.js 20+." >&2
  exit 1
fi

cd "$WEB_DIR"

if [ ! -d node_modules ]; then
  echo "Installation des dépendances frontend..."
  npm install
fi

echo "=========================================="
echo "   DEMARRAGE FRONTEND WEB"
echo "=========================================="
echo "URL: http://localhost:5173"
echo "=========================================="

exec npm run dev -- --host
