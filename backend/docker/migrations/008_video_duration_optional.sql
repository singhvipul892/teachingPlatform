-- ============================================================================
-- 008 — videos.duration is optional
--
-- The entity has always treated duration as optional (Video.java: no
-- nullable=false) and the admin "Add class" form never sends one, but
-- 000_consolidated.sql created the column NOT NULL. Any database built from
-- that file — a fresh server, the local stack, the test stack — refused every
-- class added from the admin panel. This brings every database to what the
-- code expects. On a database where the column is already nullable it is a no-op.
--
-- Applied automatically by scripts/run-migrations.sh. Safe to re-run.
-- ============================================================================

ALTER TABLE videos ALTER COLUMN duration DROP NOT NULL;
