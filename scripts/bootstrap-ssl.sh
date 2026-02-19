#!/usr/bin/env bash
# Bootstrap SSL: start nginx with HTTP-only config, obtain Let's Encrypt cert, switch to SSL and restart nginx.
# Run from project root, or from anywhere: script will cd to the directory that contains docker-compose.prod.yml.
# Prerequisites: teacherplatform.duckdns.org must resolve to this host; ports 80 and 443 open.

set -e

# Ensure we run from project root (directory that contains docker-compose.prod.yml)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
if [ ! -f "$ROOT_DIR/$COMPOSE_FILE" ]; then
  echo "Error: $COMPOSE_FILE not found in $ROOT_DIR. Run this script from the project root (or from the scripts/ directory)."
  exit 1
fi
cd "$ROOT_DIR"

# Use 'docker compose' (v2) if available, otherwise 'docker-compose' (v1)
if docker compose version &>/dev/null; then
  DOCKER_COMPOSE="docker compose"
elif command -v docker-compose &>/dev/null; then
  DOCKER_COMPOSE="docker-compose"
else
  echo "Error: neither 'docker compose' nor 'docker-compose' found. Install Docker Compose."
  exit 1
fi

DOMAIN="${LETSENCRYPT_DOMAIN:-teacherplatform.duckdns.org}"
EMAIL="${1:-$LETSENCRYPT_EMAIL}"

if [ -z "$EMAIL" ]; then
  echo "Usage: bash scripts/bootstrap-ssl.sh YOUR_EMAIL"
  echo "   or: LETSENCRYPT_EMAIL=your@email.com bash scripts/bootstrap-ssl.sh"
  exit 1
fi

echo "Bootstrapping SSL for $DOMAIN (email: $EMAIL)"
echo "Project root: $ROOT_DIR"

# Phase 1: start nginx with HTTP-only config so ACME challenge can be served
export NGINX_CONF=nginx.no-ssl.conf
echo "Starting stack with HTTP-only nginx..."
$DOCKER_COMPOSE -f "$COMPOSE_FILE" up -d

echo "Obtaining certificate..."
$DOCKER_COMPOSE -f "$COMPOSE_FILE" run --rm certbot certonly \
  --webroot -w /var/www/certbot \
  -d "$DOMAIN" \
  --email "$EMAIL" \
  --agree-tos \
  --non-interactive

# Phase 2: switch to SSL config and restart nginx
unset NGINX_CONF
echo "Switching nginx to SSL config and restarting..."
$DOCKER_COMPOSE -f "$COMPOSE_FILE" up -d --force-recreate nginx

echo "Done. HTTPS should be available at https://$DOMAIN"
