-- Catalog listings imported from open sources (Google Sheets / CSV)
-- listing_only = view-only, no booking

ALTER TABLE dive_centers
  ADD COLUMN IF NOT EXISTS listing_only BOOLEAN NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS data_source VARCHAR(64),
  ADD COLUMN IF NOT EXISTS external_import_key VARCHAR(512),
  ADD COLUMN IF NOT EXISTS locations JSONB DEFAULT '[]'::jsonb;

CREATE UNIQUE INDEX IF NOT EXISTS idx_dive_centers_external_import_key
  ON dive_centers (external_import_key)
  WHERE external_import_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_dive_centers_listing_only
  ON dive_centers (listing_only)
  WHERE listing_only = true;

ALTER TABLE shops
  ADD COLUMN IF NOT EXISTS listing_only BOOLEAN NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS data_source VARCHAR(64),
  ADD COLUMN IF NOT EXISTS external_import_key VARCHAR(512),
  ADD COLUMN IF NOT EXISTS locations JSONB DEFAULT '[]'::jsonb;

CREATE UNIQUE INDEX IF NOT EXISTS idx_shops_external_import_key
  ON shops (external_import_key)
  WHERE external_import_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_shops_listing_only
  ON shops (listing_only)
  WHERE listing_only = true;
