#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

echo "=========================================="
echo "   SETUP VideoStreaming Platform"
echo "=========================================="

# Check Java 21+
if ! command -v java >/dev/null 2>&1; then
  echo "❌ Java introuvable. Installez un JDK 21+" >&2; exit 1
fi
JAVA_VER=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
if [ "${JAVA_VER:-0}" -lt 21 ] 2>/dev/null; then
  echo "⚠️  Java $JAVA_VER détecté — Java 21+ recommandé"
else
  echo "✅ Java $JAVA_VER"
fi

# Check ffmpeg
if command -v ffmpeg >/dev/null 2>&1; then
  echo "✅ ffmpeg disponible — thumbnails et métadonnées activés"
else
  echo "⚠️  ffmpeg absent — thumbnails désactivés (apt install ffmpeg)"
fi

# Check ffprobe
if command -v ffprobe >/dev/null 2>&1; then
  echo "✅ ffprobe disponible — extraction métadonnées automatique"
else
  echo "⚠️  ffprobe absent — métadonnées (durée, résolution) non disponibles"
fi

# Check Node.js for frontend
if command -v node >/dev/null 2>&1; then
  echo "✅ Node.js $(node --version)"
else
  echo "⚠️  Node.js absent — frontend web non disponible"
fi

# Create required directories
mkdir -p lib data videos
echo "✅ Dossiers créés: lib/ data/ videos/"

# Copy H2 jar if available from system
if [ ! -s "lib/h2.jar" ] && [ -f "/usr/share/java/h2.jar" ]; then
  cp /usr/share/java/h2.jar lib/h2.jar
  echo "✅ H2 jar copié depuis /usr/share/java/h2.jar"
fi

# Generate application.properties if missing
if [ ! -f "application.properties" ]; then
  cat > application.properties << 'EOF'
diary.host=localhost
diary.port=1099
api.port=8080
admin.api.port=8081
admin.secret=changeme-admin-secret
db.url=jdbc:h2:./data/videostreaming;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1
db.user=sa
db.password=
h2.console.enabled=true
h2.console.port=8082
streaming.max.connections.per.ip=5
videos.directory=./videos
log.level=INFO
EOF
  echo "✅ application.properties créé"
else
  echo "✅ application.properties existant conservé"
fi

# Make all scripts executable
chmod +x build.sh clean.sh start-*.sh 2>/dev/null || true

echo ""
echo "=========================================="
echo "   CONFIGURATION TERMINÉE"
echo "=========================================="
echo ""
echo "Démarrage (5 terminaux) :"
echo "  1. ./start-diary.sh"
echo "  2. ./start-streaming-server.sh"
echo "  3. ./start-admin-api.sh"
echo "  4. ./start-web-api.sh"
echo "  5. ./start-web-frontend.sh    → http://localhost:5173 (client)"
echo ""
echo "Panel admin: cd admin && npm install && npm run dev"
echo "             → http://localhost:5174"
echo "H2 Console : http://localhost:8082 (si activé)"
echo "=========================================="

