#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

ADMIN_PORT="${1:-18081}"

if ! [[ "$ADMIN_PORT" =~ ^[0-9]+$ ]] || [ "$ADMIN_PORT" -lt 1 ] || [ "$ADMIN_PORT" -gt 65535 ]; then
  echo "Erreur: port invalide ($ADMIN_PORT)" >&2
  exit 1
fi

# Build classpath
CP=""
for jar in lib/*.jar; do
  [ -f "$jar" ] && CP="${CP:+$CP:}$jar"
done
CP="${CP:+$CP:}bin"

./build.sh

echo "=========================================="
echo "   DEMARRAGE ADMIN API"
echo "=========================================="
echo "Admin API:   http://localhost:$ADMIN_PORT"
echo "Endpoints:   /api/admin/* (Bearer auth)"
echo "Public:      /api/videos, /api/events, /api/logs"
echo "=========================================="

exec java -cp "$CP" server.api.AdminApiServer "$ADMIN_PORT"
