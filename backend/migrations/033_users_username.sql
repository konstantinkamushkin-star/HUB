-- Unique public handle (Instagram-style), normalized lowercase a-z0-9_ length 3–30.
-- Application enforces format; NULL allowed for legacy rows until profile is completed.

ALTER TABLE users ADD COLUMN IF NOT EXISTS username VARCHAR(32);

CREATE UNIQUE INDEX IF NOT EXISTS idx_users_username_unique
  ON users (username)
  WHERE username IS NOT NULL;

-- Best-effort backfill from diver_profile JSON (skips duplicates).
UPDATE users u
SET username = v.n
FROM (
  SELECT
    id,
    lower(
      regexp_replace(
        trim(both from coalesce(diver_profile ->> 'username', '')),
        '^@+',
        '',
        'g'
      )
    ) AS n
  FROM users
  WHERE diver_profile IS NOT NULL
    AND diver_profile ? 'username'
) v
WHERE u.id = v.id
  AND u.username IS NULL
  AND length(v.n) BETWEEN 3 AND 30
  AND v.n ~ '^[a-z0-9_]+$'
  AND NOT EXISTS (
    SELECT 1 FROM users o WHERE o.username = v.n AND o.id <> u.id
  );
