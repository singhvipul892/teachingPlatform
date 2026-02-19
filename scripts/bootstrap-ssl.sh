#!/usr/bin/env bash
# Bootstrap SSL: start nginx with HTTP-only config, obtain Let's Encrypt cert, switch to SSL and restart nginx.
# Run from project root. Prerequisites: teacherplatform.duckdns.org must resolve to this host; ports 80 and 443 open.

set -e

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
DOMAIN="${LETSENCRYPT_DOMAIN:-teacherplatform.duckdns.org}"
EMAIL="${1:-$LETSENCRYPT_EMAIL}"

if [ -z "$EMAIL" ]; then
  echo "Usage: $0 YOUR_EMAIL"
  echo "   or: LETSENCRYPT_EMAIL=your@email.com $0"
  exit 1
fi

echo "Bootstrapping SSL for $DOMAIN (email: $EMAIL)"

# Phase 1: start nginx with HTTP-only config so ACME challenge can be served
export NGINX_CONF=nginx.no-ssl.conf
echo "Starting stack with HTTP-only nginx..."
docker compose -f "$COMPOSE_FILE" up -d

echo "Obtaining certificate..."
docker compose -f "$COMPOSE_FILE" run --rm certbot certonly \
  --webroot -w /var/www/certbot \
  -d "$DOMAIN" \
  --email "$EMAIL" \
  --agree-tos \
  --non-interactive

# Phase 2: switch to SSL config and restart nginx
unset NGINX_CONF
echo "Switching nginx to SSL config and restarting..."
docker compose -f "$COMPOSE_FILE" up -d --force-recreate nginx

echo "Done. HTTPS should be available at https://$DOMAIN"
