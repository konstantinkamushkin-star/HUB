-- Align trigger with users table column "updatedAt" (quoted camelCase from 003_create_users.sql).
-- If the function referenced NEW.updated_at, UPDATEs fail with:
--   record "new" has no field "updated_at"
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW."updatedAt" = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
