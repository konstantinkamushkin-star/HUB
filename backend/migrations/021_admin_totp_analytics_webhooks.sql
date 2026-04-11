-- Admin TOTP (только для админ-входа в веб-панель)
ALTER TABLE users
  ADD COLUMN IF NOT EXISTS "adminTotpSecret" VARCHAR(64),
  ADD COLUMN IF NOT EXISTS "adminTotpEnabled" BOOLEAN NOT NULL DEFAULT false;

-- События продукта (аналитика)
CREATE TABLE IF NOT EXISTS analytics_events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name VARCHAR(128) NOT NULL,
  properties JSONB,
  "userId" UUID,
  "sessionId" VARCHAR(128),
  source VARCHAR(64),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_analytics_events_name ON analytics_events (name);
CREATE INDEX IF NOT EXISTS idx_analytics_events_created ON analytics_events (created_at DESC);

-- Входящие webhook платежей (Stripe и др.)
CREATE TABLE IF NOT EXISTS payment_webhook_events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  provider VARCHAR(32) NOT NULL,
  event_type VARCHAR(128),
  payload JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_payment_webhook_provider ON payment_webhook_events (provider);
CREATE INDEX IF NOT EXISTS idx_payment_webhook_created ON payment_webhook_events (created_at DESC);
