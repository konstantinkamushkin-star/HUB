-- In-app chat (matches iOS /api/chat/*)
CREATE TABLE IF NOT EXISTS chat_conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kind VARCHAR(30) NOT NULL,
    "canonicalKey" VARCHAR(220) UNIQUE NOT NULL,
    "diveCenterId" UUID REFERENCES dive_centers(id) ON DELETE SET NULL,
    "shopId" UUID REFERENCES shops(id) ON DELETE SET NULL,
    "bookingId" UUID,
    "createdAt" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_chat_conversations_updated ON chat_conversations("updatedAt" DESC);

-- Do not attach update_updated_at_column() here: shared function may use NEW.updated_at while this
-- table uses "updatedAt"; bump_chat_conversation_updated_at() already sets "updatedAt" on new messages.
-- See migrations/012_drop_chat_conversations_updated_at_trigger.sql for existing DBs.

CREATE TABLE IF NOT EXISTS chat_conversation_participants (
    "conversationId" UUID NOT NULL REFERENCES chat_conversations(id) ON DELETE CASCADE,
    "participantType" VARCHAR(20) NOT NULL,
    "participantId" UUID NOT NULL,
    "lastReadAt" TIMESTAMP,
    PRIMARY KEY ("conversationId", "participantType", "participantId")
);

CREATE INDEX IF NOT EXISTS idx_chat_participants_lookup
    ON chat_conversation_participants("participantType", "participantId");

CREATE TABLE IF NOT EXISTS chat_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    "conversationId" UUID NOT NULL REFERENCES chat_conversations(id) ON DELETE CASCADE,
    "senderType" VARCHAR(20) NOT NULL,
    "senderId" UUID NOT NULL,
    content TEXT NOT NULL,
    "messageType" VARCHAR(20) NOT NULL DEFAULT 'text',
    "createdAt" TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_chat_messages_conv_created
    ON chat_messages("conversationId", "createdAt");

CREATE OR REPLACE FUNCTION bump_chat_conversation_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE chat_conversations SET "updatedAt" = CURRENT_TIMESTAMP WHERE id = NEW."conversationId";
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS chat_messages_bump_conv_updated ON chat_messages;
CREATE TRIGGER chat_messages_bump_conv_updated
    AFTER INSERT ON chat_messages
    FOR EACH ROW EXECUTE FUNCTION bump_chat_conversation_updated_at();
