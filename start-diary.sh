#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

HOST="${1:-localhost}"
PORT="${2:-12999}"

if ! [[ "$PORT" =~ ^[0-9]+$ ]] || [ "$PORT" -lt 1 ] || [ "$PORT" -gt 65535 ]; then
  echo "Erreur: port Diary invalide ($PORT). Attendu: 1..65535" >&2
  exit 1
fi

./build.sh

echo "=========================================="
echo "   DEMARRAGE DU SERVEUR DIARY"
echo "=========================================="
echo "Host: $HOST"
echo "Port: $PORT"
echo "=========================================="

CP=""; for jar in lib/*.jar; do [ -f "$jar" ] && CP="${CP:+$CP:}$jar"; done; CP="${CP:+$CP:}bin"; exec java -cp "$CP" diary.DiaryServer "$HOST" "$PORT"
