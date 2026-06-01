-- Free-text dive site / place label (optional), independent of diveSiteId
ALTER TABLE dive_logs
  ADD COLUMN IF NOT EXISTS "locationName" VARCHAR(255) NULL;
