#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

./build.sh

echo "=========================================="
echo "   DEMARRAGE DU CLIENT"
echo "=========================================="

echo "Interface graphique en cours de chargement..."

exec java -cp bin client.gui.ClientGui
