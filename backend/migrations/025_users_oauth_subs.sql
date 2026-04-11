-- OAuth: stable provider subject (Apple / Google) for account lookup and linking.
ALTER TABLE users ADD COLUMN IF NOT EXISTS "appleSub" VARCHAR(255) NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS "googleSub" VARCHAR(255) NULL;

CREATE UNIQUE INDEX IF NOT EXISTS "UQ_users_appleSub"
  ON users ("appleSub") WHERE "appleSub" IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS "UQ_users_googleSub"
  ON users ("googleSub") WHERE "googleSub" IS NOT NULL;
