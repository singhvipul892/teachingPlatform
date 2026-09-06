#!/bin/bash
# Deploy the given branch (default: main) on the server.
# Usage: scripts/deploy.sh [branch]
set -euo pipefail

BRANCH="${1:-main}"
APP_DIR="/opt/teacherplatform"

cd "$APP_DIR"

# The repo is cloned by root via EC2 user-data, but deploys run as ec2-user.
# Without this git refuses to operate on the directory ("dubious ownership").
git config --global --get-all safe.directory | grep -qx "$APP_DIR" \
  || git config --global --add safe.directory "$APP_DIR"

git fetch origin "$BRANCH"
git reset --hard "origin/$BRANCH"

docker compose -f backend/docker-compose.prod.yml up -d --build api
docker compose -f backend/docker-compose.prod.yml exec -T nginx nginx -s reload

# Each rebuild leaves the previous api image dangling and grows the Gradle build
# cache, which filled the disk once. Never prune volumes here — teacher_db lives
# in one.
docker image prune -f
docker builder prune -f --filter until=168h

echo "Deployed $BRANCH @ $(git rev-parse --short HEAD)"
df -h / | tail -1
