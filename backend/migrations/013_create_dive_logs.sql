CREATE TABLE IF NOT EXISTS dive_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    "userId" UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    "diveSiteId" UUID NULL,
    date DATE NOT NULL,
    "startTime" TIMESTAMPTZ NULL,
    "endTime" TIMESTAMPTZ NULL,
    duration INT NOT NULL,
    "maxDepth" DOUBLE PRECISION NOT NULL,
    "averageDepth" DOUBLE PRECISION NULL,
    "waterTemperature" DOUBLE PRECISION NULL,
    visibility DOUBLE PRECISION NULL,
    current VARCHAR(64) NULL,
    "diveType" VARCHAR(64) NULL,
    notes TEXT NULL,
    "photoUrls" JSONB NOT NULL DEFAULT '[]'::jsonb,
    "videoUrls" JSONB NOT NULL DEFAULT '[]'::jsonb,
    "fishSpecies" JSONB NOT NULL DEFAULT '[]'::jsonb,
    "isPublished" BOOLEAN NULL,
    "createdAt" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_dive_logs_user ON dive_logs("userId");
CREATE INDEX IF NOT EXISTS idx_dive_logs_date ON dive_logs(date DESC);

DROP TRIGGER IF EXISTS update_dive_logs_updated_at ON dive_logs;
CREATE TRIGGER update_dive_logs_updated_at BEFORE UPDATE ON dive_logs
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
