#!/usr/bin/env node
/**
 * Fetches dive sites for Egypt from RapidAPI World Scuba Diving Sites API
 * using exactly 20 GPS bounding-box requests (grid over country bounds).
 *
 * Env:
 *   RAPIDAPI_KEY — required
 *   RAPIDAPI_HOST — optional, default world-scuba-diving-sites-api.p.rapidapi.com
 *   RAPIDAPI_GPS_PATH — optional, default /divesites/gs (как в RapidAPI Code Snippets; было /divesites/gps)
 *
 * Output:
 *   dive_sites_egypt_rapidapi.json — array for import_processed_dive_sites.js
 *   dive_sites_egypt_rapidapi.fetch.json — meta (tiles, truncation warnings)
 */

const https = require('https');
const fs = require('fs');
const path = require('path');

function loadEnvFile(envPath, { override = false } = {}) {
  try {
    const envFile = fs.readFileSync(envPath, 'utf8');
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
      if (key && value && (override || process.env[key] === undefined)) {
        process.env[key] = value;
      }
    });
  } catch {
    // missing file
  }
}

loadEnvFile(path.join(__dirname, '.env'), { override: false });
loadEnvFile(path.join(__dirname, '.env.local'), { override: true });

const API_KEY = process.env.RAPIDAPI_KEY || process.env.X_RAPIDAPI_KEY;
const API_HOST =
  process.env.RAPIDAPI_HOST || 'world-scuba-diving-sites-api.p.rapidapi.com';
/** RapidAPI показывает в сниппете путь /divesites/gs; старый вариант — /divesites/gps */
const GPS_PATH =
  process.env.RAPIDAPI_GPS_PATH || '/divesites/gs';

const MAX_PER_REQUEST = 200;
const REQUEST_BUDGET = 20;

/** Egypt (mainland + Sinai): approximate bounds covering Med + Red Sea coasts */
const EGYPT_BOUNDS = {
  southWestLat: 22.0,
  northEastLat: 31.85,
  southWestLng: 24.5,
  northEastLng: 37.0,
};

const VALID_SITE_TYPES = new Set([
  'reef',
  'wreck',
  'wall',
  'cave',
  'drift',
  'shore',
  'boat',
  'other',
]);

function extractArray(payload) {
  if (Array.isArray(payload)) return payload;
  if (payload && Array.isArray(payload.data)) return payload.data;
  if (payload && Array.isArray(payload.divesites)) return payload.divesites;
  if (payload && Array.isArray(payload.results)) return payload.results;
  return [];
}

function mapSiteTypes(raw) {
  let types = raw.siteTypes || raw.types || raw.type;
  if (typeof types === 'string') types = [types];
  if (!Array.isArray(types) || types.length === 0) return ['reef'];
  const mapped = types
    .map((t) => String(t).toLowerCase().trim())
    .filter((t) => VALID_SITE_TYPES.has(t));
  return mapped.length ? mapped : ['reef'];
}

function mapDifficulty(raw) {
  const d = raw.difficulty ?? raw.difficultyLevel;
  const n = parseInt(d, 10);
  if (Number.isNaN(n) || n < 1) return 1;
  return Math.min(4, n);
}

function mapToImportSite(raw) {
  const lat = parseFloat(
    raw.latitude ?? raw.lat ?? raw.coordinates?.latitude ?? NaN
  );
  const lng = parseFloat(
    raw.longitude ?? raw.lng ?? raw.coordinates?.longitude ?? NaN
  );
  if (
    Number.isNaN(lat) ||
    Number.isNaN(lng) ||
    Math.abs(lat) > 90 ||
    Math.abs(lng) > 180
  ) {
    return null;
  }

  const name = String(
    raw.name || raw.diveSiteName || raw.title || 'Unknown Dive Site'
  ).trim();
  if (!name) return null;

  const desc = String(raw.description || raw.desc || raw.about || '').trim();

  return {
    name: name.substring(0, 255),
    description: desc.substring(0, 5000),
    latitude: parseFloat(lat.toFixed(6)),
    longitude: parseFloat(lng.toFixed(6)),
    country: String(raw.country || raw.countryName || 'Egypt').substring(0, 100),
    region: String(raw.region || raw.area || raw.location || '').substring(
      0,
      100
    ),
    siteTypes: mapSiteTypes(raw),
    difficulty: mapDifficulty(raw),
    depthMin:
      raw.depthMin ?? raw.minDepth ?? raw.depth?.min ?? null,
    depthMax:
      raw.depthMax ?? raw.maxDepth ?? raw.depth?.max ?? null,
    marineLife: Array.isArray(raw.marineLife)
      ? raw.marineLife
      : Array.isArray(raw.fish)
        ? raw.fish
        : [],
    source: 'rapidapi-world-scuba-diving-sites',
    externalId: raw.id ?? raw.diveSiteId ?? null,
  };
}

