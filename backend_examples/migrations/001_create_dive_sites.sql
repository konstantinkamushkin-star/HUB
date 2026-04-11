-- Migration: Create dive_sites table with PostGIS
-- Version: 1.0
-- Date: 2026-01-16

-- Enable PostGIS extension
CREATE EXTENSION IF NOT EXISTS postgis;

-- Create dive_sites table
CREATE TABLE dive_sites (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- Basic information
    name VARCHAR(255) NOT NULL,
    description TEXT,
    localized_name JSONB,
    localized_description JSONB,
    
    -- Geolocation (PostGIS GEOGRAPHY type for accurate distances)
    location GEOGRAPHY(POINT, 4326) NOT NULL,
    latitude DOUBLE PRECISION GENERATED ALWAYS AS (ST_Y(location::geometry)) STORED,
    longitude DOUBLE PRECISION GENERATED ALWAYS AS (ST_X(location::geometry)) STORED,
    
    -- Address
    country VARCHAR(100),
    region VARCHAR(100),
    address TEXT,
    
    -- Characteristics
    site_types TEXT[] NOT NULL DEFAULT '{}',
    difficulty_level INTEGER NOT NULL DEFAULT 1 CHECK (difficulty_level BETWEEN 1 AND 4),
    depth_min DOUBLE PRECISION CHECK (depth_min >= 0),
    depth_max DOUBLE PRECISION CHECK (depth_max >= 0),
    water_temp_min DOUBLE PRECISION,
    water_temp_max DOUBLE PRECISION,
    seasonality JSONB, -- {"jan": true, "feb": true, ...} or null for year-round
    access_type TEXT[] DEFAULT '{}',
    price_from DECIMAL(10, 2) CHECK (price_from >= 0),
    
    -- Rating and popularity
    average_rating DECIMAL(3, 2) DEFAULT 0.0 CHECK (average_rating BETWEEN 0 AND 5),
    review_count INTEGER DEFAULT 0 CHECK (review_count >= 0),
    
    -- Media
    photo_urls TEXT[] DEFAULT '{}',
    video_urls TEXT[] DEFAULT '{}',
    marine_life TEXT[] DEFAULT '{}',
    
    -- Metadata
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    -- Additional
    ai_summary TEXT,
    affiliated_centers UUID[] DEFAULT '{}'
);

-- Create function to automatically set location from lat/lng
CREATE OR REPLACE FUNCTION set_dive_site_location()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.latitude IS NOT NULL AND NEW.longitude IS NOT NULL THEN
        NEW.location := ST_SetSRID(ST_MakePoint(NEW.longitude, NEW.latitude), 4326)::geography;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger for location
CREATE TRIGGER dive_site_location_trigger
    BEFORE INSERT OR UPDATE OF latitude, longitude ON dive_sites
    FOR EACH ROW
    EXECUTE FUNCTION set_dive_site_location();

-- Create function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger for updated_at
CREATE TRIGGER dive_site_updated_at_trigger
    BEFORE UPDATE ON dive_sites
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- ============================================
-- INDEXES (Critical for performance)
-- ============================================

-- 1. GIST index for geospatial queries (MOST IMPORTANT!)
CREATE INDEX idx_dive_sites_location_gist 
ON dive_sites USING GIST (location);

-- 2. Composite index for active sites + geo
CREATE INDEX idx_dive_sites_active_location 
ON dive_sites USING GIST (location) 
WHERE is_active = true;

-- 3. GIN index for array filtering (site_types)
CREATE INDEX idx_dive_sites_site_types 
ON dive_sites USING GIN (site_types);

-- 4. Index for difficulty filtering
CREATE INDEX idx_dive_sites_difficulty 
ON dive_sites (difficulty_level) 
WHERE is_active = true;

-- 5. Index for rating sorting
CREATE INDEX idx_dive_sites_rating 
ON dive_sites (average_rating DESC, review_count DESC) 
WHERE is_active = true;

-- 6. Index for depth filtering
CREATE INDEX idx_dive_sites_depth 
ON dive_sites (depth_min, depth_max) 
WHERE is_active = true;

-- 7. Index for country/region (for fallback)
CREATE INDEX idx_dive_sites_country_region 
ON dive_sites (country, region) 
WHERE is_active = true;

-- 8. Index for created_at (for newest sorting)
CREATE INDEX idx_dive_sites_created_at 
ON dive_sites (created_at DESC) 
WHERE is_active = true;

-- 9. Composite index for common filters
CREATE INDEX idx_dive_sites_composite 
ON dive_sites (difficulty_level, country, is_active) 
INCLUDE (latitude, longitude, average_rating, review_count);

-- 10. GIN index for access_type array
CREATE INDEX idx_dive_sites_access_type 
ON dive_sites USING GIN (access_type);

-- ============================================
-- SAMPLE DATA (for testing)
-- ============================================

-- Insert sample dive sites
INSERT INTO dive_sites (
    name, description, latitude, longitude,
    country, region, site_types, difficulty_level,
    depth_min, depth_max, average_rating, review_count,
    is_active
) VALUES
(
    'Blue Hole', 
    'Famous circular sinkhole, one of the most popular dive sites in the world',
    17.3158, -87.5346,
    'Belize', 'Ambergris Caye',
    ARRAY['wall', 'cave'], 3,
    0, 124, 4.8, 1234,
    true
),
(
    'Great Blue Hole',
    'Underwater sinkhole off the coast of Belize',
    17.3158, -87.5346,
    'Belize', 'Lighthouse Reef',
    ARRAY['wall'], 4,
    0, 124, 4.9, 2156,
    true
),
(
    'Shark Ray Alley',
    'Shallow reef where you can swim with nurse sharks and stingrays',
    17.9167, -87.9500,
    'Belize', 'Ambergris Caye',
    ARRAY['reef'], 1,
    3, 12, 4.7, 892,
    true
);

-- ============================================
-- VERIFICATION QUERIES
-- ============================================

-- Test geospatial query
EXPLAIN ANALYZE
SELECT 
    id,
    name,
    ST_Distance(
        location,
        ST_SetSRID(ST_MakePoint(-87.5346, 17.3158), 4326)::geography
    ) as distance_meters
FROM dive_sites
WHERE is_active = true
  AND ST_DWithin(
      location,
      ST_SetSRID(ST_MakePoint(-87.5346, 17.3158), 4326)::geography,
      50000 -- 50km
  )
ORDER BY distance_meters
LIMIT 20;

-- Expected: Should use idx_dive_sites_location_gist index
-- Execution time should be < 50ms
