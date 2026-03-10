# Backend Deployment Guide

Production deployment on AWS EC2 using Docker Compose, Nginx, and Let's Encrypt SSL.

> For basic EC2 setup (launch instance, install Docker, IAM role), see [`EC2_BACKEND_DEPLOYMENT_PLAN.md`](EC2_BACKEND_DEPLOYMENT_PLAN.md).

---

## Prerequisites

- EC2 instance running (t3.small recommended, ap-south-1)
- IAM role attached with S3 permissions on `teacherplatform.503561455300`
- Security group open on ports: **22** (SSH), **80** (HTTP), **443** (HTTPS)
- Domain `teacherplatform.duckdns.org` pointing to EC2 Elastic IP
- Docker + Docker Compose plugin installed

---

## 1. Get the Code on EC2

```bash
# Option A: rsync from local machine
rsync -avz -e "ssh -i /path/to/key.pem" --exclude '.git' --exclude 'android' \
  ./ ec2-user@<ELASTIC-IP>:~/app/

# Option B: clone from Git on EC2
git clone <your-repo-url> ~/app
cd ~/app
```

---

## 2. Create `.env`

```bash
cd ~/app
nano .env
```

Paste and fill in your values:

```env
# Database
POSTGRES_DB=teacher_videos
POSTGRES_USER=teacher
POSTGRES_PASSWORD=teacher

# JWT — use a long random secret (32+ chars)
JWT_SECRET=change-me-to-a-long-random-secret-at-least-32-chars

# S3 (uses EC2 IAM role for credentials — no keys needed)
APP_STORAGE_S3_BUCKET=teacherplatform.503561455300
APP_STORAGE_S3_REGION=ap-south-1
APP_STORAGE_S3_PRESIGN_EXPIRY_MINUTES=10

# Razorpay
RAZORPAY_KEY_ID=<your-razorpay-key-id>
RAZORPAY_KEY_SECRET=<your-razorpay-key-secret>

# SMS — set false to use real SMS, true to skip in dev
SMS_MOCK=false

# Nginx — use no-ssl.conf for first deploy, switch to nginx.conf after SSL
NGINX_CONF=nginx.no-ssl.conf

# pgAdmin (accessed via SSH tunnel only — not exposed publicly)
PGADMIN_EMAIL=admin@local.com
PGADMIN_PASSWORD=admin
```

---

## 3. First Deploy (HTTP only)

Deploy without SSL first so Certbot can verify domain ownership over HTTP.

```bash
docker compose -f docker-compose.prod.yml up -d --build
```

Check all containers are up:

```bash
docker compose -f docker-compose.prod.yml ps
```

Expected: `db`, `api`, `nginx`, `metadata-proxy` all running.

Verify API is reachable:

```bash
curl http://teacherplatform.duckdns.org/api/courses
# Should return JSON (empty array [] if no courses yet)
```

---

## 4. Get SSL Certificate (Let's Encrypt)

```bash
docker compose -f docker-compose.prod.yml run --rm \
  --profile certbot certbot certonly \
  --webroot -w /var/www/certbot \
  -d teacherplatform.duckdns.org \
  --email <your-email> \
  --agree-tos --non-interactive
```

On success you'll see: `Certificate is saved at /etc/letsencrypt/live/teacherplatform.duckdns.org/`

---

## 5. Switch to SSL

Update `.env`:

```env
NGINX_CONF=nginx.conf
```

Reload nginx:

```bash
docker compose -f docker-compose.prod.yml up -d nginx
```

---

## 6. Verify

```bash
# API
curl https://teacherplatform.duckdns.org/api/courses

# Admin dashboard
open https://teacherplatform.duckdns.org/admin/

# Student web app
open https://teacherplatform.duckdns.org/student/
```

---

## 7. Useful Commands

```bash
# Logs
docker compose -f docker-compose.prod.yml logs -f api
docker compose -f docker-compose.prod.yml logs -f db

# Redeploy after code change
git pull
docker compose -f docker-compose.prod.yml up -d --build api

# Restart nginx (after config/cert change)
docker compose -f docker-compose.prod.yml restart nginx

# Stop everything (preserves DB data)
docker compose -f docker-compose.prod.yml down
```

---

## 8. pgAdmin (database inspection)

pgAdmin is bound to localhost only. Access it via SSH tunnel:

```bash
# On your local machine
ssh -i /path/to/key.pem -L 5050:localhost:5050 ec2-user@<ELASTIC-IP>
```

Then open `http://localhost:5050` in your browser.

Start pgAdmin:

```bash
docker compose -f docker-compose.prod.yml --profile tools up -d pgadmin
```

Connection settings inside pgAdmin:
- Host: `db`
- Port: `5432`
- Database: `teacher_videos`
- Username: `teacher`
- Password: `teacher`

---

## 9. SSL Renewal

Certbot renews automatically if you run it periodically. Add a cron job on EC2:

```bash
crontab -e
# Add:
0 3 * * * cd ~/app && docker compose -f docker-compose.prod.yml run --rm --profile certbot certbot renew && docker compose -f docker-compose.prod.yml restart nginx
```

---

## Checklist

- [ ] `.env` created with all required values
- [ ] `NGINX_CONF=nginx.no-ssl.conf` for first boot
- [ ] `docker compose -f docker-compose.prod.yml up -d --build` ran successfully
- [ ] `curl http://teacherplatform.duckdns.org/api/courses` returns JSON
- [ ] SSL cert obtained via Certbot
- [ ] `NGINX_CONF=nginx.conf` and nginx reloaded
- [ ] `curl https://teacherplatform.duckdns.org/api/courses` returns JSON
- [ ] Razorpay payment flow tested end-to-end
- [ ] SSL renewal cron job added
