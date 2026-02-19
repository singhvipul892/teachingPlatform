# SSL, nginx, and certificates – simple overview

This document explains what we set up for HTTPS on teacherplatform.duckdns.org and how it all fits together. No prior SSL or nginx experience assumed.

---

## 1. What’s what (in simple terms)

**HTTPS (SSL/TLS)**  
When you use **https://** instead of **http://**, the connection is **encrypted**. Browsers show a padlock. That’s what we set up.

**Certificate**  
A small “proof” file that says “this server really is teacherplatform.duckdns.org.” Browsers trust it only if it’s signed by a known authority (we use **Let’s Encrypt** – free and automatic).

**nginx**  
A **reverse proxy**: it sits in front of your app. When someone visits your domain:

- Their request hits **nginx** first (on ports 80 and 443).
- Nginx then forwards the request to your **API** (the Spring Boot app).
- For HTTPS, nginx uses the **certificate** to do the encryption; your API can stay on plain HTTP inside the server.

So: **User ↔ nginx (with certificate) ↔ your API.**

---

## 2. The problem we had

Your nginx config said: “Use this certificate file for HTTPS.”  
But that file **didn’t exist yet** – you get certificates only **after** proving you control the domain. To prove that, Let’s Encrypt needs to reach your server on **port 80** and read a special file. So:

- Nginx wouldn’t start without the certificate.
- You couldn’t get the certificate without something (like nginx) serving the proof on port 80.

That’s the “chicken and egg” we fixed.

---

## 3. What we did (consolidated)

### A. Two nginx configs

- **`nginx/nginx.conf`**  
  “Normal” production config:  
  - Port 80: redirect to HTTPS and serve the Let’s Encrypt challenge path.  
  - Port 443: use the certificate and proxy to your API.  
  - **Needs the certificate files to exist** – otherwise nginx won’t start.

- **`nginx/nginx.no-ssl.conf`**  
  “Bootstrap” config:  
  - Only port 80, no certificate.  
  - Serves the Let’s Encrypt challenge path and proxies everything else to the API.  
  - **Can start even when there are no certificates.**

So: first we use **no-ssl** to get the cert, then we switch to the **full** config.

### B. Docker setup (`docker-compose.prod.yml`)

- **Named volumes**  
  `letsencrypt_certs` and `certbot_webroot` are shared “disks” so that:
  - **Certbot** (the tool that gets the certificate) can write the challenge files and the cert.
  - **Nginx** can read the same cert and serve the challenge.  
  So both containers see the same certificate and webroot.

- **Certbot service**  
  A container that runs the **certbot** program. It’s not a long-running server; you run it once (or for renewal) with a command like:  
  `docker compose run --rm certbot certonly --webroot ...`  
  We put it under a **profile** so that a normal `docker compose up` does **not** start certbot (avoids the “exited with code 1” message). Certbot is only run when you explicitly call it (or use the bootstrap script).

- **Which nginx config is used**  
  Controlled by **`NGINX_CONF`** (e.g. in `.env`):
  - `NGINX_CONF=nginx.no-ssl.conf` → use the bootstrap config (no certificate required).
  - Unset or `NGINX_CONF=nginx.conf` → use the full SSL config (certificate required).

### C. Bootstrap scripts

- **`scripts/bootstrap-ssl.sh`** (Linux/macOS) and **`scripts/bootstrap-ssl.ps1`** (Windows) do the same thing in three steps:
  1. Start the stack with **no-SSL** nginx (`NGINX_CONF=nginx.no-ssl.conf`).
  2. Run **certbot** once to get the certificate (Let’s Encrypt checks port 80, finds the challenge, issues the cert; certbot saves it into the shared volume).
  3. Switch to **SSL** nginx (`nginx.conf`) and restart nginx so it uses the new certificate.

So one script does the whole “first-time SSL” flow.

### D. Firewall / security group

Let’s Encrypt’s servers must reach your server on **port 80** (and later users on **443**). On EC2 we opened:

- **Port 80** (HTTP) – needed for the certificate check (and for redirect to HTTPS).
- **Port 443** (HTTPS) – needed for your actual site.

Without that, you’d see “Timeout during connect (likely firewall problem).”

### E. Documentation and small fixes

- **`docs/SSL-SETUP.md`**  
  Describes: first-time bootstrap (script + manual steps), renewal, and basic troubleshooting.

- **Bootstrap script**  
  We made it:
  - Run correctly from any directory (it finds the project root).
  - Work with both `docker compose` and `docker-compose`.
  - Show clear usage (e.g. `bash scripts/bootstrap-ssl.sh YOUR_EMAIL`).

---

## 4. How it all fits together (flow)

1. **First time (no certificate yet)**  
   - You run the bootstrap script with your email.  
   - Script starts nginx with **no-SSL** config → nginx listens on 80.  
   - Script runs certbot → certbot creates a challenge file in the shared volume, Let’s Encrypt hits your server on 80, sees the file, and issues the certificate; certbot saves it in the same volume.  
   - Script switches nginx to **SSL** config and restarts → nginx now uses that certificate and serves HTTPS on 443.  
   - After that, **https://teacherplatform.duckdns.org** works with a padlock.

2. **Normal runs**  
   - You use `docker compose up` (or `up -d`).  
   - Nginx starts with **nginx.conf** (full SSL) and reads the certificate from the shared volume.  
   - Certbot container does **not** start (profile); you only run it when you need to obtain or renew the cert.

3. **Renewal (every ~90 days)**  
   - Run certbot renew, then reload nginx (steps are in `docs/SSL-SETUP.md`).  
   - You can automate this with a cron job (or scheduled task on Windows).

---

## 5. Quick reference

| What | Where / How |
|------|------------------|
| Full SSL nginx config (needs cert) | `nginx/nginx.conf` |
| Bootstrap nginx config (no cert) | `nginx/nginx.no-ssl.conf` |
| Switch config | Set `NGINX_CONF=nginx.no-ssl.conf` or `nginx.conf` (e.g. in `.env`) |
| First-time SSL setup | Run `bash scripts/bootstrap-ssl.sh your@email.com` (from project root) |
| Cert storage (inside Docker) | Volumes `letsencrypt_certs` and `certbot_webroot` in `docker-compose.prod.yml` |
| Cert renewal + reload | See `docs/SSL-SETUP.md` (and set up cron/scheduled task) |
| Why certbot doesn’t start on `up` | Certbot service has a **profile** so it only runs when you call it (or the script). |

---

## See also

- [SSL-SETUP.md](SSL-SETUP.md) – Step-by-step first-time setup, renewal, and troubleshooting.
