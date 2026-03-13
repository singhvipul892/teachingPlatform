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
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_payment_orders_user_id           ON payment_orders(user_id);
CREATE INDEX IF NOT EXISTS idx_payment_orders_razorpay_order_id ON payment_orders(razorpay_order_id);
CREATE INDEX IF NOT EXISTS idx_payment_orders_course_id         ON payment_orders(course_id);

CREATE TABLE IF NOT EXISTS purchases (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT       NOT NULL REFERENCES users(id),
    course_id           BIGINT       NOT NULL REFERENCES courses(id),
    razorpay_order_id   VARCHAR(100) NOT NULL UNIQUE REFERENCES payment_orders(razorpay_order_id),
    razorpay_payment_id VARCHAR(100) NOT NULL UNIQUE,
    amount_paise        INTEGER      NOT NULL,
    currency            VARCHAR(10)  NOT NULL DEFAULT 'INR',
    purchased_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_purchases_user_id    ON purchases(user_id);
CREATE INDEX IF NOT EXISTS idx_purchases_course_id  ON purchases(course_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_purchases_user_course ON purchases(user_id, course_id);

-- ============================================================================
-- 5. Done - All tables created with proper dependencies
-- ============================================================================
-- To set an admin user after first signup, run:
-- UPDATE users SET role = 'ADMIN' WHERE email = 'your-email@example.com';
