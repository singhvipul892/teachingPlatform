# Running everything locally

Run the Android app against a backend and database on your own machine instead of production.

```
Android app ("local" variant) ──► API :8080 ──► Postgres :5433
Admin panel  :8081 ─────────────────┘
```

Everything runs from `docker-compose.local.yml`. The API image is built from **your working
tree**, so backend changes you haven't committed are included. Gradle doesn't have to work on
the host because the build runs inside Docker.

## 1. Start the backend

Start Docker Desktop, then from the repo root:

```bash
docker compose -f docker-compose.local.yml up -d --build
```

The first build takes about 3 minutes; later builds take seconds. It's ready when this
shows `Started ...Application`:

```bash
docker compose -f docker-compose.local.yml logs -f api
```

| What | Where |
|---|---|
| API | http://localhost:8080 (from the emulator: http://10.0.2.2:8080) |
| Admin panel | http://localhost:8081/web/admin/index.html |
| Postgres | `localhost:5433`, user `teacher`, password `teacher`, db `teacher_videos` |

**Seeded accounts** (`backend/docker/local/seed.sql`, only on an empty database — a restored
production backup keeps its real users and gets none of these):

| Login | Password | Use |
|---|---|---|
| `admin@local.test` | `Admin@1234` | admin panel |
| `student@local.test` | `Student@1234` | Android app, already enrolled in the demo course |

The demo course has 3 chapters. The last one is empty on purpose, because students never see
empty chapters. Add your own courses, chapters and classes in the admin panel.

## 2. Run the Android app

1. In Android Studio, open **Build ▸ Select Build Variant** and set `app` to **`local`**.
2. Run the app on an **emulator**. It calls `http://10.0.2.2:8080/`, which the emulator
   routes to your PC.
3. Log in as `student@local.test` / `Student@1234`.

The `local` variant installs as **"Singh Sir (Local)"** (`com.maths.teacher.app.local`),
next to the normal app. The `debug` and `release` variants still point at production.

**Which variant to pick** (Android Studio → Build Variants):

| Variant | Talks to | Use it for |
|---|---|---|
| `debug` | production API | trying an app-only change (UI, player) on a phone with real data |
| `local` | Docker on this PC (needs `docker compose ... up` first) | backend changes, or anything that writes data |
| `release` | production API | Play Store builds |

### On a physical phone

Pick **one** of these, then rebuild the `local` variant:

- **USB cable (simplest).** Add this line to `android/local.properties`:
  ```
  local.api.url=http://localhost:8080/
  ```
  Building the `local` variant (including Android Studio's Run) runs
  `adb reverse tcp:8080 tcp:8080` for every connected device. If you plug the phone in
  *after* installing, press Run again, or run that command yourself. If you skip it, the app
  shows "Failed to connect to localhost/127.0.0.1:8080".
- **Same Wi-Fi.** Find your PC's IP with `ipconfig` (for example 192.168.1.20) and add this
  line to `android/local.properties`:
  ```
  local.api.url=http://192.168.1.20:8080/
  ```
  If the phone can't connect, Windows Firewall is probably blocking port 8080. Allow it for
  private networks.

`local.properties` is not committed, so this setting is yours alone.

## Everyday commands

```bash
docker compose -f docker-compose.local.yml up -d --build   # rebuild the API after backend changes
docker compose -f docker-compose.local.yml logs -f api     # API logs (OTP codes are printed here)
docker compose -f docker-compose.local.yml down            # stop and keep the data
docker compose -f docker-compose.local.yml down -v         # stop and wipe it; reseeded on next up
```

**New migrations.** Every `up` runs the `migrate` service. It applies any
`backend/docker/migrations/*.sql` file not yet recorded in `schema_migrations`, the same way the
production deploy does. Pulling a branch with a new migration therefore only needs another `up`.
The API boots with `ddl-auto: validate`, as production does, so a migration that doesn't match
the entities stops the API here, before it can break a deploy.

**Web pages without rebuilding.** The `web/` folder is mounted live, so reloading the browser is
enough after editing the admin panel.

## What doesn't work locally

- **PDF uploads and course thumbnails.** These go to S3, and the local stack has no S3, so those
  uploads fail. Everything else works: courses without a thumbnail, chapters, classes, drag to
  reorder, enrolling students, and the whole app.
- **Real SMS or email.** OTPs for "forgot password" are only written to the API log
  (`logs -f api`).
- **Real payments.** Razorpay uses test keys. Enrol students from the admin panel instead.

## Safety

The local stack is kept separate from everything real:

- It uses its own compose project (`singhsir-local`) and its own volume (`singhsir_local_db`).
  It never uses the `teacher_db` volume from `docker-compose.yml`.
- Its database runs on port 5433. The test stack uses 55432, and `docker-compose.yml` uses 5432.
- The seed script runs only in this stack, and only on an empty database (no users). A restored
  production backup gets no demo accounts.
