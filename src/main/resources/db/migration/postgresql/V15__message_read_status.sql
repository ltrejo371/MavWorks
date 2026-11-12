ALTER TABLE messages ADD COLUMN read_at TIMESTAMP WITH TIME ZONE;
-- Existing history predates notifications; notify only for new incoming messages.
UPDATE messages SET read_at = sent_at;
CREATE INDEX idx_messages_unread ON messages (read_at, conversation_id, sender_id, id);
