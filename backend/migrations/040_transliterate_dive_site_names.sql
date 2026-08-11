-- Transliterate dive site names to ASCII Latin (English-friendly display).
-- Uses unaccent for diacritics; normalizes curly/smart quotes.

CREATE EXTENSION IF NOT EXISTS unaccent;

UPDATE dive_sites
SET name = regexp_replace(
  unaccent(name),
  E'[\u2018\u2019\u201A\u201B\u2032\u2035\u02BC\u02B9''`´]',
  '''',
  'g'
)
WHERE is_active = true
  AND name ~ '[^[:ascii:]]';
