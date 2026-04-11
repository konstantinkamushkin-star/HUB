-- Create users table for authentication
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    "firstName" VARCHAR(255) NOT NULL,
    "lastName" VARCHAR(255) NOT NULL,
    "avatarUrl" VARCHAR(500),
    phone VARCHAR(50),
    "dateOfBirth" DATE,
    role VARCHAR(50) DEFAULT 'DIVER_BASIC',
    "subscriptionTier" VARCHAR(50),
    "subscriptionExpiresAt" TIMESTAMP,
    "totalDives" INTEGER DEFAULT 0,
    "totalDiveTime" INTEGER DEFAULT 0,
    "maxDepth" DOUBLE PRECISION,
    language VARCHAR(10) DEFAULT 'en',
    "countryCode" VARCHAR(10),
    timezone VARCHAR(50) DEFAULT 'UTC',
    "emailVerified" BOOLEAN DEFAULT false,
    "phoneVerified" BOOLEAN DEFAULT false,
    "lastLogin" TIMESTAMP,
    "passwordResetCode" VARCHAR(10),
    "passwordResetExpires" TIMESTAMP,
    "shareLogbook" BOOLEAN DEFAULT false,
    "createdAt" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_role ON users(role);

-- Create trigger to update updatedAt
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW."updatedAt" = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_users_updated_at BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
