-- Partner onboarding: force password change on first app login; link dive center to owner user
ALTER TABLE users
ADD COLUMN IF NOT EXISTS "mustChangePassword" BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE dive_centers
ADD COLUMN IF NOT EXISTS owner_id UUID REFERENCES users(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_dive_centers_owner_id ON dive_centers(owner_id);