function buildGridTiles(rows, cols, b) {
  const { southWestLat, northEastLat, southWestLng, northEastLng } = b;
  const dLat = (northEastLat - southWestLat) / rows;
  const dLng = (northEastLng - southWestLng) / cols;
  const tiles = [];
  for (let r = 0; r < rows; r++) {
    for (let c = 0; c < cols; c++) {
      tiles.push({
        id: `r${r + 1}c${c + 1}`,
        southWestLat: southWestLat + r * dLat,
        northEastLat: southWestLat + (r + 1) * dLat,
        southWestLng: southWestLng + c * dLng,
        northEastLng: southWestLng + (c + 1) * dLng,
      });
    }
  }
  return tiles;
}

function requestGpsBox(box) {
  return new Promise((resolve, reject) => {
    const q = new URLSearchParams({
      southWestLat: String(box.southWestLat),
      northEastLat: String(box.northEastLat),
      southWestLng: String(box.southWestLng),
      northEastLng: String(box.northEastLng),
    });
    const path = `${GPS_PATH.startsWith('/') ? '' : '/'}${GPS_PATH}?${q.toString()}`;

    const req = https.request(
      {
        hostname: API_HOST,
        path,
        method: 'GET',
        headers: {
          'x-rapidapi-host': API_HOST,
          'x-rapidapi-key': API_KEY,
        },
      },
      (res) => {
        let data = '';
        res.on('data', (chunk) => {
          data += chunk;
        });
        res.on('end', () => {
          if (res.statusCode !== 200) {
            reject(
              new Error(`HTTP ${res.statusCode}: ${data.slice(0, 400)}`)
            );
            return;
          }
          try {
            resolve(JSON.parse(data));
          } catch (e) {
            reject(new Error(`JSON parse: ${e.message}`));
          }
        });
      }
    );
    req.on('error', reject);
    req.setTimeout(45000, () => {
      req.destroy();
      reject(new Error('timeout'));
    });
    req.end();
  });
}

function dedupeKey(site) {
  if (site.externalId != null && site.externalId !== '') {
    return `id:${site.externalId}`;
  }
  return `geo:${site.name.toLowerCase()}|${site.latitude.toFixed(5)}|${site.longitude.toFixed(5)}`;
}

async function main() {
  if (!API_KEY) {
    console.error('Missing RAPIDAPI_KEY in environment or backend/.env');
    process.exit(1);
  }

  const rows = 4;
  const cols = 5;
  const tiles = buildGridTiles(rows, cols, EGYPT_BOUNDS);
  if (tiles.length !== REQUEST_BUDGET) {
    console.error('Internal error: tile count must be', REQUEST_BUDGET);
    process.exit(1);
  }

  const meta = {
    region: 'Egypt',
    bounds: EGYPT_BOUNDS,
    grid: { rows, cols },
    maxPerRequest: MAX_PER_REQUEST,
    requestsUsed: 0,
    tiles: [],
    truncatedTiles: [],
    errors: [],
  };

  const byKey = new Map();

  for (let i = 0; i < tiles.length; i++) {
    const tile = tiles[i];
    meta.requestsUsed++;
    process.stdout.write(
      `[${i + 1}/${tiles.length}] ${tile.id} … `
    );
    try {
      const payload = await requestGpsBox(tile);
      const arr = extractArray(payload);
      const tileInfo = {
        id: tile.id,
        bounds: {
          southWestLat: tile.southWestLat,
          northEastLat: tile.northEastLat,
          southWestLng: tile.southWestLng,
          northEastLng: tile.northEastLng,
        },
        count: arr.length,
        maybeTruncated: arr.length >= MAX_PER_REQUEST,
      };
      meta.tiles.push(tileInfo);
      if (tileInfo.maybeTruncated) {
        meta.truncatedTiles.push(tile.id);
      }

      for (const raw of arr) {
        const site = mapToImportSite(raw);
        if (!site) continue;
        const k = dedupeKey(site);
        if (!byKey.has(k)) byKey.set(k, site);
      }
      console.log(`${arr.length} raw → ${byKey.size} unique`);
    } catch (e) {
      console.log(`ERR ${e.message}`);
      meta.errors.push({ tile: tile.id, message: e.message });
    }

    if (i < tiles.length - 1) {
      await new Promise((r) => setTimeout(r, 350));
    }
  }

  const out = Array.from(byKey.values());
  out.sort((a, b) => a.name.localeCompare(b.name));

  const outJson = path.join(__dirname, 'dive_sites_egypt_rapidapi.json');
  const metaJson = path.join(__dirname, 'dive_sites_egypt_rapidapi.fetch.json');

  fs.writeFileSync(outJson, JSON.stringify(out, null, 2), 'utf8');
  fs.writeFileSync(metaJson, JSON.stringify(meta, null, 2), 'utf8');

  console.log(`\n✅ Unique sites: ${out.length}`);
  console.log(`   Written: ${outJson}`);
  console.log(`   Meta:    ${metaJson}`);
  if (meta.truncatedTiles.length) {
    console.log(
      `\n⚠️  Tiles with ${MAX_PER_REQUEST}+ results (data may be incomplete): ${meta.truncatedTiles.join(', ')}`
    );
  }
  if (meta.errors.length) {
    console.log(`\n❌ Failed tiles: ${meta.errors.length}`);
  }
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
