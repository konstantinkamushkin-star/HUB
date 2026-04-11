-- Compliance requests + data jobs

CREATE TABLE IF NOT EXISTS admin_compliance_requests (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  "userId" UUID NOT NULL,
  type VARCHAR(32) NOT NULL,
  status VARCHAR(32) DEFAULT 'pending',
  reason TEXT,
  payload JSONB,
  "handledByAdminId" UUID,
  "createdAt" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  "updatedAt" TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_admin_compliance_requests_user_id ON admin_compliance_requests("userId");
CREATE INDEX IF NOT EXISTS idx_admin_compliance_requests_type ON admin_compliance_requests(type);
CREATE INDEX IF NOT EXISTS idx_admin_compliance_requests_status ON admin_compliance_requests(status);
CREATE INDEX IF NOT EXISTS idx_admin_compliance_requests_created_at ON admin_compliance_requests("createdAt");

CREATE TABLE IF NOT EXISTS admin_data_jobs (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  type VARCHAR(32) NOT NULL,
  format VARCHAR(32) NOT NULL,
  "targetType" VARCHAR(64) NOT NULL,
  status VARCHAR(32) DEFAULT 'queued',
  filters JSONB,
  "resultMeta" JSONB,
  "createdByAdminId" UUID,
  "createdAt" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  "updatedAt" TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_admin_data_jobs_type ON admin_data_jobs(type);
CREATE INDEX IF NOT EXISTS idx_admin_data_jobs_status ON admin_data_jobs(status);
CREATE INDEX IF NOT EXISTS idx_admin_data_jobs_created_at ON admin_data_jobs("createdAt");
