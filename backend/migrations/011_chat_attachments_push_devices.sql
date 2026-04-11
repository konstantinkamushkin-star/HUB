ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS attachments JSONB;

CREATE TABLE IF NOT EXISTS user_push_devices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    "userId" UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token TEXT NOT NULL,
    platform VARCHAR(20) NOT NULL DEFAULT 'ios',
    "createdAt" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT user_push_devices_user_token UNIQUE ("userId", token)
);

CREATE INDEX IF NOT EXISTS idx_user_push_devices_user ON user_push_devices("userId");

DROP TRIGGER IF EXISTS update_user_push_devices_updated_at ON user_push_devices;
CREATE TRIGGER update_user_push_devices_updated_at BEFORE UPDATE ON user_push_devices
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
