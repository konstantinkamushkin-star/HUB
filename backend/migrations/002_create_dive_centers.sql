-- Migration: Create dive_centers table with PostGIS
-- Version: 2.0
-- Date: 2026-01-16

-- Enable PostGIS extension (if not already enabled)
CREATE EXTENSION IF NOT EXISTS postgis;

-- Create dive_centers table
CREATE TABLE IF NOT EXISTS dive_centers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- Basic information
    name VARCHAR(255) NOT NULL,
    description TEXT,
    localized_description JSONB,
    
    -- Geolocation (PostGIS GEOGRAPHY type for accurate distances)
    location GEOGRAPHY(POINT, 4326) NOT NULL,
    latitude DOUBLE PRECISION GENERATED ALWAYS AS (ST_Y(location::geometry)) STORED,
    longitude DOUBLE PRECISION GENERATED ALWAYS AS (ST_X(location::geometry)) STORED,
    
    -- Address
    country VARCHAR(100),
    city VARCHAR(100),
    address TEXT,
    
    -- Contact info
    email VARCHAR(255),
    phone VARCHAR(50),
    website VARCHAR(255),
    social_media JSONB,
    
    -- Services and features
    services TEXT[] NOT NULL DEFAULT '{}',
    certification_agency VARCHAR(50),
    languages TEXT[] DEFAULT '{}',
    nitrox_available BOOLEAN DEFAULT false,
    price_from DECIMAL(10, 2) CHECK (price_from >= 0),
    
    -- Operating hours (stored as JSONB)
    operating_hours JSONB,
    
    -- Media
    photo_urls TEXT[] DEFAULT '{}',
    video_urls TEXT[] DEFAULT '{}',
    
    -- Rating and popularity
    average_rating DECIMAL(3, 2) DEFAULT 0.0 CHECK (average_rating BETWEEN 0 AND 5),
    review_count INTEGER DEFAULT 0 CHECK (review_count >= 0),
    
    -- Additional
    ai_summary TEXT,
    affiliated_sites UUID[] DEFAULT '{}',
    instructor_ids UUID[] DEFAULT '{}',
    
    -- Metadata
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Create function to automatically set location from lat/lng
CREATE OR REPLACE FUNCTION set_dive_center_location()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.latitude IS NOT NULL AND NEW.longitude IS NOT NULL THEN
        NEW.location := ST_SetSRID(ST_MakePoint(NEW.longitude, NEW.latitude), 4326)::geography;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger for location
DROP TRIGGER IF EXISTS dive_center_location_trigger ON dive_centers;
CREATE TRIGGER dive_center_location_trigger
    BEFORE INSERT OR UPDATE OF latitude, longitude ON dive_centers
    FOR EACH ROW
    EXECUTE FUNCTION set_dive_center_location();

-- Create function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_dive_center_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger for updated_at
DROP TRIGGER IF EXISTS dive_center_updated_at_trigger ON dive_centers;
CREATE TRIGGER dive_center_updated_at_trigger
    BEFORE UPDATE ON dive_centers
    FOR EACH ROW
    EXECUTE FUNCTION update_dive_center_updated_at();

-- ============================================
-- INDEXES (Critical for performance)
-- ============================================

-- 1. GIST index for geospatial queries (MOST IMPORTANT!)
CREATE INDEX IF NOT EXISTS idx_dive_centers_location_gist 
ON dive_centers USING GIST (location);

-- 2. Composite index for active centers + geo
CREATE INDEX IF NOT EXISTS idx_dive_centers_active_location 
ON dive_centers USING GIST (location) 
WHERE is_active = true;

-- 3. GIN index for array filtering (services)
CREATE INDEX IF NOT EXISTS idx_dive_centers_services 
ON dive_centers USING GIN (services);

-- 4. Index for rating sorting
CREATE INDEX IF NOT EXISTS idx_dive_centers_rating 
ON dive_centers (average_rating DESC, review_count DESC) 
WHERE is_active = true;

-- 5. Index for country/city (for fallback)
CREATE INDEX IF NOT EXISTS idx_dive_centers_country_city 
ON dive_centers (country, city) 
WHERE is_active = true;

-- 6. Index for email (for uniqueness checks)
CREATE INDEX IF NOT EXISTS idx_dive_centers_email 
ON dive_centers (email) 
WHERE email IS NOT NULL;
