-- User location sharing for friends / discover, and app privacy flags

ALTER TABLE users
  ADD COLUMN IF NOT EXISTS share_location BOOLEAN NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS show_in_friend_search BOOLEAN NOT NULL DEFAULT true,
  ADD COLUMN IF NOT EXISTS public_profile BOOLEAN NOT NULL DEFAULT false;

CREATE TABLE IF NOT EXISTS user_locations (
  "userId" UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  latitude DOUBLE PRECISION NOT NULL,
  longitude DOUBLE PRECISION NOT NULL,
  "accuracyMeters" DOUBLE PRECISION,
  source VARCHAR(20) NOT NULL DEFAULT 'last_known',
  "updatedAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_user_locations_updated_at ON user_locations("updatedAt");
