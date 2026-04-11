-- Social feed: posts, likes, comments (matches iOS /api/feed/*)
CREATE TABLE IF NOT EXISTS feed_posts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    "userId" UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type VARCHAR(20) NOT NULL,
    content TEXT,
    "diveLogId" UUID,
    photos JSONB NOT NULL DEFAULT '[]'::jsonb,
    "createdAt" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_feed_posts_user ON feed_posts("userId");
CREATE INDEX IF NOT EXISTS idx_feed_posts_created ON feed_posts("createdAt" DESC);

CREATE TRIGGER update_feed_posts_updated_at BEFORE UPDATE ON feed_posts
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TABLE IF NOT EXISTS feed_post_likes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    "postId" UUID NOT NULL REFERENCES feed_posts(id) ON DELETE CASCADE,
    "userId" UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    "createdAt" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT feed_post_likes_unique UNIQUE ("postId", "userId")
);

CREATE INDEX IF NOT EXISTS idx_feed_post_likes_post ON feed_post_likes("postId");
CREATE INDEX IF NOT EXISTS idx_feed_post_likes_user ON feed_post_likes("userId");

CREATE TABLE IF NOT EXISTS feed_post_comments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    "postId" UUID NOT NULL REFERENCES feed_posts(id) ON DELETE CASCADE,
    "userId" UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    "createdAt" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_feed_post_comments_post ON feed_post_comments("postId");

CREATE TRIGGER update_feed_post_comments_updated_at BEFORE UPDATE ON feed_post_comments
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
