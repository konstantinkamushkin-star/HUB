-- Migration: Create trips table
-- Version: 5.0
-- Date: 2026-01-16

CREATE TABLE IF NOT EXISTS trips (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- Organizer information
    organizer_id UUID NOT NULL,
    organizer_type VARCHAR(20) NOT NULL CHECK (organizer_type IN ('dive_center', 'user')),
    
    -- Trip type
    trip_type VARCHAR(20) NOT NULL CHECK (trip_type IN ('daily', 'safari')),
    
    -- Location
    hotel_id UUID,
    yacht_id UUID,
    country VARCHAR(100) NOT NULL,
    region VARCHAR(100),
    
    -- Dates
    start_date DATE NOT NULL,
    end_date DATE NOT NULL CHECK (end_date >= start_date),
    
    -- Requirements
    minimum_certification_level VARCHAR(100),
    minimum_dives INTEGER CHECK (minimum_dives >= 0),
    
    -- Description
    description TEXT NOT NULL,
    
    -- Media
    photo_urls TEXT[] DEFAULT '{}',
    
    -- Capacity
    total_spots INTEGER NOT NULL CHECK (total_spots > 0),
    booked_spots INTEGER DEFAULT 0 CHECK (booked_spots >= 0 AND booked_spots <= total_spots),
    
    -- Participants (stored as JSONB)
    participants JSONB DEFAULT '[]',
    
    -- Available courses
    available_courses UUID[] DEFAULT '{}',
    
    -- Features
    nitrox_available BOOLEAN DEFAULT false,
    equipment_rental_available BOOLEAN DEFAULT false,
    
    -- Group leader (for dive centers)
    group_leader_id UUID REFERENCES users(id) ON DELETE SET NULL,
    
    -- Program (stored as JSONB)
    program_days JSONB DEFAULT '[]',
    
    -- Additional expenses (stored as JSONB)
    additional_expenses JSONB DEFAULT '[]',
    
    -- Price details (stored as JSONB)
    price_details JSONB NOT NULL,
    
    -- Metadata
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_trips_organizer_id ON trips(organizer_id);
CREATE INDEX IF NOT EXISTS idx_trips_organizer_type ON trips(organizer_type);
CREATE INDEX IF NOT EXISTS idx_trips_trip_type ON trips(trip_type);
CREATE INDEX IF NOT EXISTS idx_trips_country ON trips(country);
CREATE INDEX IF NOT EXISTS idx_trips_start_date ON trips(start_date);
CREATE INDEX IF NOT EXISTS idx_trips_end_date ON trips(end_date);
CREATE INDEX IF NOT EXISTS idx_trips_available_courses ON trips USING GIN(available_courses);

-- Create trigger for updated_at
CREATE TRIGGER update_trips_updated_at
    BEFORE UPDATE ON trips
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
