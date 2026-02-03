# EC2 Backend Deployment Plan

Deploy the Spring Boot backend on a single EC2 instance using Docker Compose. Everything (secrets, JWT, DB credentials, ports) stays as-is. After deployment you get:

- **Swagger UI** at `http://<EC2-PUBLIC-IP>:8080/swagger-ui/index.html`
- **pgAdmin** at `http://<EC2-PUBLIC-IP>:5050` to connect to the PostgreSQL database on the same EC2

---

## 1. What Runs on EC2

| Service   | Port | Purpose                          |
|----------|------|----------------------------------|
| API      | 8080 | Spring Boot app + Swagger        |
| PostgreSQL | 5432 | Database (internal to host)    |
| pgAdmin  | 5050 | Web UI to manage PostgreSQL      |

Credentials and config are taken from your existing setup (see section 3).

---

## 2. Prerequisites

- AWS account, region **ap-south-1** (same as S3 bucket).
- Codebase on your machine (or in a repo you can clone on EC2).
- IAM role for EC2 with S3 access for bucket `teacherplatform.503561455300` (e.g. `s3:GetObject`, `s3:ListBucket`, `s3:PutObject` if you upload PDFs from backend).

---

## 3. Credentials & Ports (Use As-Is)

Use these values so everything matches your current setup.

| Variable | Value (as-is) |
|----------|----------------|
| **DB** | |
| `POSTGRES_DB` | `teacher_videos` |
| `POSTGRES_USER` | `teacher` |
| `POSTGRES_PASSWORD` | `teacher` |
| **API** | |
| `JWT_SECRET` | `change-me-in-production-use-long-secret-key-at-least-32-chars` (or your existing secret) |
| `APP_STORAGE_S3_BUCKET` | `teacherplatform.503561455300` |
| `APP_STORAGE_S3_REGION` | `ap-south-1` |
| **pgAdmin** | |
| `PGADMIN_EMAIL` | `admin@local.com` |
| `PGADMIN_PASSWORD` | `admin` |

Ports: **8080** (API), **5050** (pgAdmin), **5432** (PostgreSQL, optional to expose; not required for pgAdmin when it runs on same host).

---

## 4. EC2 Setup

### 4.1 Launch instance

1. **Region**: ap-south-1 (Mumbai).
2. **AMI**: Amazon Linux 2023 or Ubuntu 22.04 LTS.
3. **Instance type**: e.g. **t3.small** (or t3.micro for minimal cost; small is more comfortable for Docker + DB + API).
4. **Key pair**: Create or select one; download the `.pem` and keep it safe.
5. **Network**: Default VPC or your preferred VPC.
6. **Storage**: 20–30 GB.
7. **IAM role**: Attach the role that has S3 access for your bucket.
8. **Security group** (create new or edit existing):
   - **22** (SSH): Your IP only.
   - **8080** (API/Swagger): `0.0.0.0/0` (or restrict to your IP / VPN if you prefer).
   - **5050** (pgAdmin): `0.0.0.0/0` (or restrict to your IP).
   - **5432** (PostgreSQL): Optional; only if you need external DB clients. For pgAdmin on EC2, leave this closed.

9. Launch the instance.
10. Allocate an **Elastic IP** and associate it to this instance so the IP does not change after restarts.

### 4.2 Connect and install Docker

```bash
# Replace with your key and Elastic IP
ssh -i /path/to/your-key.pem ec2-user@<ELASTIC-IP>
# Ubuntu: ssh -i /path/to/your-key.pem ubuntu@<ELASTIC-IP>
```

**Amazon Linux 2023:**

```bash
sudo dnf update -y
sudo dnf install -y docker
sudo systemctl enable docker
sudo systemctl start docker
sudo usermod -aG docker ec2-user
# Log out and log back in so docker runs without sudo
```

**Ubuntu 22.04:**

```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y docker.io
sudo systemctl enable docker
sudo systemctl start docker
sudo usermod -aG docker ubuntu
# Log out and log back in
```

Install Docker Compose v2 (plugin):

```bash
sudo mkdir -p /usr/local/lib/docker/cli-plugins
sudo curl -SL "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/lib/docker/cli-plugins/docker-compose
sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
docker compose version
```

---

## 5. Deploy Backend on EC2

### 5.1 Copy project to EC2

From your **local machine** (PowerShell or Git Bash):

