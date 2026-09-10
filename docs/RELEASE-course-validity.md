# Release runbook — course validity

**Branch:** `claude/course-validity`
**Written:** 2026-09-10

Three commits, on top of `main` (the video player work has since been merged, so
this branch now carries only its own changes):

1. **Course validity** — optional expiry, stamped onto a purchase when it is made.
2. **The CI image build** — `DEPLOY.md` already described it; it was never committed.
3. **Automatic schema migrations** — see below.

---

## What makes this deploy ordinary

Production runs Hibernate with `ddl-auto: validate`, so the new image cannot
start until `courses.validity_days` and `purchases.expires_at` exist. That used
to mean piping a `.sql` file through `psql` by hand, in the right order, before
every schema-changing deploy — and taking the site down if you forgot.

`scripts/deploy.sh` now runs `scripts/run-migrations.sh` before starting the new
image, so **there is no manual step and no ordering to get wrong.** Merge, and
the migration and the code that needs it go out together.

If a migration fails the deploy aborts before the API is touched: the previous
image keeps serving against the previous schema, and nothing is half-applied.

---

## Step 1 — Back up the database

There is a 2 AM cron, but take a fresh dump immediately before the first
schema-changing deploy.

```bash
docker compose -f backend/docker-compose.prod.yml exec -T db pg_dump -U teacher teacher_videos | gzip > ~/pre-validity-$(date +%F-%H%M).sql.gz
```

## Step 2 — Merge and deploy

Open the PR from `claude/course-validity` to `main` and merge it. That runs the
Deploy workflow, which:

1. builds the image and pushes it to GHCR, tagged with the commit's short SHA
2. SSHes in, resets the server tree to that commit
3. applies pending migrations
4. pulls the image and restarts the API
5. reloads nginx

The static `web/` bundle and the nginx config are read from disk, so the admin
panel and the student site update with the same tree reset.

To deploy the branch without merging: **Actions → Deploy → Run workflow**, pick
the branch.

## Step 3 — Verify

```bash
# The migration was applied and recorded
docker compose -f backend/docker-compose.prod.yml exec -T db psql -U teacher -d teacher_videos -c "SELECT filename, applied_at FROM schema_migrations;"

# Nobody who already paid picked up an expiry — with_expiry must be 0
docker compose -f backend/docker-compose.prod.yml exec -T db psql -U teacher -d teacher_videos -c "SELECT count(*) AS purchases, count(expires_at) AS with_expiry FROM purchases;"

# The API came back up
docker compose -f backend/docker-compose.prod.yml logs api | grep "Started TeacherPlatformApplication"

# The public catalogue carries the new field
curl -s https://<host>/api/courses | head -c 400
```

Then in the browser:

- **Admin panel** → a **Validity** column in the course list, a **Validity (days)**
  field on the course form, expiry controls in the students modal.
- **Student site** → each course states "Lifetime access" (nothing has a validity yet).
- **An existing student** → their course still shows no expiry.

The workflow's own smoke test proves little: over HTTP nginx answers 301 and
`curl` without `-L` treats that as success, so it passes even if the API is
dead. Check the log line above yourself.

## Step 4 — Ship the APK *before* setting any validity

The backend and web tiers go live with the deploy, but the app needs a Play
Store release.

Old APKs keep working — Retrofit uses Gson, which ignores unknown JSON fields,
so `expiryDate` and friends are silently dropped by the current app. That
tolerance only matters while no course has a validity: a student on the old APK
whose access expired would see the course fail to load with no explanation.

**So: leave every course at validity 0 until the new APK is out and adopted.**
The feature is inert until the first non-zero value is typed, which makes this a
safe deploy to sit on for as long as you need.

The APK release also needs the version bump sitting uncommitted in
`android/app/build.gradle.kts` (`versionCode` 13 → 15, `versionName` 2.0.9 →
3.1.0) — deliberately not in this release, since it is a decision about the Play
Store listing rather than about the server.

## Step 5 — Rollback

```bash
scripts/deploy.sh <previous-sha>
```

About twenty seconds, no rebuild — every commit keeps its own image tag.

**Leave the columns in place.** They are nullable with defaults and older code
ignores them, so there is nothing to undo, and `schema_migrations` will not
re-apply them on the way forward again. Only restore the database dump if
something has actually corrupted data, which this migration cannot do — it only
adds columns.

---

## Known-stale documentation

[DEPLOYMENT_STATUS.md](../DEPLOYMENT_STATUS.md) still reads
"🟡 BLOCKED — Android app calls deleted `/api/sections` endpoints". That
migration has since been done: `TeacherApi.kt` has no `/api/sections` calls left,
and `VideoRepository` is course-based. Worth updating so it stops reading as a
blocker on this release.
