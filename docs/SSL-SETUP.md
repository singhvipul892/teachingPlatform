# SSL setup with Let's Encrypt

This guide covers obtaining and renewing Let's Encrypt certificates so nginx can serve HTTPS.

## Prerequisites

- **teacherplatform.duckdns.org** must resolve to the machine where Docker runs (public IP or port-forwarded).
- Ports **80** and **443** must be reachable from the internet.

## First-time bootstrap (obtain certificates)

Nginx cannot start with the full SSL config until certificate files exist. Use a two-phase bootstrap:

### Option A: Bootstrap script (recommended)

**Linux / WSL:**

```bash
# From project root
./scripts/bootstrap-ssl.sh your@email.com
```

**Windows PowerShell:**

```powershell
# From project root
.\scripts\bootstrap-ssl.ps1 your@email.com
```

Or set the email in the environment:

```bash
export LETSENCRYPT_EMAIL=your@email.com
./scripts/bootstrap-ssl.sh
```

The script will:

1. Start the stack with HTTP-only nginx (`nginx.no-ssl.conf`) so port 80 serves the ACME challenge.
2. Run certbot to obtain a certificate for teacherplatform.duckdns.org.
3. Switch nginx to the SSL config and restart it.

After it finishes, the app is available at **https://teacherplatform.duckdns.org**.

### Option B: Manual steps

1. Start with HTTP-only nginx:

   ```bash
   export NGINX_CONF=nginx.no-ssl.conf   # or set in .env
   docker compose -f docker-compose.prod.yml up -d
   ```

2. Obtain the certificate:

   ```bash
   docker compose -f docker-compose.prod.yml run --rm certbot certonly \
     --webroot -w /var/www/certbot \
     -d teacherplatform.duckdns.org \
     --email YOUR_EMAIL \
     --agree-tos \
     --non-interactive
   ```

3. Switch to SSL and restart nginx:

   ```bash
   unset NGINX_CONF   # or remove NGINX_CONF from .env
   docker compose -f docker-compose.prod.yml up -d --force-recreate nginx
   ```

## Certificate renewal

Certificates expire in about 90 days. Renew and reload nginx:

```bash
docker compose -f docker-compose.prod.yml run --rm certbot renew
docker compose -f docker-compose.prod.yml exec nginx nginx -s reload
```

To automate renewal, add a cron job (Linux) or scheduled task (Windows) that runs the above (e.g. twice per month).

## Troubleshooting

- **Nginx fails to start with "cannot load certificate"**  
  Certificates are not present yet. Use the bootstrap procedure above (start with `NGINX_CONF=nginx.no-ssl.conf`, run certbot, then switch to `nginx.conf`).

- **Certbot fails with connection or validation errors**  
  Ensure teacherplatform.duckdns.org points to this host and that port 80 is open from the internet. While bootstrapping, nginx must be running with `nginx.no-ssl.conf` so `/.well-known/acme-challenge/` is served.

- **Using a different domain**  
  Set `LETSENCRYPT_DOMAIN` when running the bootstrap script, and ensure `nginx.conf` and `nginx.no-ssl.conf` use the same `server_name`.
