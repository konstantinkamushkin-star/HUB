-- Reports & moderation foundation

ALTER TABLE feed_post_comments
  ADD COLUMN IF NOT EXISTS "moderationStatus" VARCHAR(32) DEFAULT 'published',
  ADD COLUMN IF NOT EXISTS "deletedAt" TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_feed_post_comments_moderation_status
  ON feed_post_comments("moderationStatus");

CREATE TABLE IF NOT EXISTS admin_reports (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  "targetType" VARCHAR(64) NOT NULL,
  "targetId" VARCHAR(128) NOT NULL,
  "reporterUserId" UUID,
  "reasonCode" VARCHAR(128),
  message TEXT,
  status VARCHAR(32) DEFAULT 'new',
  priority VARCHAR(16) DEFAULT 'normal',
  "handledByAdminId" UUID,
  resolution TEXT,
  history JSONB,
  "createdAt" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  "updatedAt" TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_admin_reports_status ON admin_reports(status);
CREATE INDEX IF NOT EXISTS idx_admin_reports_priority ON admin_reports(priority);
CREATE INDEX IF NOT EXISTS idx_admin_reports_target ON admin_reports("targetType", "targetId");
CREATE INDEX IF NOT EXISTS idx_admin_reports_created_at ON admin_reports("createdAt");
