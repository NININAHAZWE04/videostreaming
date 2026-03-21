#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

DIARY_HOST="${1:-localhost}"
DIARY_PORT="${2:-12999}"
API_PORT="${3:-18080}"

validate_port() {
  local p="$1"
  if ! [[ "$p" =~ ^[0-9]+$ ]] || [ "$p" -lt 1 ] || [ "$p" -gt 65535 ]; then
    echo "Erreur: port invalide ($p). Attendu: 1..65535" >&2
    exit 1
  fi
}

validate_port "$DIARY_PORT"
validate_port "$API_PORT"

./build.sh

echo "=========================================="
echo "   DEMARRAGE WEB API"
echo "=========================================="
echo "Diary: $DIARY_HOST:$DIARY_PORT"
echo "API:   http://localhost:$API_PORT"
echo "=========================================="

CP=""; for jar in lib/*.jar; do [ -f "$jar" ] && CP="${CP:+$CP:}$jar"; done; CP="${CP:+$CP:}bin"; exec java -cp "$CP" server.api.DiaryApiServer "$DIARY_HOST" "$DIARY_PORT" "$API_PORT"
