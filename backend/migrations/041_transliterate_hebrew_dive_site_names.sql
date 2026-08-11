-- Romanize Hebrew dive site names to Latin (English-friendly display).

UPDATE dive_sites
SET name = 'Shunit Almogim'
WHERE is_active = true AND name = 'שונית אלמוגים';

UPDATE dive_sites
SET name = 'Ha Me''arot'
WHERE is_active = true AND name = 'המערות';
