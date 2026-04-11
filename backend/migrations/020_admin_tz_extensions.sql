-- Marine life dictionary (admin CMS-style)
CREATE TABLE IF NOT EXISTS marine_species (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  scientific_name VARCHAR(255) NOT NULL,
  common_name VARCHAR(255) NOT NULL,
  family VARCHAR(128),
  description TEXT,
  photo_url TEXT,
  status VARCHAR(32) NOT NULL DEFAULT 'published',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_marine_species_status ON marine_species (status);
CREATE INDEX IF NOT EXISTS idx_marine_species_common_name ON marine_species (LOWER(common_name));

-- Support tickets (helpdesk stub per TZ)
CREATE TABLE IF NOT EXISTS admin_support_tickets (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  reporter_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
  reporter_email VARCHAR(255),
  subject VARCHAR(512) NOT NULL,
  body TEXT NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'open',
  priority VARCHAR(16) NOT NULL DEFAULT 'normal',
  assigned_admin_id UUID REFERENCES users(id) ON DELETE SET NULL,
  resolution_note TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_admin_support_tickets_status ON admin_support_tickets (status);
CREATE INDEX IF NOT EXISTS idx_admin_support_tickets_created ON admin_support_tickets (created_at DESC);

-- CMS pages (static content)
CREATE TABLE IF NOT EXISTS admin_cms_pages (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  slug VARCHAR(255) NOT NULL,
  locale VARCHAR(16) NOT NULL DEFAULT 'ru',
  title VARCHAR(512) NOT NULL,
  body TEXT NOT NULL DEFAULT '',
  status VARCHAR(32) NOT NULL DEFAULT 'draft',
  published_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (slug, locale)
);
CREATE INDEX IF NOT EXISTS idx_admin_cms_pages_status ON admin_cms_pages (status);

-- Integration registry (config stub — no real secrets in repo)
CREATE TABLE IF NOT EXISTS admin_integrations (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  key VARCHAR(64) NOT NULL UNIQUE,
  display_name VARCHAR(255) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT false,
  config JSONB,
  last_check_at TIMESTAMPTZ,
  last_check_status VARCHAR(64),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Subscription plans catalog (billing stub; user tier stays on users.subscriptionTier)
CREATE TABLE IF NOT EXISTS admin_subscription_plans (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  code VARCHAR(64) NOT NULL UNIQUE,
  name VARCHAR(255) NOT NULL,
  description TEXT,
  price_cents INTEGER NOT NULL DEFAULT 0,
  currency VARCHAR(3) NOT NULL DEFAULT 'USD',
  billing_interval VARCHAR(32) NOT NULL DEFAULT 'monthly',
  active BOOLEAN NOT NULL DEFAULT true,
  features JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_admin_subscription_plans_active ON admin_subscription_plans (active);
