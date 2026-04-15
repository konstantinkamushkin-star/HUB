-- Migration: create bookings table for dive center/pool flows

CREATE TABLE IF NOT EXISTS bookings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    dive_center_id UUID NOT NULL REFERENCES dive_centers(id) ON DELETE CASCADE,
    service_id VARCHAR(120) NOT NULL,

    dive_site_id UUID NULL REFERENCES dive_sites(id) ON DELETE SET NULL,
    instructor_id UUID NULL REFERENCES users(id) ON DELETE SET NULL,

    date TIMESTAMPTZ NOT NULL,
    date_end TIMESTAMPTZ NULL,
    start_time VARCHAR(10) NOT NULL,

    participants JSONB NOT NULL DEFAULT '[]'::jsonb,
    participants_count INTEGER NULL CHECK (participants_count IS NULL OR participants_count > 0),

    gear_rental JSONB NULL,
    payment JSONB NOT NULL DEFAULT '{}'::jsonb,

    status VARCHAR(20) NOT NULL DEFAULT 'pending'
      CHECK (status IN ('pending', 'quoted', 'confirmed', 'completed', 'cancelled', 'refunded')),

    booking_type VARCHAR(20) NULL
      CHECK (booking_type IN ('open_water', 'pool')),
    request_mode VARCHAR(30) NULL
      CHECK (request_mode IN ('instant', 'manual_approval')),
    session_id VARCHAR(120) NULL,
    instructor_preferences JSONB NULL,
    equipment_rental JSONB NULL,

    notes TEXT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE OR REPLACE FUNCTION update_bookings_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS update_bookings_updated_at ON bookings;
CREATE TRIGGER update_bookings_updated_at
    BEFORE UPDATE ON bookings
    FOR EACH ROW
    EXECUTE FUNCTION update_bookings_updated_at();

CREATE INDEX IF NOT EXISTS idx_bookings_user_id ON bookings (user_id);
CREATE INDEX IF NOT EXISTS idx_bookings_center_id ON bookings (dive_center_id);
CREATE INDEX IF NOT EXISTS idx_bookings_instructor_id ON bookings (instructor_id);
CREATE INDEX IF NOT EXISTS idx_bookings_status ON bookings (status);
CREATE INDEX IF NOT EXISTS idx_bookings_date ON bookings (date);
