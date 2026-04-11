-- Verification workflow history + merge module support

CREATE TABLE IF NOT EXISTS admin_verification_requests (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  "targetType" VARCHAR(64) NOT NULL,
  "targetId" VARCHAR(128) NOT NULL,
  status VARCHAR(32) DEFAULT 'pending',
  "attemptNumber" INTEGER DEFAULT 1,
  documents JSONB,
  "decisionNote" TEXT,
  "handledByAdminId" UUID,
  history JSONB,
  "createdAt" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  "updatedAt" TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_admin_verification_requests_target
  ON admin_verification_requests("targetType", "targetId");
CREATE INDEX IF NOT EXISTS idx_admin_verification_requests_status
  ON admin_verification_requests(status);
CREATE INDEX IF NOT EXISTS idx_admin_verification_requests_created_at
  ON admin_verification_requests("createdAt");
