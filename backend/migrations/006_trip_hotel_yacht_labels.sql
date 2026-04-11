-- Optional display names for daily/safari (iOS-style); hotel_id/yacht_id stay UUID for future FKs.
ALTER TABLE trips ADD COLUMN IF NOT EXISTS hotel_label VARCHAR(200);
ALTER TABLE trips ADD COLUMN IF NOT EXISTS yacht_label VARCHAR(200);
