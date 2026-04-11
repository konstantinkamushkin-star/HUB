-- Migration: Create dive_sites (PostGIS) — базовая таблица для Explore / geo API.
-- Запускать на чистой БД до миграций, которые делают ALTER TABLE dive_sites (например 015).

CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE IF NOT EXISTS dive_sites (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    name VARCHAR(255) NOT NULL,
    description TEXT,
    localized_name JSONB,
    localized_description JSONB,

    location GEOGRAPHY(POINT, 4326) NOT NULL,
    latitude DOUBLE PRECISION GENERATED ALWAYS AS (ST_Y(location::geometry)) STORED,
    longitude DOUBLE PRECISION GENERATED ALWAYS AS (ST_X(location::geometry)) STORED,

    country VARCHAR(100),
    region VARCHAR(100),
    address TEXT,

    site_types TEXT[] NOT NULL DEFAULT '{}',
    difficulty_level INTEGER NOT NULL DEFAULT 1,

    depth_min DOUBLE PRECISION,
    depth_max DOUBLE PRECISION,
    water_temp_min DOUBLE PRECISION,
    water_temp_max DOUBLE PRECISION,
    seasonality JSONB,

    access_type TEXT[] NOT NULL DEFAULT '{}',
    price_from DECIMAL(10, 2),

    average_rating DECIMAL(3, 2) NOT NULL DEFAULT 0 CHECK (average_rating >= 0 AND average_rating <= 5),
    review_count INTEGER NOT NULL DEFAULT 0 CHECK (review_count >= 0),

    photo_urls TEXT[] NOT NULL DEFAULT '{}',
    video_urls TEXT[] NOT NULL DEFAULT '{}',
    marine_life TEXT[] NOT NULL DEFAULT '{}',

    is_active BOOLEAN NOT NULL DEFAULT true,
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    deleted_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    ai_summary TEXT,
    affiliated_centers UUID[] NOT NULL DEFAULT '{}'
);

CREATE OR REPLACE FUNCTION update_dive_site_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS dive_site_updated_at_trigger ON dive_sites;
CREATE TRIGGER dive_site_updated_at_trigger
    BEFORE UPDATE ON dive_sites
    FOR EACH ROW
    EXECUTE FUNCTION update_dive_site_updated_at();

CREATE INDEX IF NOT EXISTS idx_dive_sites_location_gist ON dive_sites USING GIST (location);
CREATE INDEX IF NOT EXISTS idx_dive_sites_difficulty ON dive_sites (difficulty_level);
CREATE INDEX IF NOT EXISTS idx_dive_sites_rating ON dive_sites (average_rating DESC, review_count DESC) WHERE is_active = true;
CREATE INDEX IF NOT EXISTS idx_dive_sites_reviews ON dive_sites (review_count);
CREATE INDEX IF NOT EXISTS idx_dive_sites_active ON dive_sites (is_active);
CREATE INDEX IF NOT EXISTS idx_dive_sites_status ON dive_sites (status);
