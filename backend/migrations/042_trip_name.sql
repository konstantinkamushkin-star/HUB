-- Migration: Trip display name
-- Version: 42
-- Date: 2026-06-19
--
-- trips uses snake_case updated_at; shared update_updated_at_column() assigns
-- NEW."updatedAt" and breaks UPDATE. Fix trigger before the UPDATE below
-- (same as 045_fix_trips_courses_updated_at_trigger.sql).

CREATE OR REPLACE FUNCTION update_updated_at_snake_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS update_trips_updated_at ON trips;
CREATE TRIGGER update_trips_updated_at
    BEFORE UPDATE ON trips
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_snake_column();

ALTER TABLE trips ADD COLUMN IF NOT EXISTS name VARCHAR(200);

UPDATE trips
SET name = COALESCE(
  NULLIF(TRIM(region), ''),
  NULLIF(TRIM(country), ''),
  LEFT(TRIM(description), 200)
)
WHERE name IS NULL OR TRIM(name) = '';

UPDATE trips
SET name = 'Trip'
WHERE name IS NULL OR TRIM(name) = '';

CREATE INDEX IF NOT EXISTS idx_trips_name ON trips (name);
