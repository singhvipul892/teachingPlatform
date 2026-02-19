# Bootstrap SSL: start nginx with HTTP-only config, obtain Let's Encrypt cert, switch to SSL and restart nginx.
# Run from project root. Prerequisites: teacherplatform.duckdns.org must resolve to this host; ports 80 and 443 open.

$ErrorActionPreference = "Stop"

$ComposeFile = if ($env:COMPOSE_FILE) { $env:COMPOSE_FILE } else { "docker-compose.prod.yml" }
$Domain = if ($env:LETSENCRYPT_DOMAIN) { $env:LETSENCRYPT_DOMAIN } else { "teacherplatform.duckdns.org" }
$Email = if ($args.Count -gt 0) { $args[0] } elseif ($env:LETSENCRYPT_EMAIL) { $env:LETSENCRYPT_EMAIL } else { $null }

if (-not $Email) {
    Write-Host "Usage: .\bootstrap-ssl.ps1 YOUR_EMAIL"
    Write-Host "   or: `$env:LETSENCRYPT_EMAIL = 'your@email.com'; .\bootstrap-ssl.ps1"
    exit 1
}

Write-Host "Bootstrapping SSL for $Domain (email: $Email)"

# Phase 1: start nginx with HTTP-only config
$env:NGINX_CONF = "nginx.no-ssl.conf"
Write-Host "Starting stack with HTTP-only nginx..."
docker compose -f $ComposeFile up -d

Write-Host "Obtaining certificate..."
docker compose -f $ComposeFile run --rm certbot certonly `
    --webroot -w /var/www/certbot `
    -d $Domain `
    --email $Email `
    --agree-tos `
    --non-interactive

# Phase 2: switch to SSL config and restart nginx
Remove-Item Env:\NGINX_CONF -ErrorAction SilentlyContinue
Write-Host "Switching nginx to SSL config and restarting..."
docker compose -f $ComposeFile up -d --force-recreate nginx

Write-Host "Done. HTTPS should be available at https://$Domain"
