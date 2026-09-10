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

Deploys are automated — see [Automated Deploys](#automated-deploys-github-actions) below. Push to `main`, or run the workflow manually to deploy any branch.

To deploy from the server by hand (fallback, e.g. if GitHub Actions is down):

```bash
cd /opt/teacherplatform
scripts/deploy.sh              # deploys main
scripts/deploy.sh my-feature   # deploys any branch
scripts/deploy.sh a9d4186      # deploys, or rolls back to, any commit
```

The script pulls a prebuilt image from GHCR — it does not compile. It resets the
working tree and pulls the image tag for the same commit, so the running code and
the on-disk nginx config / `web/` bundle can never drift apart.

**Rollback** is just an older commit: every build keeps its own tag, so
`scripts/deploy.sh <old-sha>` reverts in about 20 seconds without a rebuild.

### Schema changes come first

There is no Flyway or Liquibase. `backend/docker/init/000_consolidated.sql` only
runs on an empty database, so an existing production database is migrated by
hand, from `backend/docker/migrations/`.

Production runs Hibernate with `ddl-auto: validate`, which means **the API will
refuse to start if the new code expects a column the database does not have.**
So the order is not optional:

```bash
# 1. Apply the migration FIRST, against the running database
docker compose -f backend/docker-compose.prod.yml exec -T db \
  psql -U teacher -d teacher_videos < backend/docker/migrations/001_course_validity.sql

# 2. Then deploy the code
scripts/deploy.sh main
```

Migrations are written to be safe to run twice (`ADD COLUMN IF NOT EXISTS`), so
re-running one after a rollback does no harm. A rollback to code that predates a
column is also fine — the old code simply ignores it.

If CI is unavailable and you must build on the server itself (needs ~1.5 GB free
RAM and several GB of disk — this is what used to fill the root volume):

```bash
docker compose -f backend/docker-compose.prod.yml build api
docker compose -f backend/docker-compose.prod.yml up -d api
```

---

## Automated Deploys (GitHub Actions)

`.github/workflows/deploy.yml` SSHs into the EC2 box and runs `scripts/deploy.sh`.

**Triggers**

- **Push to `main`** → deploys automatically.
- **Manual** → **Actions** tab → **Deploy** → **Run workflow**, then pick any branch from the dropdown. No branch name is typed; the workflow reads GitHub's own branch selector (`github.ref_name`). Useful for testing a branch on the real server before merging. CLI equivalent: `gh workflow run deploy.yml --ref my-feature`.

> The dropdown only lists branches that contain `.github/workflows/deploy.yml`. A branch created before this workflow existed won't appear until `main` is merged into it.

**What a deploy does**

The workflow has two jobs. `build` runs on GitHub's runners; `deploy` touches the server.

1. **build** — compiles the image from `backend/Dockerfile` and pushes it to GHCR as
   `ghcr.io/singhvipul892/teachingplatform-api`, tagged with the 7-character commit
   SHA and `latest`. Dependency resolution is a separate Docker layer and the layer
   cache persists between runs, so a source-only change rebuilds in well under a minute.
2. **deploy** — SSHes in, `git fetch` + `git reset --hard` to the deployed commit (not
   `git pull` — avoids conflicts if the server's tree drifts; untracked `.env` survives),
   then runs `scripts/deploy.sh`.
3. That script **pulls** the image tagged with the same commit and restarts **only** the
   `api` service — the `db` container is never touched, so there's no database downtime.
4. Reloads nginx. Static `web/` files need no rebuild at all; they're a live bind mount,
   so the `git reset` alone updates the site.
5. Prunes dangling images, then prints disk usage.
6. Smoke-tests the login page and fails the run if the site doesn't come back.

> The server no longer compiles anything. It used to run `docker compose up -d --build`,
> which pulled the ~1 GB `gradle:8.7-jdk21` image and ran a Gradle build alongside the
> live API and Postgres — the cause of the disk-and-memory pressure below.

**GHCR access:** the simplest setup is a **public** package (GitHub → Packages →
`teachingplatform-api` → Package settings → Change visibility). The image holds no
secrets — those all arrive via `.env` at runtime — and a public package means no
registry credentials on the server at all. To keep it private instead, put a
read-only PAT in the server's environment as `GHCR_USER` / `GHCR_TOKEN`;
`scripts/deploy.sh` logs in automatically when they are set.

**Secrets** live in the `teacher-platform-prod` environment (Settings → Environments → `teacher-platform-prod` → Environment secrets), not in repo-level secrets:

| Secret | Value |
|---|---|
| `EC2_HOST` | Elastic IP or `teacherplatform.duckdns.org` |
| `EC2_USER` | `ec2-user` |
| `EC2_SSH_KEY` | Private key content (the `.pem` you use to SSH in) |
| `EC2_SSH_PORT` | Optional, defaults to `22` |

**One-time server prerequisite:** the repo must be owned by the SSH user, or git refuses to touch it:

```bash
sudo chown -R ec2-user:ec2-user /opt/teacherplatform
```

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

This should no longer happen: builds moved to GitHub Actions, so the server never
runs Gradle and never grows a build cache. Only pulled images accumulate, and
`scripts/deploy.sh` prunes dangling ones after every deploy. If you hit it anyway
(e.g. after a manual on-server `build` fallback), reclaim space with:

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
