-- ============================================================================
-- 004 — Closing enrolment, and one payment covering several courses
--
-- courses.enrolment_closes_on
--   The last day a NEW student may join, Indian time. NULL means open with no
--   end date, which is every existing course. This is NOT validity_days:
--     validity_days      = how long access lasts once someone has bought.
--     enrolment_closes_on = how long the course accepts new people at all.
--   A dated batch sets the second; students already in it are untouched by it,
--   and keep exactly the access they were sold.
--
-- purchases.razorpay_payment_id
--   Was globally UNIQUE, which made one payment reference usable only once
--   across the whole system. That is wrong for offline sales: a single UPI
--   transfer can legitimately pay for two courses, and entering the same
--   reference twice failed with a duplicate-key 500. It becomes unique per
--   course instead, so the same reference may appear on different courses while
--   a genuine duplicate on one course is still refused.
--
--   Gateway payment ids stay unique in practice because Razorpay issues them
--   that way; nothing here relaxes what the gateway guarantees.
--
-- Existing rows need no backfill: enrolment_closes_on defaults to NULL, and
-- every current (course_id, razorpay_payment_id) pair is already distinct
-- because the column used to be unique on its own.
--
-- Applied automatically by scripts/run-migrations.sh. Safe to re-run.
-- ============================================================================

ALTER TABLE courses ADD COLUMN IF NOT EXISTS enrolment_closes_on DATE NULL;

ALTER TABLE purchases DROP CONSTRAINT IF EXISTS purchases_razorpay_payment_id_key;

CREATE UNIQUE INDEX IF NOT EXISTS idx_purchases_course_payment
    ON purchases(course_id, razorpay_payment_id);
