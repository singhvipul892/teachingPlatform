-- ============================================================================
-- 005 — The reference a human recognises
--
-- payment_orders.reference holds the identifier a person would actually quote:
-- the UPI/bank reference an admin typed while tagging, or Razorpay's payment id
-- for an online sale. Until now the ledger only had razorpay_order_id, which for
-- an offline sale is a synthetic "ADMIN-ORDER-<user>-<course>-<millis>" string
-- that means nothing to anybody — so the payment history had nothing useful to
-- show, and a duplicate entry could not be recognised as one.
--
-- NULL is allowed: rows written before this, and complimentary seats, have no
-- reference to record.
--
-- Backfill: offline rows take the reference from the purchase they paid for,
-- where the admin's transaction id has been stored all along. Online rows take
-- the gateway payment id from the same place.
--
-- Applied automatically by scripts/run-migrations.sh. Safe to re-run.
-- ============================================================================

ALTER TABLE payment_orders ADD COLUMN IF NOT EXISTS reference VARCHAR(100) NULL;

UPDATE payment_orders o
   SET reference = p.razorpay_payment_id
  FROM purchases p
 WHERE p.razorpay_order_id = o.razorpay_order_id
   AND o.reference IS NULL;

-- Spotting a reference entered twice for the same course.
CREATE INDEX IF NOT EXISTS idx_payment_orders_course_reference
    ON payment_orders(course_id, reference);
