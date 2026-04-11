-- Social reviews: reviews for dive sites, centers, instructors, shops
CREATE TABLE IF NOT EXISTS reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    "userId" UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    "reviewableType" VARCHAR(50) NOT NULL,
    "reviewableId" UUID NOT NULL,
    rating INT NOT NULL,
    text TEXT NOT NULL,
    language VARCHAR(10) NOT NULL DEFAULT 'en',
    categories JSONB,
    "createdAt" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_reviews_user ON reviews("userId");
CREATE INDEX IF NOT EXISTS idx_reviews_reviewable ON reviews("reviewableType", "reviewableId");

CREATE TRIGGER update_reviews_updated_at BEFORE UPDATE ON reviews
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

