-- ============================================================================
-- 007 — Chapters: Course → Chapter → Video
--
-- chapters
--   A course is split into chapters (Percentage, Profit & Loss, ...), each
--   holding its classes. Order is display_order within the course, written by
--   the admin panel from drag position — nobody types it.
--
-- videos.chapter_id
--   Every video belongs to exactly one chapter. videos.course_id stays: access
--   checks and the flat GET /api/courses/{id}/videos (used by older app builds)
--   still key off it, and the service keeps it equal to the chapter's course.
--
-- Backfill: each course that already has videos gets one chapter, "All
-- Classes", and its videos move into it in their current order (renumbered
-- 1..n so duplicate or gapped orders typed by hand become clean). Nothing a
-- student can see disappears.
--
-- Applied automatically by scripts/run-migrations.sh. Safe to re-run, and safe
-- after a fresh 000_consolidated.sql that already created all of this.
-- ============================================================================

CREATE TABLE IF NOT EXISTS chapters (
    id            BIGSERIAL PRIMARY KEY,
    course_id     BIGINT       NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    title         VARCHAR(200) NOT NULL,
    display_order INTEGER      NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_chapters_course_order ON chapters(course_id, display_order);

ALTER TABLE videos ADD COLUMN IF NOT EXISTS chapter_id BIGINT NULL;

INSERT INTO chapters (course_id, title, display_order)
SELECT DISTINCT v.course_id, 'All Classes', 1
FROM videos v
WHERE v.chapter_id IS NULL
  AND v.course_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM chapters c WHERE c.course_id = v.course_id);

UPDATE videos v
SET chapter_id = (
    SELECT c.id FROM chapters c
    WHERE c.course_id = v.course_id
    ORDER BY c.display_order, c.id
    LIMIT 1
)
WHERE v.chapter_id IS NULL
  AND v.course_id IS NOT NULL;

-- Renumber 1..n inside each chapter, keeping the existing relative order.
UPDATE videos v
SET display_order = r.rn
FROM (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY chapter_id ORDER BY display_order, id) AS rn
    FROM videos
    WHERE chapter_id IS NOT NULL
) r
WHERE v.id = r.id
  AND v.display_order IS DISTINCT FROM r.rn;

DO $$ BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.table_constraints
    WHERE constraint_name = 'fk_videos_chapter' AND table_name = 'videos'
  ) THEN
    ALTER TABLE videos ADD CONSTRAINT fk_videos_chapter
    FOREIGN KEY (chapter_id) REFERENCES chapters(id) ON DELETE CASCADE;
  END IF;
END $$;

-- A video with no course_id cannot be placed in a chapter; such rows were
-- already invisible to students. Leave them, and the column nullable, rather
-- than fail the deploy — but say so.
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM videos WHERE chapter_id IS NULL) THEN
    RAISE NOTICE '007_chapters: videos without a course remain without a chapter; chapter_id left nullable';
  ELSE
    ALTER TABLE videos ALTER COLUMN chapter_id SET NOT NULL;
  END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_videos_chapter_order ON videos(chapter_id, display_order);
