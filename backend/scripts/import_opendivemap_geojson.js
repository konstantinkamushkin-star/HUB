#!/usr/bin/env node
/**
 * Импорт дайв-сайтов из OpenDiveMap-style GeoJSON (FeatureCollection, Point).
 *
 * Использование:
 *   node scripts/import_opendivemap_geojson.js /path/to/opendivemap_all_sites.geojson
 *   DB_HOST=127.0.0.1 node scripts/import_opendivemap_geojson.js ./opendivemap_all_sites.geojson --dry-run
 *
 * На VPS (БД на localhost, порт 5432):
 *   cd /opt/divehub-backend && DB_HOST=127.0.0.1 node scripts/import_opendivemap_geojson.js /tmp/opendivemap_all_sites.geojson
 */

const { Pool } = require('pg');
const fs = require('fs');
const path = require('path');

function loadEnvFile() {
  try {
    const envFile = fs.readFileSync(path.join(__dirname, '..', '.env'), 'utf8');
    envFile.split('\n').forEach((line) => {
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith('#')) return;
      const eq = trimmed.indexOf('=');
      if (eq <= 0) return;
      const key = trimmed.slice(0, eq).trim();
      let value = trimmed.slice(eq + 1).trim();
      if (
        (value.startsWith('"') && value.endsWith('"')) ||
        (value.startsWith("'") && value.endsWith("'"))
      ) {
        value = value.slice(1, -1);
      }
      if (key && value && process.env[key] === undefined) {
        process.env[key] = value;
      }
    });
  } catch {
    // no .env
  }
}

loadEnvFile();

const dbConfig = {
  host: process.env.DB_HOST || 'localhost',
  port: parseInt(process.env.DB_PORT || '5432', 10),
  database: process.env.DB_DATABASE || 'divehub',
  user: process.env.DB_USERNAME || process.env.DB_USER || 'postgres',
  password: process.env.DB_PASSWORD || 'postgres',
};

const pool = new Pool(dbConfig);

function mapSiteTypes(props) {
  const out = new Set();
  const env = (props.environment || '').toLowerCase();
  const entry = (props.entry || '').toLowerCase();
  const name = (props.name || '').toLowerCase();
  const tops = Array.isArray(props.topologies) ? props.topologies : [];
  if (name.includes('wreck') || tops.some((t) => String(t).toLowerCase().includes('wreck'))) {
    out.add('wreck');
  }
  if (env === 'ocean' || env === 'sea') out.add('reef');
  if (entry === 'shore') out.add('shore');
  if (entry === 'boat') out.add('boat');
  if (!out.size) out.add('reef');
  return Array.from(out);
}

function guessDifficulty(props, tags) {
  const d = props.max_depth;
  if (d == null || Number.isNaN(Number(d))) return 2;
  if (d <= 18) return 1;
  if (d <= 30) return 2;
  if (d <= 40) return 3;
  return 4;
}

function featureToRow(feature) {
  const g = feature.geometry;
  if (!g || g.type !== 'Point' || !Array.isArray(g.coordinates) || g.coordinates.length < 2) {
    return null;
  }
  const lon = Number(g.coordinates[0]);
  const lat = Number(g.coordinates[1]);
  if (!Number.isFinite(lat) || !Number.isFinite(lon) || Math.abs(lat) > 90 || Math.abs(lon) > 180) {
    return null;
  }

  const p = feature.properties || {};
  const tags = p.tags && typeof p.tags === 'object' ? p.tags : {};

  const name = (p.name || 'Unknown Dive Site').trim().substring(0, 255);
  const description = (tags.description || '').toString().substring(0, 8000);
  const country = (p.country_name || '').toString().substring(0, 100);
  const region = p.sea_name ? String(p.sea_name).substring(0, 100) : null;

  const depthMax = p.max_depth != null ? Number(p.max_depth) : null;
  const depthMin = null;

  let averageRating = 0;
  if (tags.average_rating != null) {
    const ar = Number(tags.average_rating);
    if (Number.isFinite(ar)) averageRating = Math.min(5, Math.max(0, ar));
  }

  let reviewCount = 0;
  if (tags.logged_dives != null) {
    const n = parseInt(tags.logged_dives, 10);
    if (Number.isFinite(n)) reviewCount = Math.min(n, 2147483647);
  }

  const marineLife = [];
  if (tags.description_wildlife) {
    marineLife.push(
      ...String(tags.description_wildlife)
        .split(/[,;]/)
        .map((s) => s.trim())
        .filter(Boolean)
        .slice(0, 20),
    );
  }

  const photo_urls = [];
  if (tags.thumbnail) photo_urls.push(String(tags.thumbnail).substring(0, 2000));

  return {
    name,
    description: description || null,
    latitude: parseFloat(lat.toFixed(7)),
    longitude: parseFloat(lon.toFixed(7)),
    country: country || null,
    region,
    site_types: mapSiteTypes(p),
    difficulty_level: guessDifficulty(p, tags),
    depth_min: depthMin,
    depth_max: depthMax != null && Number.isFinite(depthMax) ? depthMax : null,
    average_rating: averageRating,
    review_count: reviewCount,
    marine_life: marineLife,
    photo_urls,
    status: 'published',
  };
}

