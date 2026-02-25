#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

./build.sh

echo "=========================================="
echo "   DEMARRAGE DU STREAMING SERVER"
echo "=========================================="

echo "Interface graphique en cours de chargement..."

exec java -cp bin server.gui.StreamingServerGui
