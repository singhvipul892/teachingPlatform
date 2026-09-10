#!/bin/bash
# Deploy a CI-built image on the server.
#
# The server no longer compiles anything. GitHub Actions builds the image and
# pushes it to GHCR; this script pulls the tag matching the commit and restarts
# the api container. That removes the "no space left on device" failure class
# documented in DEPLOY.md, and cuts a deploy from ~5 minutes to ~20 seconds.
#
# The working tree is still reset, because the compose file, the nginx config
# and the static web/ bundle are read from disk, not baked into the image.
# Tree and image are pinned to the same commit, so they can never drift.
#
# Pending SQL migrations are applied before the new image starts. That ordering
# is not a nicety: the API runs with hibernate ddl-auto=validate and refuses to
# boot against a schema older than its entities. Doing it here means a deploy is
# one step again, and nobody has to remember to run psql first.
#
# Usage:
#   scripts/deploy.sh                 # deploy origin/main
#   scripts/deploy.sh my-feature      # deploy any branch
#   scripts/deploy.sh a9d4186         # deploy (or roll back to) any commit
set -euo pipefail

REF="${1:-main}"
APP_DIR="/opt/teacherplatform"
IMAGE_REPO="${IMAGE_REPO:-ghcr.io/singhvipul892/teachingplatform-api}"
COMPOSE=(docker compose -f backend/docker-compose.prod.yml)

cd "$APP_DIR"

# The repo is cloned by root via EC2 user-data, but deploys run as ec2-user.
# Without this git refuses to operate on the directory ("dubious ownership").
git config --global --get-all safe.directory | grep -qx "$APP_DIR" \
  || git config --global --add safe.directory "$APP_DIR"

# Resolve whatever was passed — branch name or commit SHA — to one commit.
git fetch --prune origin
SHA="$(git rev-parse --verify --quiet "origin/$REF" || git rev-parse --verify "$REF^{commit}")"
SHORT="$(git rev-parse --short=7 "$SHA")"

# Reset (not pull) so the deploy still works if the server's tree has drifted.
# Untracked .env survives.
git reset --hard "$SHA"

# Only needed while the GHCR package is private. Make it public and this whole
# block is a no-op with no credentials on the server:
#   GitHub -> Packages -> teachingplatform-api -> Package settings -> Change visibility
if [ -n "${GHCR_TOKEN:-}" ]; then
  echo "$GHCR_TOKEN" | docker login ghcr.io -u "${GHCR_USER:?GHCR_USER required when GHCR_TOKEN is set}" --password-stdin
fi

# Apply pending schema migrations BEFORE the new image starts: the API runs with
# ddl-auto=validate and will not boot against a schema older than its entities.
# A failure here exits non-zero and the API is left untouched, still running the
# previous image against the previous schema.
APP_DIR="$APP_DIR" bash scripts/run-migrations.sh

export API_IMAGE="${IMAGE_REPO}:${SHORT}"
echo "Deploying ${API_IMAGE}"

"${COMPOSE[@]}" pull api
"${COMPOSE[@]}" up -d api
"${COMPOSE[@]}" exec -T nginx nginx -s reload

# Pulled images accumulate; the build cache no longer does, since nothing is
# built here. Never prune volumes — teacher_db lives in one.
docker image prune -f

echo "Deployed $REF @ $SHORT"
df -h / | tail -1
