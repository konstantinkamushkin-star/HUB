-- Feed posts: attach processed underwater videos
ALTER TABLE feed_posts
    ADD COLUMN IF NOT EXISTS videos JSONB NOT NULL DEFAULT '[]'::jsonb;
