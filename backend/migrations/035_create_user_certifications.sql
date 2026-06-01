-- Diver certification cards (PADI/SSI/etc.) linked to users
-- Column names match TypeORM entity (snake_case in DB via @Column name).
CREATE TABLE IF NOT EXISTS user_certifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    agency VARCHAR(128) NOT NULL,
    level VARCHAR(256) NOT NULL,
    card_image_url TEXT,
    issue_date TIMESTAMPTZ,
    instructor_number VARCHAR(128),
    verification_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_user_certifications_user_id ON user_certifications (user_id);
