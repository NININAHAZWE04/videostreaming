#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

./build.sh

echo "=========================================="
echo "   DEMARRAGE DU CLIENT"
echo "=========================================="

echo "Interface graphique en cours de chargement..."

CP=""; for jar in lib/*.jar; do [ -f "$jar" ] && CP="${CP:+$CP:}$jar"; done; CP="${CP:+$CP:}bin"; exec java -cp "$CP" client.gui.ClientGui
