-- Admin foundation: statuses, lifecycle fields, audit logs

-- users lifecycle / moderation fields
ALTER TABLE users
  ADD COLUMN IF NOT EXISTS "accountStatus" VARCHAR(32) DEFAULT 'active',
  ADD COLUMN IF NOT EXISTS "verificationStatus" VARCHAR(32) DEFAULT 'unverified',
  ADD COLUMN IF NOT EXISTS "riskLevel" VARCHAR(16) DEFAULT 'normal',
  ADD COLUMN IF NOT EXISTS "mergedIntoUserId" UUID,
  ADD COLUMN IF NOT EXISTS "deletedAt" TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_users_account_status ON users("accountStatus");
CREATE INDEX IF NOT EXISTS idx_users_verification_status ON users("verificationStatus");
CREATE INDEX IF NOT EXISTS idx_users_risk_level ON users("riskLevel");

-- dive centers status fields
ALTER TABLE dive_centers
  ADD COLUMN IF NOT EXISTS status VARCHAR(32) DEFAULT 'pending',
  ADD COLUMN IF NOT EXISTS verification_status VARCHAR(32) DEFAULT 'unverified',
  ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_dive_centers_status ON dive_centers(status);
CREATE INDEX IF NOT EXISTS idx_dive_centers_verification_status ON dive_centers(verification_status);

-- dive sites status fields
ALTER TABLE dive_sites
  ADD COLUMN IF NOT EXISTS status VARCHAR(32) DEFAULT 'pending',
  ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_dive_sites_status ON dive_sites(status);

-- feed posts moderation fields
ALTER TABLE feed_posts
  ADD COLUMN IF NOT EXISTS "moderationStatus" VARCHAR(32) DEFAULT 'published',
  ADD COLUMN IF NOT EXISTS "commentsEnabled" BOOLEAN DEFAULT true,
  ADD COLUMN IF NOT EXISTS "likesEnabled" BOOLEAN DEFAULT true,
  ADD COLUMN IF NOT EXISTS "deletedAt" TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_feed_posts_moderation_status ON feed_posts("moderationStatus");

-- dive logs moderation fields
ALTER TABLE dive_logs
  ADD COLUMN IF NOT EXISTS "moderationStatus" VARCHAR(32) DEFAULT 'published',
  ADD COLUMN IF NOT EXISTS "deletedAt" TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_dive_logs_moderation_status ON dive_logs("moderationStatus");

-- immutable-style admin audit log storage
CREATE TABLE IF NOT EXISTS admin_audit_logs (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  "adminId" UUID,
  action VARCHAR(128) NOT NULL,
  "targetType" VARCHAR(64),
  "targetId" VARCHAR(128),
  "before" JSONB,
  "after" JSONB,
  ip VARCHAR(64),
  device VARCHAR(256),
  outcome VARCHAR(32) DEFAULT 'success',
  reason TEXT,
  "correlationId" VARCHAR(128),
  "createdAt" TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_admin_audit_logs_admin_id ON admin_audit_logs("adminId");
CREATE INDEX IF NOT EXISTS idx_admin_audit_logs_action ON admin_audit_logs(action);
CREATE INDEX IF NOT EXISTS idx_admin_audit_logs_target ON admin_audit_logs("targetType", "targetId");
CREATE INDEX IF NOT EXISTS idx_admin_audit_logs_created_at ON admin_audit_logs("createdAt");
