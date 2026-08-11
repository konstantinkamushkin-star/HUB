-- Optional custom dive title (falls back to reef/site + date on clients)
ALTER TABLE dive_logs
  ADD COLUMN IF NOT EXISTS title VARCHAR(255) NULL;
