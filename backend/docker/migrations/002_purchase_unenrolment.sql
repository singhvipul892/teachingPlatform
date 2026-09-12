-- ============================================================================
-- 002 — Untagging keeps the enrolment row
--
-- Removing a student from a course used to DELETE the purchases row, which
-- destroyed the record that they were ever enrolled. Untagging now stamps
--   purchases.unenrolled_at : NULL = currently enrolled (every existing row).
--                             A timestamp = the moment an admin removed them.
--
-- The row stays, so re-tagging the same student reuses it — which is what the
-- unique index on (user_id, course_id) requires — and the payment history for
-- how many times they actually paid lives in payment_orders, one row per
-- payment.
--
-- Existing rows need no backfill: NULL means enrolled, so everyone who has
-- access today keeps it.
--
-- Applied automatically by scripts/run-migrations.sh, which scripts/deploy.sh
-- runs before starting the new API image. Nothing to do by hand.
--
-- Safe to re-run: every statement is IF NOT EXISTS.
-- ============================================================================

ALTER TABLE purchases ADD COLUMN IF NOT EXISTS unenrolled_at TIMESTAMPTZ NULL;

-- Every access check now filters on "still enrolled", so the lookups that used
-- to end at (user_id, course_id) carry this column too.
CREATE INDEX IF NOT EXISTS idx_purchases_unenrolled_at ON purchases(unenrolled_at);
