-- ============================================================================
-- Consolidated Database Schema for Singh Sir Teaching Platform
-- This file creates all required tables and indexes in a single script
-- ============================================================================

-- ============================================================================
-- 1. VIDEOS & PDFS (Independent tables, no FK dependencies)
-- ============================================================================

CREATE TABLE IF NOT EXISTS videos (
    id           BIGSERIAL PRIMARY KEY,
    video_id     VARCHAR(64)  NOT NULL,
    title        VARCHAR(200) NOT NULL,
    course_id    BIGINT       NULL,        -- FK to courses (set after courses table exists)
    thumbnail_url VARCHAR(500) NOT NULL,
    duration     VARCHAR(20)  NOT NULL,
    display_order INTEGER      NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_videos_course_id    ON videos (course_id);
CREATE INDEX IF NOT EXISTS idx_videos_course_order ON videos (course_id, display_order);

CREATE TABLE IF NOT EXISTS video_pdfs (
    id           BIGSERIAL PRIMARY KEY,
    video_id_fk  BIGINT       NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
    title        VARCHAR(200) NOT NULL,
    pdf_type     VARCHAR(100) NOT NULL,
    file_url     VARCHAR(500) NOT NULL,
    display_order INTEGER     NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_video_pdfs_video_id ON video_pdfs(video_id_fk);

-- ============================================================================
-- 2. USERS (With RBAC support - role column)
-- ============================================================================

CREATE TABLE IF NOT EXISTS users (
    id            BIGSERIAL PRIMARY KEY,
    first_name    VARCHAR(100) NOT NULL,
    last_name     VARCHAR(100) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    mobile_number VARCHAR(20)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'ADMIN', 'TEACHER')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT uq_users_mobile UNIQUE (mobile_number)
);

CREATE INDEX IF NOT EXISTS idx_users_email ON users (email);
CREATE INDEX IF NOT EXISTS idx_users_mobile ON users (mobile_number);
CREATE INDEX IF NOT EXISTS idx_users_role ON users(role);

-- ============================================================================
-- 3. COURSES (Then create FK from videos to courses)
-- ============================================================================

CREATE TABLE IF NOT EXISTS courses (
    id            BIGSERIAL PRIMARY KEY,
    title         VARCHAR(200) NOT NULL,
    description   TEXT         NOT NULL DEFAULT '',
    price_paise   INTEGER      NOT NULL,
    currency      VARCHAR(10)  NOT NULL DEFAULT 'INR',
    thumbnail_url VARCHAR(500) NOT NULL DEFAULT '',
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    -- 0 = lifetime access. Otherwise the number of days a new purchase lasts.
    validity_days INTEGER      NOT NULL DEFAULT 0,
    -- Last day a NEW student may join, Indian time. NULL = open indefinitely.
    -- Not validity_days: that is how long access lasts once bought.
    enrolment_closes_on DATE   NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_courses_active ON courses(active);

-- Add FK constraint from videos to courses (if not already present)
DO $$ BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.table_constraints
    WHERE constraint_name = 'fk_videos_course' AND table_name = 'videos'
  ) THEN
    ALTER TABLE videos ADD CONSTRAINT fk_videos_course
    FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE;
  END IF;
END $$;

-- ============================================================================
-- 4. PAYMENTS & PURCHASES
-- ============================================================================

CREATE TABLE IF NOT EXISTS payment_orders (
    id                BIGSERIAL PRIMARY KEY,
    razorpay_order_id VARCHAR(100) NOT NULL UNIQUE,
    user_id           BIGINT       NOT NULL REFERENCES users(id),
    course_id         BIGINT       NOT NULL REFERENCES courses(id),
    amount_paise      INTEGER      NOT NULL,
    currency          VARCHAR(10)  NOT NULL DEFAULT 'INR',
    status            VARCHAR(20)  NOT NULL DEFAULT 'CREATED',
    -- RAZORPAY paid online · OFFLINE paid in cash/UPI and tagged by an admin ·
    -- COMPLIMENTARY granted with no payment (always amount_paise = 0).
    source            VARCHAR(20)  NOT NULL DEFAULT 'RAZORPAY',
    -- What a person would quote: the UPI/bank reference an admin typed, or the
    -- gateway payment id. NULL for free seats and for rows predating this.
    reference         VARCHAR(100) NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_payment_orders_user_id           ON payment_orders(user_id);
CREATE INDEX IF NOT EXISTS idx_payment_orders_razorpay_order_id ON payment_orders(razorpay_order_id);
CREATE INDEX IF NOT EXISTS idx_payment_orders_course_id         ON payment_orders(course_id);
CREATE INDEX IF NOT EXISTS idx_payment_orders_status_source   ON payment_orders(status, source);
CREATE INDEX IF NOT EXISTS idx_payment_orders_course_reference ON payment_orders(course_id, reference);

CREATE TABLE IF NOT EXISTS purchases (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT       NOT NULL REFERENCES users(id),
    course_id           BIGINT       NOT NULL REFERENCES courses(id),
    razorpay_order_id   VARCHAR(100) NOT NULL UNIQUE REFERENCES payment_orders(razorpay_order_id),
    -- Unique per course, not globally: one offline payment reference can pay
    -- for two courses. See idx_purchases_course_payment below.
    razorpay_payment_id VARCHAR(100) NOT NULL,
    amount_paise        INTEGER      NOT NULL,
    currency            VARCHAR(10)  NOT NULL DEFAULT 'INR',
    purchased_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    -- NULL = access never expires. Stamped at purchase, never recalculated.
    expires_at          TIMESTAMPTZ  NULL,
    -- NULL = still enrolled. A timestamp is when an admin removed the student;
    -- the row is kept so the enrolment is never destroyed, and re-tagging reuses it.
    unenrolled_at       TIMESTAMPTZ  NULL
);

CREATE INDEX IF NOT EXISTS idx_purchases_user_id    ON purchases(user_id);
CREATE INDEX IF NOT EXISTS idx_purchases_course_id  ON purchases(course_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_purchases_user_course ON purchases(user_id, course_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_purchases_course_payment ON purchases(course_id, razorpay_payment_id);
CREATE INDEX IF NOT EXISTS idx_purchases_expires_at ON purchases(expires_at);
CREATE INDEX IF NOT EXISTS idx_purchases_unenrolled_at ON purchases(unenrolled_at);

-- ============================================================================
-- 5. Done - All tables created with proper dependencies
-- ============================================================================
-- To set an admin user after first signup, run:
-- UPDATE users SET role = 'ADMIN' WHERE email = 'your-email@example.com';

-- ============================================================================
-- 6. PASSWORD RESET OTPs (Added for forgot password feature)
-- ============================================================================
CREATE TABLE IF NOT EXISTS password_reset_otps (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    mobile_number   VARCHAR(20)  NOT NULL,
    otp_hash        VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ  NOT NULL,
    used            BOOLEAN      NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_prt_mobile ON password_reset_otps(mobile_number);