```bash
# Option A: rsync (if you have it)
rsync -avz -e "ssh -i /path/to/your-key.pem" --exclude '.git' --exclude 'android' ./ ec2-user@<ELASTIC-IP>:~/app/

# Option B: clone from Git on EC2 (after SSH)
# On EC2:
sudo dnf install -y git
git clone <your-repo-url> ~/app
cd ~/app
```

Ensure on EC2 you have at least:

- `docker-compose.prod.yml`
- `backend/` (Dockerfile, `docker/init/`, `src/`, `build.gradle`, etc.)

### 5.2 Create `.env` on EC2 (same values as above)

SSH into EC2, then:

```bash
cd ~/app
nano .env
```

Paste (adjust only if you intentionally change something):

```env
# DB – use as-is
POSTGRES_DB=teacher_videos
POSTGRES_USER=teacher
POSTGRES_PASSWORD=teacher

# JWT – use as-is (or your existing secret)
JWT_SECRET=change-me-in-production-use-long-secret-key-at-least-32-chars

# S3 – use as-is
APP_STORAGE_S3_BUCKET=teacherplatform.503561455300
APP_STORAGE_S3_REGION=ap-south-1
APP_STORAGE_S3_PRESIGN_EXPIRY_MINUTES=10

# pgAdmin – use as-is
PGADMIN_EMAIL=admin@local.com
PGADMIN_PASSWORD=admin
```

Save and exit (`Ctrl+O`, `Enter`, `Ctrl+X`).

### 5.3 Start stack with Docker Compose

On EC2:

```bash
cd ~/app
docker compose -f docker-compose.prod.yml up -d --build
```

Check that all containers are running:

```bash
docker compose -f docker-compose.prod.yml ps
```

You should see `db`, `api`, and `pgadmin` running.

---

## 6. Post-Deployment: Swagger & pgAdmin

### 6.1 Swagger URL

- **Swagger UI**: `http://<ELASTIC-IP>:8080/swagger-ui/index.html`
- **OpenAPI JSON**: `http://<ELASTIC-IP>:8080/v3/api-docs`

If you get “connection refused”, check:

- Security group allows **8080** from your IP (or 0.0.0.0/0).
- `docker compose -f docker-compose.prod.yml logs api` for errors.

### 6.2 pgAdmin – connect to database on EC2

1. Open: `http://<ELASTIC-IP>:5050`
2. Log in with:
   - Email: `admin@local.com`
   - Password: `admin`
3. Add a new server in pgAdmin:
   - **General** tab: Name = e.g. `Teacher DB`
   - **Connection** tab:
     - **Host**: `db` (Docker service name; pgAdmin and Postgres are in the same Docker network)
     - **Port**: `5432`
     - **Maintenance database**: `teacher_videos`
     - **Username**: `teacher`
     - **Password**: `teacher`
   - Save.

You should see the `teacher_videos` database and tables (`videos`, `video_pdfs`, etc.).

---

## 7. Useful Commands on EC2

```bash
# View logs
docker compose -f docker-compose.prod.yml logs -f
docker compose -f docker-compose.prod.yml logs -f api
docker compose -f docker-compose.prod.yml logs -f db

# Restart after code/config change
docker compose -f docker-compose.prod.yml up -d --build

# Stop everything
docker compose -f docker-compose.prod.yml down

# Stop but keep DB data (volumes)
docker compose -f docker-compose.prod.yml down
docker compose -f docker-compose.prod.yml up -d --build
```

---

## 8. Checklist

- [ ] EC2 in **ap-south-1** with IAM role for S3
- [ ] Security group: 22 (SSH), 8080 (API), 5050 (pgAdmin)
- [ ] Elastic IP attached
- [ ] Docker and Docker Compose installed
- [ ] Project and `docker-compose.prod.yml` on EC2
- [ ] `.env` created with DB, JWT, S3, pgAdmin values (as-is)
- [ ] `docker compose -f docker-compose.prod.yml up -d --build` run successfully
- [ ] Swagger: `http://<ELASTIC-IP>:8080/swagger-ui/index.html` works
- [ ] pgAdmin: `http://<ELASTIC-IP>:5050` login and add server with host `db`, port 5432, user `teacher`, password `teacher`

---

## 9. Optional: Restrict pgAdmin / 8080

For better security you can:

- Limit **5050** and **8080** in the security group to your IP or office/VPN CIDR instead of `0.0.0.0/0`.
- Use HTTPS in front of the API (e.g. Nginx + Let’s Encrypt) and optionally close direct access to 8080 from the internet.

This plan keeps everything as-is; you can tighten access after the first successful deployment.
