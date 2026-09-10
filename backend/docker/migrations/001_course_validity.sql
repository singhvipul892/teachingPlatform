-- ============================================================================
-- 001 — Course validity
--
-- Adds an optional expiry to course access.
--   courses.validity_days : 0 = lifetime access (the default, and what every
--                           existing course keeps). Any other value is the
--                           number of days a NEW purchase stays valid for.
--   purchases.expires_at  : NULL = never expires. Stamped at purchase time from
--                           the course's validity as it stood that day, and
--                           never recalculated afterwards.
--
-- Existing rows need no backfill: courses default to 0 and purchases to NULL,
-- so every student who has already bought keeps permanent access.
--
-- Applied automatically by scripts/run-migrations.sh, which scripts/deploy.sh
-- runs before starting the new API image. Nothing to do by hand.
--
-- Safe to re-run: every statement is IF NOT EXISTS, so a database that already
-- has these columns is simply recorded as migrated.
-- ============================================================================

ALTER TABLE courses   ADD COLUMN IF NOT EXISTS validity_days INTEGER NOT NULL DEFAULT 0;
ALTER TABLE purchases ADD COLUMN IF NOT EXISTS expires_at    TIMESTAMPTZ NULL;

-- Access checks filter on (user_id, course_id) and then on expiry; the existing
-- unique index on (user_id, course_id) already covers the lookup.
CREATE INDEX IF NOT EXISTS idx_purchases_expires_at ON purchases(expires_at);
