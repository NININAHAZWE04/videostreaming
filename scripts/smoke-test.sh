#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-http://localhost:18081}"
ADMIN_SECRET="${ADMIN_SECRET:-changeme-admin-secret}"

echo "[smoke] Base URL: $BASE_URL"

echo "[smoke] GET /api/health"
curl -fsS "$BASE_URL/api/health" > /tmp/vs-health.json

echo "[smoke] GET /api/videos"
curl -fsS "$BASE_URL/api/videos" > /tmp/vs-videos.json

echo "[smoke] GET /api/videos/highlights"
curl -fsS "$BASE_URL/api/videos/highlights" > /tmp/vs-highlights.json

echo "[smoke] GET /api/admin/stats"
curl -fsS -H "Authorization: Bearer $ADMIN_SECRET" "$BASE_URL/api/admin/stats" > /tmp/vs-admin-stats.json

echo "[smoke] GET /api/admin/settings"
curl -fsS -H "Authorization: Bearer $ADMIN_SECRET" "$BASE_URL/api/admin/settings" > /tmp/vs-admin-settings.json

echo "[smoke] OK - outputs in /tmp/vs-*.json"

