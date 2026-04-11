-- Migration: Create courses table
-- Version: 4.0
-- Date: 2026-01-16

CREATE TABLE IF NOT EXISTS courses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- Basic information
    name VARCHAR(255) NOT NULL,
    level VARCHAR(50) NOT NULL CHECK (level IN ('basic', 'advanced', 'professional', 'technical', 'specialization')),
    description TEXT,
    localized_description JSONB,
    
    -- Training systems
    training_systems TEXT[] DEFAULT '{}',
    
    -- Program modules (stored as JSONB)
    modules JSONB DEFAULT '[]',
    
    -- Duration
    duration INTEGER NOT NULL CHECK (duration > 0), -- in days
    
    -- Prerequisites
    prerequisites TEXT[] DEFAULT '{}',
    
    -- Relations
    dive_center_id UUID REFERENCES dive_centers(id) ON DELETE CASCADE,
    instructor_id UUID REFERENCES users(id) ON DELETE SET NULL,
    
    -- Media
    photo_urls TEXT[] DEFAULT '{}',
    
    -- Metadata
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_courses_dive_center_id ON courses(dive_center_id);
CREATE INDEX IF NOT EXISTS idx_courses_instructor_id ON courses(instructor_id);
CREATE INDEX IF NOT EXISTS idx_courses_level ON courses(level);
CREATE INDEX IF NOT EXISTS idx_courses_training_systems ON courses USING GIN(training_systems);

-- Create trigger for updated_at
CREATE TRIGGER update_courses_updated_at
    BEFORE UPDATE ON courses
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
