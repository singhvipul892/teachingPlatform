# Deployment Guide

## Local Development

```bash
# 1. Start the database
docker compose up -d

# 2. Run the backend
cd backend
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

DB is available at `localhost:5432` (user: `teacher`, password: `teacher`, db: `teacher_videos`).

---

## Production — Fresh EC2 Instance

### 1. Install Docker (Amazon Linux 2023)

```bash
sudo dnf update -y
sudo dnf install -y docker git

sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user && newgrp docker

# Install Docker Compose plugin
COMPOSE_VERSION=$(curl -s https://api.github.com/repos/docker/compose/releases/latest | grep '"tag_name"' | cut -d'"' -f4)
sudo mkdir -p /usr/local/lib/docker/cli-plugins
sudo curl -SL "https://github.com/docker/compose/releases/download/${COMPOSE_VERSION}/docker-compose-linux-x86_64" \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

# Verify
docker --version && docker compose version
```

### 2. Clone and configure

```bash
git clone https://github.com/singhvipul892/teachingPlatform.git /opt/teacherplatform
cd /opt/teacherplatform

cp .env.example .env
chmod 600 .env
nano .env   # Fill in every CHANGE_ME value (see notes below)
```

**Required `.env` values:**

| Variable | How to get it |
|---|---|
| `POSTGRES_PASSWORD` | Choose a strong password |
| `SPRING_DATASOURCE_PASSWORD` | Same as `POSTGRES_PASSWORD` |
| `JWT_SECRET` | Run: `openssl rand -hex 64` |
| `RAZORPAY_KEY_ID` | Razorpay dashboard → Live keys |
| `RAZORPAY_KEY_SECRET` | Razorpay dashboard → Live keys |
| `APP_STORAGE_S3_BUCKET` | Your S3 bucket name |

> S3 credentials are NOT needed in `.env` — the EC2 IAM role is used automatically.
> Attach an IAM role to the instance with `s3:GetObject`, `s3:PutObject`, `s3:DeleteObject`
> on `arn:aws:s3:::YOUR_BUCKET/*`.

### 3. Bootstrap SSL (first deploy only)

```bash
# Use the no-ssl nginx config until certs exist
sed -i 's/^NGINX_CONF=.*/NGINX_CONF=nginx.no-ssl.conf/' .env

# Start DB, nginx, and metadata-proxy only
docker compose -f backend/docker-compose.prod.yml up -d db nginx metadata-proxy

# Wait for DB to be healthy
docker compose -f backend/docker-compose.prod.yml ps

# Obtain SSL certificate
docker compose -f backend/docker-compose.prod.yml run --rm certbot \
  certonly --webroot -w /var/www/certbot \
  -d teacherplatform.duckdns.org \
  --email YOUR_EMAIL@example.com \
  --agree-tos --non-interactive

# Switch to full SSL config
sed -i 's/^NGINX_CONF=.*/NGINX_CONF=nginx.conf/' .env
```

### 4. Start the full stack

```bash
docker compose -f backend/docker-compose.prod.yml up -d --build
```

### 5. Verify

```bash
# All containers should show "running" or "healthy"
docker compose -f backend/docker-compose.prod.yml ps

# API logs (watch for "Started Application" or errors)
docker compose -f backend/docker-compose.prod.yml logs api --tail=100

# DB connectivity
docker compose -f backend/docker-compose.prod.yml exec db \
  pg_isready -U teacher -d teacher_videos

# HTTPS endpoint
curl -I https://teacherplatform.duckdns.org
```

---

## Subsequent Deploys

```bash
cd /opt/teacherplatform
git pull

# Rebuild and restart the API only (zero DB downtime)
docker compose -f backend/docker-compose.prod.yml up -d --build api

# Or rebuild everything
docker compose -f backend/docker-compose.prod.yml up -d --build
```

Or just run `scripts/deploy.sh [branch]` (defaults to `main`) — it does the same thing plus an nginx reload.

---

## Automated Deploys (GitHub Actions)

`.github/workflows/deploy.yml` SSHs into the EC2 box and runs `scripts/deploy.sh`:

- **Push to `main`** → deploys automatically.
- **Manual dispatch** → **Actions** tab → **Deploy** → **Run workflow**, then pick the branch from the dropdown (it lists every branch that has this workflow file). Useful for testing a branch on the real server before merging. CLI equivalent: `gh workflow run deploy.yml --ref my-feature`.

Required GitHub repo secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `EC2_HOST` | Elastic IP or `teacherplatform.duckdns.org` |
| `EC2_USER` | `ec2-user` |
| `EC2_SSH_KEY` | Private key content (the `.pem` you use to SSH in) |
| `EC2_SSH_PORT` | Optional, defaults to `22` |

---

## Database Backups

Daily automated backup to S3, set up once per server:

```bash
# One-time: install AWS CLI if not already present
sudo dnf install -y awscli

# One-time: register the daily cron (runs at 2 AM, logs to /var/log/db-backup.log)
(crontab -l 2>/dev/null; echo "0 2 * * * /opt/teacherplatform/scripts/backup-db.sh >> /var/log/db-backup.log 2>&1") | crontab -
```

`scripts/backup-db.sh` dumps `teacher_videos`, gzips it, uploads to `s3://teacherplatform.503561455300/db-backups/`, and keeps 7 days of local copies. It uses the EC2 instance's existing IAM role (same one used for PDF storage) — no extra credentials needed.

To avoid unbounded S3 storage growth, add a lifecycle rule on the bucket to expire objects under `db-backups/` after 30–90 days (S3 console → bucket → Management → Lifecycle rules).

To restore a backup:

```bash
gunzip -c teacher_videos_2026-09-06_020000.sql.gz | \
  docker compose -f backend/docker-compose.prod.yml exec -T db psql -U teacher -d teacher_videos
```

---

## SSL Certificate Renewal

Set up a cron job on the EC2 instance (run once after first deploy):

```bash
(crontab -l 2>/dev/null; echo "0 3 * * * cd /opt/teacherplatform && docker compose -f backend/docker-compose.prod.yml run --rm certbot renew --quiet && docker compose -f backend/docker-compose.prod.yml exec nginx nginx -s reload") | crontab -
```

---

## Troubleshooting

**`no space left on device` during a build**

Each `--build` leaves the previous `api` image dangling and grows the Gradle build cache. `scripts/deploy.sh` now prunes both after every successful deploy, but to reclaim space manually:

```bash
df -h / && docker system df
docker builder prune -af && docker image prune -f
```

> **Never** run `docker volume prune` or pass `--volumes` to a prune command — the `teacher_db` volume holds the entire database.

If this keeps happening, the root EBS volume is likely too small — 30 GB is a comfortable size for the Gradle build image plus a few image generations.

**`fatal: detected dubious ownership` during a deploy**

The repo is owned by `root` (cloned by EC2 user-data) but deploys run as `ec2-user`:

```bash
sudo chown -R ec2-user:ec2-user /opt/teacherplatform
```

This also matters for `.env`, which is `chmod 600` — if `ec2-user` can't read it, `docker compose` silently substitutes **empty** values for `JWT_SECRET`, DB credentials, and Razorpay keys.

---

## Useful Commands

```bash
# View logs for a service
docker compose -f backend/docker-compose.prod.yml logs <service> -f

# Restart a single service
docker compose -f backend/docker-compose.prod.yml restart <service>

# Open a DB shell
docker compose -f backend/docker-compose.prod.yml exec db psql -U teacher -d teacher_videos

# Access pgadmin (via SSH tunnel — never expose port 5050 publicly)
#   On your local machine: ssh -L 5050:localhost:5050 ec2-user@<EC2_IP>
docker compose -f backend/docker-compose.prod.yml --profile tools up -d pgadmin
# Then open: http://localhost:5050
```
