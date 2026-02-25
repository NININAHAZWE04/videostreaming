#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

chmod +x build.sh clean.sh start-diary.sh start-streaming-server.sh start-client.sh start-web-api.sh start-web-frontend.sh

cat <<'MSG'
==========================================
   CONFIGURATION DU PROJET TERMINEE
==========================================
Scripts backend:
  - ./build.sh
  - ./clean.sh
  - ./start-diary.sh [host] [port]
  - ./start-streaming-server.sh
  - ./start-client.sh
  - ./start-web-api.sh [diaryHost] [diaryPort] [apiPort]
Scripts frontend:
  - ./start-web-frontend.sh
==========================================
MSG
