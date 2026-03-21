#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WEB_DIR="$ROOT_DIR/web"
API_TARGET="${1:-http://localhost:18081}"

if [[ "$API_TARGET" =~ ^[0-9]+$ ]]; then
  API_TARGET="http://localhost:${API_TARGET}"
fi

if ! command -v npm >/dev/null 2>&1; then
  echo "Erreur: npm introuvable. Installez Node.js 20+." >&2
  exit 1
fi

cd "$WEB_DIR"
export VITE_WEB_API_URL="$API_TARGET"

if [ ! -d node_modules ]; then
  echo "Installation des dépendances frontend..."
  npm install
fi

echo "=========================================="
echo "   DEMARRAGE FRONTEND WEB"
echo "=========================================="
echo "URL: http://localhost:55173"
echo "API backend: $VITE_WEB_API_URL"
echo "=========================================="

exec npm run dev -- --host 127.0.0.1
