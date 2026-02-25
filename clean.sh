#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

if [ -d bin ]; then
  find bin -type f -name '*.class' -delete
fi

echo "Nettoyage terminé (classes supprimées)."
