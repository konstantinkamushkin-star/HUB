-- Migration: Trip display name
-- Version: 42
-- Date: 2026-06-19

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
