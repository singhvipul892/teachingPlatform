-- ============================================================================
-- 006 — Password reset by email, with a wrong-guess limit
--
-- The reset OTP now goes by email instead of SMS, and OTPs are looked up by the
-- user they belong to rather than by mobile number (an email does not fit in
-- mobile_number's 20 characters). mobile_number is left in place and still
-- written, so the previous image keeps working if we roll back.
--
-- password_reset_otps.attempts counts wrong guesses against one OTP. After five
-- the OTP is burned and a new one must be requested, so a 6-digit code cannot
-- be brute-forced inside its 10-minute window.
--
-- Applied automatically by scripts/run-migrations.sh. Safe to re-run.
-- ============================================================================

ALTER TABLE password_reset_otps ADD COLUMN IF NOT EXISTS attempts INTEGER NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_prt_user ON password_reset_otps(user_id);
