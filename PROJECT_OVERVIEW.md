# Teacher Platform — Project Overview

A full-stack educational platform that lets teachers share video lessons and PDF resources with students. It consists of a **Spring Boot REST API**, an **Android mobile app**, and a **Docker-based production deployment** with PostgreSQL, Nginx, and Let's Encrypt SSL.

---

## Table of Contents

1. [Project Structure](#project-structure)
2. [Tech Stack](#tech-stack)
3. [Functionality](#functionality)
4. [Architecture](#architecture)
5. [Database Schema](#database-schema)
6. [API Endpoints](#api-endpoints)
7. [Infrastructure & Deployment](#infrastructure--deployment)
8. [Configuration Reference](#configuration-reference)

---

## Project Structure

```
cursor/
├── backend/                        # Spring Boot REST API
│   ├── src/main/java/              # Java source (layered architecture)
│   ├── src/main/resources/         # application.yml, application-dev.yml
│   ├── docker/init/                # PostgreSQL init SQL scripts
│   ├── Dockerfile                  # Multi-stage Docker build
│   ├── docker-compose.yml          # Dev-only compose file
│   └── build.gradle                # Gradle build config
│
├── android/                        # Android Kotlin app
│   ├── app/src/main/java/          # Kotlin source (MVVM)
│   ├── app/src/main/res/           # Resources
│   ├── app/build.gradle.kts        # App build config
│   └── build.gradle.kts            # Root Gradle config
│
├── docker/
│   └── metadata-proxy/             # AWS EC2 metadata proxy (socat)
│
├── nginx/
│   ├── nginx.conf                  # Production config (HTTPS + SSL)
│   └── nginx.no-ssl.conf           # Bootstrap config (HTTP only)
│
├── scripts/
│   ├── bootstrap-ssl.sh            # SSL setup automation (Linux/macOS)
│   └── bootstrap-ssl.ps1           # SSL setup automation (Windows)
│
├── docs/                           # Deployment and architecture docs
├── docker-compose.yml              # Local development environment
└── docker-compose.prod.yml         # Production environment
```

---

## Tech Stack

### Backend

| Layer | Technology | Version |
|-------|-----------|---------|
| Language | Java | 21 |
| Framework | Spring Boot | 3.3.4 |
| Build Tool | Gradle | 8.7 |
| ORM | Spring Data JPA / Hibernate | — |
| Security | Spring Security + JWT (jjwt) | 0.12.5 |
| Database Driver | PostgreSQL JDBC | — |
| Cloud Storage | AWS SDK v2 (S3) | 2.26.11 |
| API Docs | SpringDoc OpenAPI (Swagger UI) | 2.5.0 |

### Android

| Layer | Technology | Version |
|-------|-----------|---------|
| Language | Kotlin | 1.9.24 |
| UI Framework | Jetpack Compose | BOM 2024.09.03 |
| Min / Target SDK | Android | 24 / 35 |
| HTTP Client | Retrofit | 2.11.0 |
| HTTP Logging | OkHttp | 4.12.0 |
| Image Loading | Coil | 2.6.0 |
| Async | Kotlin Coroutines | 1.8.1 |
| Local Storage | DataStore Preferences | 1.0.0 |
| Navigation | Navigation Compose | — |

### Infrastructure

| Component | Technology |
|-----------|-----------|
| Database | PostgreSQL 16 |
| Reverse Proxy | Nginx 1.27-alpine |
| Containerization | Docker & Docker Compose v2 |
| SSL/TLS | Let's Encrypt + Certbot |
| Cloud Storage | AWS S3 (region: `ap-south-1`) |
| Dynamic DNS | Duck DNS (`teacherplatform.duckdns.org`) |
| DB Admin UI | pgAdmin 4 8.12 |

---

## Functionality

### Authentication

- Users register with **first name, last name, email, mobile number, and password**.
- Login accepts either **email or mobile number** + password.
- Passwords are hashed with Spring Security's `PasswordEncoder`.
- Successful login returns a **JWT token** used in subsequent requests (`Authorization: Bearer <token>`).

### Video Catalog

- Videos are organized into **sections** (e.g., Algebra, Trigonometry, Geometry).
- Each video stores a **YouTube video ID**, title, thumbnail URL, duration, and display order.
- The Android app renders videos grouped by section using a horizontal carousel.
- Videos play inside a **YouTube embed player** (`youtube-nocookie.com`) via `WebView`.

### PDF Resources

- Each video can have **multiple associated PDFs** (Notes, Practice Questions, Formula Sheets).
- PDF files are stored in **AWS S3**.
- Downloads use **presigned S3 URLs** (10-minute expiry) — AWS credentials are never exposed to clients.
- The Android app opens PDFs in a built-in **PDF viewer screen**.

### Content Access Control

- All catalog endpoints require a valid JWT token.
- A `VideoAccessService` handles authorization checks per resource.
- Nginx terminates TLS and proxies requests to the backend container.

---

## Architecture

### Backend — Layered Package Design

```
com.maths.teacher/
├── auth/
│   ├── domain/User.java                   # JPA entity
│   ├── repository/UserRepository.java     # Spring Data repository
│   ├── service/AuthAppService.java        # Registration & login logic
│   ├── service/JwtService.java            # Token generation & validation
│   ├── security/JwtAuthenticationFilter  # Servlet filter
│   ├── config/SecurityConfig.java         # Spring Security rules
│   └── web/AuthController.java            # /api/auth/* endpoints
│
├── catalog/
│   ├── domain/Video.java, VideoPdf.java   # JPA entities
│   ├── service/VideoCatalogService.java   # Catalog queries
│   ├── service/PdfDownloadService.java    # Presigned URL generation
│   ├── service/AdminService.java          # Admin operations
│   └── web/VideoCatalogController.java    # /api/* endpoints
│
├── security/
│   ├── AuthService.java                   # Auth utilities
│   └── VideoAccessService.java            # Per-resource access checks
│
└── storage/
    ├── S3StorageService.java              # S3 object operations
    ├── S3PresignedUrlService.java         # Presigned URL creation
    ├── S3Config.java                      # AWS SDK bean setup
    └── S3Properties.java                  # Bound from application.yml
```

### Android — MVVM

```
com.maths.teacher.app/
├── data/
│   ├── api/TeacherApi.kt                  # Retrofit interface
│   ├── api/ApiClient.kt                   # Retrofit + OkHttp setup
│   ├── model/                             # DTO data classes
│   ├── repository/VideoRepository.kt      # Data access layer
│   └── prefs/SessionManager.kt            # JWT storage (DataStore)
│
├── domain/model/
│   ├── Video.kt
│   └── SectionWithVideos.kt
│
└── ui/
    ├── auth/        LoginScreen, SignupScreen, ViewModels
    ├── home/        HomeScreen, HomeViewModel, YouTubeEmbedPlayer
    ├── videodetail/ VideoDetailScreen
    ├── pdfviewer/   PdfViewerScreen
    ├── resources/   ResourcesScreen
    └── components/  AppHeader, NavigationDrawer, VideoCardCarousel, PdfDownloadSection
```

---

## Database Schema

### `users`

| Column | Type | Notes |
|--------|------|-------|
| id | BIGSERIAL PK | |
| first_name | VARCHAR | |
| last_name | VARCHAR | |
| email | VARCHAR | UNIQUE |
| mobile_number | VARCHAR | UNIQUE |
| password_hash | VARCHAR | bcrypt |
| created_at | TIMESTAMP | default now() |

### `videos`

| Column | Type | Notes |
|--------|------|-------|
| id | BIGSERIAL PK | |
| video_id | VARCHAR | YouTube video ID |
| title | VARCHAR | |
| section | VARCHAR | e.g. "Algebra" |
| thumbnail_url | VARCHAR | |
| duration | VARCHAR | e.g. "12:34" |
| display_order | INT | sort order per section |

### `video_pdfs`

| Column | Type | Notes |
|--------|------|-------|
| id | BIGSERIAL PK | |
| video_id_fk | BIGINT FK | → `videos(id)` CASCADE DELETE |
| title | VARCHAR | |
| pdf_type | VARCHAR | Notes / Practice / Formula |
| file_url | VARCHAR | S3 object key |
| display_order | INT | |
| created_at | TIMESTAMP | |

---

## API Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/auth/signup` | None | Register new user |
| POST | `/api/auth/login` | None | Login, returns JWT |
| GET | `/api/sections` | JWT | List all video sections |
| GET | `/api/sections/{section}/videos` | JWT | Videos in a section |
| GET | `/api/videos/{videoId}/pdfs/{pdfId}/download` | JWT | Presigned S3 URL for PDF |

Swagger UI is available at `/swagger-ui/` when the server is running.

---

## Infrastructure & Deployment

### Local Development

Uses `docker-compose.yml` in the repo root:

```
Services:
  db       → PostgreSQL 16   (port 5432)
  api      → Spring Boot     (port 8080)
  pgadmin  → pgAdmin 4       (port 5050)
```

### Production (AWS EC2)

Uses `docker-compose.prod.yml`:

```
Services:
  db               → PostgreSQL 16
  api              → Spring Boot API
  nginx            → Nginx 1.27 (TLS termination, reverse proxy)
  certbot          → Let's Encrypt certificate management
  metadata-proxy   → socat proxy for EC2 IAM metadata (AWS SDK auth)
  pgadmin          → pgAdmin 4
```

**SSL Bootstrap Process** (solves the chicken-and-egg problem):

1. Start Nginx with `nginx.no-ssl.conf` (HTTP only) so Certbot can serve ACME challenges.
2. Run Certbot to obtain Let's Encrypt certificates.
3. Switch Nginx to `nginx.conf` (HTTPS) and restart.

Automation scripts (`bootstrap-ssl.sh` / `bootstrap-ssl.ps1`) handle all three steps.

**Certificate renewal** is handled by Certbot (90-day expiry, renew with `docker compose run certbot renew`).

### Nginx (Production)

- Port **80**: Redirects all HTTP to HTTPS; serves ACME challenge path (`/.well-known/acme-challenge/`).
- Port **443**: HTTPS with TLS 1.2/1.3; proxies to `http://api:8080`.
- SSL certificates mounted from the `letsencrypt_certs` Docker volume.

### AWS S3

- Bucket: `teacherplatform.503561455300`
- Region: `ap-south-1`
- Backend uses IAM role on EC2 (via metadata proxy) — no hardcoded credentials.
- Presigned URLs expire after **10 minutes**.

---

## Configuration Reference

### Backend (`application.yml`)

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/teacher_videos
    username: teacher
    password: teacher
  jpa:
    hibernate.ddl-auto: update

app:
  jwt:
    secret: <secret-key>
  storage:
    s3:
      region: ap-south-1
      bucket: teacherplatform.503561455300
      presign-expiry-minutes: 10

server:
  port: 8080
```

### Android (`AppConstants.kt`)

```kotlin
const val BASE_URL = "https://teacherplatform.duckdns.org/"
```

The app is branded as **"Singh Sir"** in `AndroidManifest.xml`.