async function siteExists(lat, lng, name) {
  const result = await pool.query(
    `SELECT id FROM dive_sites 
     WHERE ABS(latitude - $1) < 0.0001 
       AND ABS(longitude - $2) < 0.0001
       AND LOWER(name) = LOWER($3)
     LIMIT 1`,
    [lat, lng, name],
  );
  return result.rows.length > 0;
}

async function insertRow(row) {
  const q = `
    INSERT INTO dive_sites (
      name, description, location,
      country, region,
      site_types, difficulty_level,
      depth_min, depth_max,
      average_rating, review_count,
      marine_life, photo_urls,
      is_active, status,
      created_at, updated_at
    ) VALUES (
      $1, $2, ST_SetSRID(ST_MakePoint($3, $4), 4326)::geography,
      $5, $6,
      $7, $8,
      $9, $10,
      $11, $12,
      $13, $14,
      true, $15,
      NOW(), NOW()
    )
    RETURNING id
  `;
  const values = [
    row.name,
    row.description,
    row.longitude,
    row.latitude,
    row.country,
    row.region,
    row.site_types,
    row.difficulty_level,
    row.depth_min,
    row.depth_max,
    row.average_rating,
    row.review_count,
    row.marine_life,
    row.photo_urls,
    row.status,
  ];
  const r = await pool.query(q, values);
  return r.rows[0];
}

async function main() {
  const args = process.argv.slice(2).filter((a) => a !== '--dry-run');
  const dryRun = process.argv.includes('--dry-run');
  const inputPath = args[0];
  if (!inputPath) {
    console.error('Usage: node scripts/import_opendivemap_geojson.js <file.geojson> [--dry-run]');
    process.exit(1);
  }

  const abs = path.isAbsolute(inputPath) ? inputPath : path.join(process.cwd(), inputPath);
  if (!fs.existsSync(abs)) {
    console.error('File not found:', abs);
    process.exit(1);
  }

  console.log('Reading (may take a while for large files)...');
  const raw = fs.readFileSync(abs, 'utf8');
  const geo = JSON.parse(raw);
  if (geo.type !== 'FeatureCollection' || !Array.isArray(geo.features)) {
    console.error('Expected GeoJSON FeatureCollection');
    process.exit(1);
  }

  const features = geo.features;
  console.log('Features:', features.length);

  if (dryRun) {
    let ok = 0;
    let bad = 0;
    for (const f of features) {
      if (featureToRow(f)) ok++;
      else bad++;
    }
    console.log('Dry run: mappable features:', ok, 'invalid geometry/properties:', bad);
    await pool.end();
    return;
  }

  await pool.query('SELECT 1');
  console.log('DB OK.');

  let inserted = 0;
  let skipped = 0;
  let bad = 0;
  let dup = 0;

  for (let i = 0; i < features.length; i++) {
    const row = featureToRow(features[i]);
    if (!row) {
      bad++;
      continue;
    }
    if (dryRun) {
      inserted++;
      continue;
    }
    try {
      const ex = await siteExists(row.latitude, row.longitude, row.name);
      if (ex) {
        dup++;
        continue;
      }
      await insertRow(row);
      inserted++;
    } catch (e) {
      console.error('Row', i, e.message);
      bad++;
    }
    if ((i + 1) % 2000 === 0) {
      console.log('Progress:', i + 1, '/', features.length);
    }
  }

  console.log('Done. inserted:', inserted, 'duplicates:', dup, 'skipped invalid:', bad, 'dryRun:', dryRun);
  await pool.end();
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
