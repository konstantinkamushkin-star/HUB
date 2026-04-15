-- Extended diver-facing profile (JSON). Merged on PATCH /api/auth/me.
ALTER TABLE users
  ADD COLUMN IF NOT EXISTS diver_profile jsonb;

COMMENT ON COLUMN users.diver_profile IS 'Client-managed diver profile payload (display preferences, certs, privacy, etc.)';
