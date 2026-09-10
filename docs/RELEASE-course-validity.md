# Release runbook — course validity

**Branch:** `claude/course-validity` (pushed)
**Commits:** `5b4769b` course validity · `8ae88bf` CI image build
**Written:** 2026-09-10

This release carries three things, because the branch sits on top of
`claude/youtube_player`, which is 8 commits ahead of `main` and unmerged:

1. **Course validity** — optional expiry, stamped onto a purchase when it is made.
2. **The CI image build** — `DEPLOY.md` already described it; it was never committed.
3. **The video-player work** — in-app YouTube playback, Home search, PDF list.

---

## ⚠️ The one thing that must not go wrong

Production runs Hibernate with `ddl-auto: validate`. **The new image will not
start until `purchases.expires_at` and `courses.validity_days` exist.**

Merging to `main` triggers an automatic deploy. So the migration has to be
applied **before** the merge, not after. Get this backwards and the API is down
until you fix it forward.

---

## Step 1 — Back up the database

There is a 2 AM cron, but take a fresh dump immediately before a schema change.

```bash
docker compose -f backend/docker-compose.prod.yml exec -T db pg_dump -U teacher teacher_videos | gzip > ~/pre-validity-$(date +%F-%H%M).sql.gz
```

## Step 2 — Apply the migration

The file is not on the server yet — a deploy is what puts it there, and the
deploy is what needs it. Read it straight out of git instead, without touching
the working tree:

```bash
cd /opt/teacherplatform && git fetch origin claude/course-validity && git show origin/claude/course-validity:backend/docker/migrations/001_course_validity.sql | docker compose -f backend/docker-compose.prod.yml exec -T db psql -U teacher -d teacher_videos -v ON_ERROR_STOP=1
```

Expect `ALTER TABLE`, `ALTER TABLE`, `CREATE INDEX`. It is idempotent — running
it twice prints "already exists, skipping" and changes nothing.

The `db` container is not restarted by a deploy, so this can be done well ahead
of time. **The running API is unaffected**: the old code simply ignores the two
new columns.

### Confirm existing students were grandfathered

```bash
docker compose -f backend/docker-compose.prod.yml exec -T db psql -U teacher -d teacher_videos -c "SELECT count(*) AS purchases, count(expires_at) AS with_expiry FROM purchases;"
```

`with_expiry` must be **0**. Every course is `validity_days = 0` and every
existing purchase is `expires_at = NULL`, so nobody who has already paid can
lose access.

## Step 3 — Merge and deploy

Open the PR from `claude/course-validity` to `main` and merge it. That runs the
Deploy workflow: it builds the image, pushes it to GHCR tagged with the short
SHA, then SSHes in, resets the server tree to that commit and pulls the tag.

The static `web/` bundle and the nginx config are read from disk, so the admin
panel and the student site update with the same tree reset.

To deploy the branch without merging: **Actions → Deploy → Run workflow**, pick
the branch.

## Step 4 — Verify

```bash
# The API came back up
docker compose -f backend/docker-compose.prod.yml logs api | grep "Started TeacherPlatformApplication"

# The public catalogue carries the new field
curl -s https://<host>/api/courses | head -c 400
```

Then in the browser:

- **Admin panel** → a **Validity** column in the course list, a **Validity (days)**
  field on the course form, and expiry controls in the students modal.
- **Student site** → each course states "Lifetime access" (nothing has a validity yet).
- **An existing student** → their course still shows no expiry.

The workflow's own smoke test proves little: over HTTP nginx answers 301 and
`curl` without `-L` treats that as success, so it passes even if the API is
dead. Check the log line above yourself.

## Step 5 — Ship the APK *before* setting any validity

The backend and web tiers go live with the deploy, but the app needs a Play
Store release.

Old APKs keep working — Retrofit uses Gson, which ignores unknown JSON fields,
so `expiryDate` and friends are silently dropped by the current app. That
tolerance only matters while no course has a validity: a student on the old APK
whose access expires would see the course fail to load with no explanation.

**So: leave every course at validity 0 until the new APK is out and adopted.**
The feature is inert until the first non-zero value is typed, which makes this
a safe deploy to sit on.

The APK release also needs the version bump sitting uncommitted in
`android/app/build.gradle.kts` (`versionCode` 13 → 15, `versionName` 2.0.9 →
3.1.0) — deliberately not included in this release, since it is a decision about
the Play Store listing rather than about the server.

## Step 6 — Rollback

```bash
scripts/deploy.sh <previous-sha>
```

About twenty seconds, no rebuild — every commit keeps its own image tag.

**Leave the columns in place.** They are nullable with defaults and the older
code ignores them, so there is nothing to undo. Only restore the database dump
if something has actually corrupted data, which this migration cannot do — it
only adds columns.

---

## Known-stale documentation

[DEPLOYMENT_STATUS.md](../DEPLOYMENT_STATUS.md) still reads
"🟡 BLOCKED — Android app calls deleted `/api/sections` endpoints". That
migration has since been done: `TeacherApi.kt` has no `/api/sections` calls
left, and `VideoRepository` is course-based. Worth updating so it stops reading
as a blocker on this release.
