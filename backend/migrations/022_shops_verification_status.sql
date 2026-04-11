-- Partner onboarding: shops participate in the same verification workflow as dive centers.
ALTER TABLE shops
ADD COLUMN IF NOT EXISTS verification_status VARCHAR(32) NOT NULL DEFAULT 'unverified';

CREATE INDEX IF NOT EXISTS idx_shops_verification_status ON shops(verification_status);
