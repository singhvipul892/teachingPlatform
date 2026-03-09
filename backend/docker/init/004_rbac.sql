-- Database Migration: Add RBAC (Role-Based Access Control) support
-- Date: 2026-03-09

-- Add role column to users table
ALTER TABLE users
ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER'
CHECK (role IN ('USER', 'ADMIN', 'TEACHER'));

-- Create index for faster role-based queries
CREATE INDEX idx_users_role ON users(role);

-- Add foreign key constraint from videos to courses (if not already present)
-- This ensures that when a course is deleted, associated videos are also deleted
DO $$ BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.table_constraints
    WHERE constraint_name = 'fk_videos_course' AND table_name = 'videos'
  ) THEN
    ALTER TABLE videos
    ADD CONSTRAINT fk_videos_course
    FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE;
  END IF;
END $$;

-- Ensure courses table has active status index
CREATE INDEX IF NOT EXISTS idx_courses_active ON courses(active);

-- Note: To set the first admin user, run this manually after deployment:
-- UPDATE users SET role = 'ADMIN' WHERE id = 1;  -- Replace 1 with the teacher's actual user ID
