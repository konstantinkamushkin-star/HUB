-- Migration: Create shops table with PostGIS
-- Version: 6.0
-- Date: 2026-01-16

-- Enable PostGIS extension (if not already enabled)
CREATE EXTENSION IF NOT EXISTS postgis;

-- Create shops table
CREATE TABLE IF NOT EXISTS shops (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- Basic information
    name VARCHAR(255) NOT NULL,
    description TEXT,
    localized_name JSONB,
    localized_description JSONB,
    
    -- Shop type
    type VARCHAR(20) DEFAULT 'offline' CHECK (type IN ('offline', 'online')),
    
    -- Brands and services
    brands TEXT[] NOT NULL DEFAULT '{}',
    service_available BOOLEAN DEFAULT false,
    
    -- Geolocation (PostGIS GEOGRAPHY type for accurate distances)
    location GEOGRAPHY(POINT, 4326),
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
    
    -- Media
    photo_urls TEXT[] DEFAULT '{}',
    
    -- Rating and popularity
    average_rating DECIMAL(3, 2) DEFAULT 0.0 CHECK (average_rating BETWEEN 0 AND 5),
    review_count INTEGER DEFAULT 0 CHECK (review_count >= 0),
    
    -- Owner/Admin
    owner_id UUID REFERENCES users(id) ON DELETE SET NULL,
    
    -- Metadata
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Create function to automatically set location from lat/lng
CREATE OR REPLACE FUNCTION set_shop_location()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.latitude IS NOT NULL AND NEW.longitude IS NOT NULL THEN
        NEW.location := ST_SetSRID(ST_MakePoint(NEW.longitude, NEW.latitude), 4326)::geography;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger for location
DROP TRIGGER IF EXISTS shop_location_trigger ON shops;
CREATE TRIGGER shop_location_trigger
    BEFORE INSERT OR UPDATE OF latitude, longitude ON shops
    FOR EACH ROW
    EXECUTE FUNCTION set_shop_location();

-- Create function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_shop_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger for updated_at
DROP TRIGGER IF EXISTS shop_updated_at_trigger ON shops;
CREATE TRIGGER shop_updated_at_trigger
    BEFORE UPDATE ON shops
    FOR EACH ROW
    EXECUTE FUNCTION update_shop_updated_at();

-- ============================================
-- INDEXES (Critical for performance)
-- ============================================

-- 1. GIST index for geospatial queries
CREATE INDEX IF NOT EXISTS idx_shops_location_gist 
ON shops USING GIST (location)
WHERE location IS NOT NULL;

-- 2. Composite index for active shops + geo
CREATE INDEX IF NOT EXISTS idx_shops_active_location 
ON shops USING GIST (location) 
WHERE is_active = true AND location IS NOT NULL;

-- 3. GIN index for array filtering (brands)
CREATE INDEX IF NOT EXISTS idx_shops_brands 
ON shops USING GIN (brands);

-- 4. Index for rating sorting
CREATE INDEX IF NOT EXISTS idx_shops_rating 
ON shops (average_rating DESC, review_count DESC) 
WHERE is_active = true;

-- 5. Index for type
CREATE INDEX IF NOT EXISTS idx_shops_type 
ON shops (type) 
WHERE is_active = true;

-- 6. Index for owner
CREATE INDEX IF NOT EXISTS idx_shops_owner 
ON shops (owner_id) 
WHERE owner_id IS NOT NULL;

-- 7. Index for country/city
CREATE INDEX IF NOT EXISTS idx_shops_country_city 
ON shops (country, city) 
WHERE is_active = true;
