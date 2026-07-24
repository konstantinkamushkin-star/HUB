-- One review per user per place (dive_site / dive_center / shop / instructor).
-- Keep the newest duplicate, then enforce uniqueness.

DELETE FROM reviews a
USING reviews b
WHERE a."userId" = b."userId"
  AND a."reviewableType" = b."reviewableType"
  AND a."reviewableId" = b."reviewableId"
  AND (
    a."createdAt" < b."createdAt"
    OR (a."createdAt" = b."createdAt" AND a.id::text < b.id::text)
  );

CREATE UNIQUE INDEX IF NOT EXISTS idx_reviews_user_reviewable_unique
  ON reviews ("userId", "reviewableType", "reviewableId");
