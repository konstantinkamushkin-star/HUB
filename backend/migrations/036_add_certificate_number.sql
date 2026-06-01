ALTER TABLE user_certifications
    ADD COLUMN IF NOT EXISTS certificate_number VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS idx_user_certifications_user_cert_number
    ON user_certifications (user_id, certificate_number)
    WHERE certificate_number IS NOT NULL;
