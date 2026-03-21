#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

if ! command -v javac >/dev/null 2>&1; then
  echo "Erreur: javac introuvable. Installez un JDK 21+." >&2
  exit 1
fi

# Build classpath: include all jars in lib/
CP=""
for jar in lib/*.jar; do
  [ -f "$jar" ] && CP="${CP:+$CP:}$jar"
done
[ -z "$CP" ] && CP="."

mkdir -p bin
find bin -type f -name '*.class' -delete

mapfile -t JAVA_SOURCES < <(find src -type f -name '*.java' | sort)
if [ "${#JAVA_SOURCES[@]}" -eq 0 ]; then
  echo "Erreur: aucun fichier Java trouvé dans src/." >&2
  exit 1
fi

javac -encoding UTF-8 -cp "$CP" -d bin "${JAVA_SOURCES[@]}"

echo "Build terminé: ${#JAVA_SOURCES[@]} source(s) compilée(s) avec CP=$CP"
