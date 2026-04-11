-- Public instructor bio (editable by dive center admin for staff listed at center).
ALTER TABLE users ADD COLUMN IF NOT EXISTS bio TEXT;
