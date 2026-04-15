-- Migration: services catalog for dive centers (packages/pricing)

CREATE TABLE IF NOT EXISTS center_services (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dive_center_id UUID NOT NULL REFERENCES dive_centers(id) ON DELETE CASCADE,

    name VARCHAR(200) NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    service_type VARCHAR(40) NOT NULL DEFAULT 'fun_dive',

    base_price_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    currency VARCHAR(8) NOT NULL DEFAULT 'USD',
    pricing_unit VARCHAR(24) NOT NULL DEFAULT 'per_person',

    duration_minutes INTEGER NOT NULL DEFAULT 0,
    max_participants INTEGER NOT NULL DEFAULT 0,

    requirements TEXT[] NOT NULL DEFAULT '{}',
    included_items TEXT[] NOT NULL DEFAULT '{}',
    pricing_rules JSONB NULL,

    own_gear_discount_percent NUMERIC(6,2) NULL,
    group_discount_threshold INTEGER NULL,
    group_discount_percent NUMERIC(6,2) NULL,
    night_dive_surcharge_amount NUMERIC(12,2) NULL,
    private_instructor_surcharge_amount NUMERIC(12,2) NULL,

    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE OR REPLACE FUNCTION update_center_services_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS update_center_services_updated_at ON center_services;
CREATE TRIGGER update_center_services_updated_at
    BEFORE UPDATE ON center_services
    FOR EACH ROW
    EXECUTE FUNCTION update_center_services_updated_at();

CREATE INDEX IF NOT EXISTS idx_center_services_center ON center_services (dive_center_id);
CREATE INDEX IF NOT EXISTS idx_center_services_active ON center_services (is_active);
CREATE INDEX IF NOT EXISTS idx_center_services_type ON center_services (service_type);
