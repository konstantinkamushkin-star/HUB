-- Per-user underwater processing gallery (Dive Editor).
CREATE TABLE IF NOT EXISTS processed_media (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    "userId" UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    "clientId" VARCHAR(64) NOT NULL,
    kind VARCHAR(16) NOT NULL,
    source VARCHAR(16) NOT NULL,
    engine VARCHAR(32),
    "mediaPath" VARCHAR(512) NOT NULL,
    "thumbnailPath" VARCHAR(512),
    "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT processed_media_user_client UNIQUE ("userId", "clientId")
);

CREATE INDEX IF NOT EXISTS idx_processed_media_user_created
    ON processed_media ("userId", "createdAt" DESC);

CREATE INDEX IF NOT EXISTS idx_processed_media_source
    ON processed_media (source);
