#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

chmod +x build.sh clean.sh start-diary.sh start-streaming-server.sh start-client.sh

cat <<'MSG'
==========================================
   CONFIGURATION DU PROJET TERMINEE
==========================================
Scripts disponibles:
  - ./build.sh
  - ./clean.sh
  - ./start-diary.sh [host] [port]
  - ./start-streaming-server.sh
  - ./start-client.sh
==========================================
MSG
