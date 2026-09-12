# Deployment Status

**Date:** 2026-09-10
**Status:** 🟢 Backend, admin panel and student site live and current with `main`.
Android APK is the only tier behind.

> Previous versions of this file (dated 2026-03-26) said deployment was BLOCKED on
> an Android migration to course-based endpoints. **That migration is done** —
> `TeacherApi.kt` calls only `api/courses/{courseId}/videos` and `api/user/courses`,
> and `VideoRepository` is course-based. The block no longer exists. `DRY_RUN_AUDIT.md`
> from the same date is historical for the same reason.

---

## Where each tier stands

| Tier | State | Notes |
|---|---|---|
| **Backend** | 🟢 Live, current | Deployed from `main` via GitHub Actions → GHCR |
| **Admin panel** (`web/admin/`) | 🟢 Live, current | Served by nginx from the repo bind mount |
| **Student site** (`web/student/`) | 🟢 Live, current | Same |
| **Database** | 🟢 Migrated | `001_course_validity.sql` applied automatically during deploy |
| **Android APK** | 🟡 Code current, **not released** | Play Store release outstanding — see below |
| **HTTPS** | 🔴 Not set up | Port 443 refuses connections; see below |

## Verified live

```
$ curl -s http://13.205.19.207/api/courses
[{"id":2,"title":"DAILY PRACTICE BATCH 1.0 (DPB 1.0)",...,"validityDays":0,
  "expiryDate":null,"expired":false,"daysRemaining":null}, ...]
```

Course validity is deployed and every course reads `validityDays: 0` — lifetime
access, unchanged for everyone who has already bought. `001_course_validity.sql`
was applied automatically by the deploy; confirm with:

```bash
docker compose -f backend/docker-compose.prod.yml exec -T db psql -U teacher -d teacher_videos -c "SELECT filename, applied_at FROM schema_migrations;" -c "SELECT count(*) AS purchases, count(expires_at) AS with_expiry FROM purchases;"
```

`with_expiry` stays at 0 until a course is deliberately given a validity.

---

## How deployment works now

Push to `main` (or run the workflow on a branch). GitHub Actions builds the API
image and pushes it to GHCR; the server pulls it. **The server compiles nothing**,
and **schema migrations are applied automatically** before the new image starts.

Full detail in [DEPLOY.md](DEPLOY.md). Two sections worth knowing:

- [Deploying a Change](DEPLOY.md#deploying-a-change) — the standing procedure, including
  what differs for database, `web/` and Android changes.
- [Schema migrations](DEPLOY.md#schema-migrations) — how to add one so it ships with its code.

Rollback is `scripts/deploy.sh <old-sha>` — about twenty seconds, no rebuild.

---

## Outstanding

### 1. Android APK release (🟡)

The app code is current, including the course-validity UI, but students are still
running the old build. Needs the version bump currently uncommitted in
`android/app/build.gradle.kts` (`versionCode` 13 → 15, `versionName` 2.0.9 → 3.1.0),
then a Play Store release.

**Until that ships, leave every course at validity 0.** Old APKs ignore the new
fields harmlessly, but a student on the old app whose access expired would just
see the course fail to load.

### 2. HTTPS is not configured (🔴)

Port 443 refuses connections. `nginx/nginx.conf` has a complete SSL server block
ready, but certbot has never been run, so the server still serves
`nginx/nginx.no-ssl.conf` over plain HTTP. The Android app talks to
`http://13.205.19.207:8080` directly.

This matters beyond the padlock: taking Razorpay payments over plain HTTP on a
bare IP is a poor look for a paid product, and Let's Encrypt will not issue a
certificate for an IP address — HTTPS needs a real domain first.
See `DEPLOYMENT_PLAN.md` for the wider argument.

### 3. Deferred product work

[BACKLOG.md](BACKLOG.md): out-of-app expiry reminders (B1), the ungated PDF
download endpoint (B2), revenue figures drifting once renewals exist (B3).

---

## Configuration notes

- `web/*/index.html` all use `const API_BASE = ''` (relative URLs), which is the
  correct production setting — they call whatever host served the page.
- Static `web/` files are a **live bind mount**, so a deploy's `git reset` updates
  the site with no image rebuild.
- Only the `api` container restarts on deploy. The `db` container is never
  touched, so there is no database downtime.
