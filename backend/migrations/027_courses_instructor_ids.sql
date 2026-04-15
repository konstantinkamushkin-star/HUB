-- Multiple instructors per course (user IDs from dive_centers.instructor_ids).
-- Keeps instructor_id as legacy "primary" (first in list) for older clients.

ALTER TABLE courses
    ADD COLUMN IF NOT EXISTS instructor_ids UUID[] NOT NULL DEFAULT '{}';

CREATE INDEX IF NOT EXISTS idx_courses_instructor_ids ON courses USING GIN (instructor_ids);

UPDATE courses
SET instructor_ids = ARRAY[instructor_id]::uuid[]
WHERE instructor_id IS NOT NULL
  AND instructor_ids = '{}';
