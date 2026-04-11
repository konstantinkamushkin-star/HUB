-- Friend requests and accepted friendships (matches iOS /api/friends/*)
CREATE TABLE IF NOT EXISTS friendships (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    "requesterId" UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    "addresseeId" UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    "createdAt" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT friendships_requester_addressee_unique UNIQUE ("requesterId", "addresseeId"),
    CONSTRAINT friendships_no_self CHECK ("requesterId" <> "addresseeId")
);

CREATE INDEX IF NOT EXISTS idx_friendships_requester ON friendships("requesterId");
CREATE INDEX IF NOT EXISTS idx_friendships_addressee ON friendships("addresseeId");
CREATE INDEX IF NOT EXISTS idx_friendships_status ON friendships(status);

CREATE TRIGGER update_friendships_updated_at BEFORE UPDATE ON friendships
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
