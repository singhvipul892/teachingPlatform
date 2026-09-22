-- ============================================================================
-- LOCAL STACK ONLY (docker-compose.local.yml). Never run against production.
--
-- Demo data so the Android app has something to show on first launch:
--   admin@local.test   / Admin@1234     role ADMIN  — for the admin panel
--   student@local.test / Student@1234   enrolled (free seat, lifetime) in
--   "SSC CGL Maths (Local demo)": 3 chapters, the last one empty on purpose
--   (students never see empty chapters).
--
-- Runs on every `up`, but does nothing unless the users table is empty — so a
-- restored production backup gets no demo accounts, and anything you add later
-- is kept. `down -v` to start over.
-- Passwords are bcrypt ($2a$) via pgcrypto, which Spring's BCryptPasswordEncoder
-- accepts.
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

DO $$
DECLARE
  v_admin   BIGINT;
  v_student BIGINT;
  v_course  BIGINT;
  v_pct     BIGINT;
  v_pnl     BIGINT;
  -- "Me at the zoo": a long-lived, embeddable public video, used as a
  -- placeholder. Replace with real classes from the admin panel.
  v_yt      CONSTANT TEXT := 'jNQXAC9IVRw';
BEGIN
  -- Only an empty database gets demo data. A restored production copy (or
  -- anything you've already signed up in) is never touched.
  IF EXISTS (SELECT 1 FROM users) THEN
    RAISE NOTICE 'Database already has users; skipping demo seed.';
    RETURN;
  END IF;

  INSERT INTO users (first_name, last_name, email, mobile_number, password_hash, role)
  VALUES ('Local', 'Admin', 'admin@local.test', '9000000001', crypt('Admin@1234', gen_salt('bf', 10)), 'ADMIN')
  RETURNING id INTO v_admin;

  INSERT INTO users (first_name, last_name, email, mobile_number, password_hash, role)
  VALUES ('Local', 'Student', 'student@local.test', '9000000002', crypt('Student@1234', gen_salt('bf', 10)), 'USER')
  RETURNING id INTO v_student;

  INSERT INTO courses (title, description, price_paise, currency, active, validity_days)
  VALUES ('SSC CGL Maths (Local demo)', 'Seeded by backend/docker/local/seed.sql', 99900, 'INR', TRUE, 0)
  RETURNING id INTO v_course;

  INSERT INTO chapters (course_id, title, display_order) VALUES (v_course, 'Percentage', 1) RETURNING id INTO v_pct;
  INSERT INTO chapters (course_id, title, display_order) VALUES (v_course, 'Profit & Loss', 2) RETURNING id INTO v_pnl;
  INSERT INTO chapters (course_id, title, display_order) VALUES (v_course, 'Simple Interest', 3);

  INSERT INTO videos (video_id, title, course_id, chapter_id, thumbnail_url, duration, display_order) VALUES
    (v_yt, 'Type 1 — Basics of percentage',   v_course, v_pct, 'https://img.youtube.com/vi/' || v_yt || '/hqdefault.jpg', '12:40', 1),
    (v_yt, 'Type 2 — Successive percentage',  v_course, v_pct, 'https://img.youtube.com/vi/' || v_yt || '/hqdefault.jpg', NULL,    2),
    (v_yt, 'Type 3 — Population problems',    v_course, v_pct, 'https://img.youtube.com/vi/' || v_yt || '/hqdefault.jpg', NULL,    3),
    (v_yt, 'Type 1 — Cost price & selling price', v_course, v_pnl, 'https://img.youtube.com/vi/' || v_yt || '/hqdefault.jpg', NULL, 1),
    (v_yt, 'Type 2 — Discount',               v_course, v_pnl, 'https://img.youtube.com/vi/' || v_yt || '/hqdefault.jpg', NULL,    2);

  -- A free seat, recorded the way an admin "tag" without payment is.
  INSERT INTO payment_orders (razorpay_order_id, user_id, course_id, amount_paise, currency, status, source)
  VALUES ('local_seed_order_' || v_student, v_student, v_course, 0, 'INR', 'PAID', 'COMPLIMENTARY');

  INSERT INTO purchases (user_id, course_id, razorpay_order_id, razorpay_payment_id, amount_paise, currency, expires_at)
  VALUES (v_student, v_course, 'local_seed_order_' || v_student, 'local_seed_payment_' || v_student, 0, 'INR', NULL);

  RAISE NOTICE 'Seeded local demo data (course id %).', v_course;
END $$;
