#!/bin/bash
# Deploy the given branch (default: main) on the server.
# Usage: scripts/deploy.sh [branch]
set -euo pipefail

BRANCH="${1:-main}"
APP_DIR="/opt/teacherplatform"

cd "$APP_DIR"
git fetch origin "$BRANCH"
git reset --hard "origin/$BRANCH"

docker compose -f backend/docker-compose.prod.yml up -d --build api
docker compose -f backend/docker-compose.prod.yml exec -T nginx nginx -s reload

echo "Deployed $BRANCH @ $(git rev-parse --short HEAD)"
