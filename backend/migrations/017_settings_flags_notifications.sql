-- Feature flags, system settings, notification campaigns

CREATE TABLE IF NOT EXISTS admin_feature_flags (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  key VARCHAR(120) UNIQUE NOT NULL,
  enabled BOOLEAN DEFAULT false,
  "rolloutRules" JSONB,
  description TEXT,
  "updatedByAdminId" UUID,
  "createdAt" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  "updatedAt" TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS admin_system_settings (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  key VARCHAR(120) UNIQUE NOT NULL,
  value JSONB NOT NULL,
  "isSensitive" BOOLEAN DEFAULT false,
  "updatedByAdminId" UUID,
  "createdAt" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  "updatedAt" TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS admin_notification_campaigns (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  channel VARCHAR(32) NOT NULL,
  title VARCHAR(255) NOT NULL,
  body TEXT NOT NULL,
  audience JSONB,
  status VARCHAR(32) DEFAULT 'created',
  "createdByAdminId" UUID,
  "recipientCount" INTEGER DEFAULT 0,
  "createdAt" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  "updatedAt" TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_admin_notification_campaigns_channel ON admin_notification_campaigns(channel);
CREATE INDEX IF NOT EXISTS idx_admin_notification_campaigns_status ON admin_notification_campaigns(status);
CREATE INDEX IF NOT EXISTS idx_admin_notification_campaigns_created_at ON admin_notification_campaigns("createdAt");
