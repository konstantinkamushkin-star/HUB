-- Sending a chat message runs bump_chat_conversation_updated_at(), which UPDATEs chat_conversations."updatedAt".
-- BEFORE UPDATE trigger update_chat_conversations_updated_at invokes update_updated_at_column().
-- If that function still assigns NEW.updated_at (snake_case) while this table only has "updatedAt", PostgreSQL errors:
--   record "new" has no field "updated_at"
-- The bump trigger already maintains "updatedAt"; this BEFORE UPDATE is redundant for chat.
DROP TRIGGER IF EXISTS update_chat_conversations_updated_at ON chat_conversations;
