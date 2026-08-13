-- Buddy find: place + time intents (NOT tied to commercial trips)
CREATE TABLE IF NOT EXISTS buddy_searches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    "userId" UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    place VARCHAR(200) NOT NULL,
    "dateFrom" DATE NOT NULL,
    "dateTo" DATE NOT NULL,
    "certificationLevel" VARCHAR(64) NULL,
    "diveCount" INTEGER NULL,
    languages JSONB NOT NULL DEFAULT '[]'::jsonb,
    interests JSONB NOT NULL DEFAULT '[]'::jsonb,
    status VARCHAR(20) NOT NULL DEFAULT 'open',
    "createdAt" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT buddy_searches_status_check CHECK (status IN ('open', 'closed')),
    CONSTRAINT buddy_searches_dates_check CHECK ("dateTo" >= "dateFrom")
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_buddy_searches_one_open_per_user
    ON buddy_searches ("userId")
    WHERE status = 'open';

CREATE INDEX IF NOT EXISTS idx_buddy_searches_status ON buddy_searches(status);
CREATE INDEX IF NOT EXISTS idx_buddy_searches_place ON buddy_searches(place);
CREATE INDEX IF NOT EXISTS idx_buddy_searches_dates ON buddy_searches("dateFrom", "dateTo");

DROP TRIGGER IF EXISTS update_buddy_searches_updated_at ON buddy_searches;
CREATE TRIGGER update_buddy_searches_updated_at BEFORE UPDATE ON buddy_searches
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
