-- Client-computed achievement unlocks synced across devices
CREATE TABLE IF NOT EXISTS user_achievements (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    achievement_id TEXT NOT NULL,
    unlocked_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, achievement_id)
);

CREATE INDEX IF NOT EXISTS idx_user_achievements_user_id ON user_achievements (user_id);

-- Fields needed for auto achievements (buddy / centers / countries)
ALTER TABLE dive_logs
    ADD COLUMN IF NOT EXISTS "diveCenterId" UUID NULL,
    ADD COLUMN IF NOT EXISTS buddy VARCHAR(255) NULL,
    ADD COLUMN IF NOT EXISTS country VARCHAR(128) NULL;

CREATE INDEX IF NOT EXISTS idx_dive_logs_dive_center ON dive_logs ("diveCenterId");
