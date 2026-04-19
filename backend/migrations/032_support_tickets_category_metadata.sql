-- App user tickets: category, client metadata, optional chat link
ALTER TABLE admin_support_tickets
  ADD COLUMN IF NOT EXISTS category VARCHAR(32) NOT NULL DEFAULT 'other',
  ADD COLUMN IF NOT EXISTS metadata JSONB,
  ADD COLUMN IF NOT EXISTS conversation_id UUID;

CREATE INDEX IF NOT EXISTS idx_admin_support_tickets_category ON admin_support_tickets (category);
