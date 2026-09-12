-- ============================================================================
-- 003 — Where a payment came from
--
-- payment_orders now says how the money arrived, so revenue can be summed from
-- what was actually paid instead of guessed from a head count:
--   RAZORPAY      : the student paid online through the gateway.
--   OFFLINE       : the student paid cash / UPI / bank transfer and an admin
--                   tagged them. Real money, recorded at the course price.
--   COMPLIMENTARY : an admin granted access without a payment — a correction,
--                   a scholarship, a free seat. Always amount_paise = 0.
--
-- Backfill: every existing ADMIN-ORDER-% row was an admin tag, so it becomes
-- OFFLINE; everything else came from the gateway and becomes RAZORPAY.
--
-- NOTE ON HISTORIC AMOUNTS — read before trusting the first revenue figure.
-- Admin tags written before this release recorded amount_paise = 0, because the
-- amount was hardcoded rather than taken from the course. Their source is
-- corrected here but their AMOUNTS ARE NOT, because the real figure is not
-- knowable from the data — a student may have paid full price, a discount, or
-- nothing at all. So revenue will read LOWER than the old dashboard showed,
-- which counted every one of them at full list price.
--
-- To adopt the old dashboard's assumption instead (every historic offline tag
-- was a full-price sale), run this once, by hand, having decided it is true:
--
--   UPDATE payment_orders o SET amount_paise = c.price_paise
--     FROM courses c
--    WHERE c.id = o.course_id
--      AND o.source = 'OFFLINE'
--      AND o.amount_paise = 0;
--
-- It is deliberately not run here: this migration will not invent money. Decided
-- on 2026-09-12 NOT to run it — the past is left as it stands, and the dashboard
-- shows these enrolments as a separate count so the total reads as incomplete
-- rather than wrong.
--
-- Applied automatically by scripts/run-migrations.sh. Safe to re-run.
-- ============================================================================

ALTER TABLE payment_orders
    ADD COLUMN IF NOT EXISTS source VARCHAR(20) NOT NULL DEFAULT 'RAZORPAY';

UPDATE payment_orders
   SET source = 'OFFLINE'
 WHERE razorpay_order_id LIKE 'ADMIN-ORDER-%'
   AND source = 'RAZORPAY';

-- Revenue groups by source over paid orders.
CREATE INDEX IF NOT EXISTS idx_payment_orders_status_source ON payment_orders(status, source);
