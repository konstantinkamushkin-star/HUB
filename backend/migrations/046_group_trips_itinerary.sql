-- Itinerary for social group trips (Explore "Add to trip")
ALTER TABLE group_trips
  ADD COLUMN IF NOT EXISTS itinerary JSONB NOT NULL DEFAULT '[]'::jsonb;
