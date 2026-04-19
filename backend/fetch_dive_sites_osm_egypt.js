#!/usr/bin/env node
/**
 * Дайвсайты Египта из OpenStreetMap (Overpass). Несколько лёгких запросов по плиткам
 * (полный bbox давал 504 на публичных инстансах).
 *
 *   node fetch_dive_sites_osm_egypt.js
 *
 * Выход: dive_sites_egypt_osm.json
 */

const https = require('https');
const fs = require('fs');
const path = require('path');

const OUTPUT = path.join(__dirname, 'dive_sites_egypt_osm.json');

/** Overpass требует осмысленный User-Agent (иначе 429) */
const USER_AGENT = 'DiveHub/1.0 (OSM Egypt dive import; +https://github.com/)';

const OVERPASS_APIS = [
  'https://overpass.openstreetmap.fr/api/interpreter',
  'https://overpass-api.de/api/interpreter',
  'https://overpass.kumi.systems/api/interpreter',
];

/** Плитки [south, west, north, east], покрывающие Египет */
const CORE_TILES = [
  [21.4, 24.35, 26.8, 30.8],
  [21.4, 30.8, 26.8, 37.1],
  [26.8, 24.35, 29.5, 33.0],
  [26.8, 33.0, 29.5, 37.1],
  [29.5, 24.35, 32.05, 33.5],
  [29.5, 33.5, 32.05, 37.1],
];

/** Красное море (египетский берег) — отдельные плитки только для natural=reef */
const REEF_TILES = [
  [22.0, 32.0, 26.5, 35.0],
  [22.0, 35.0, 26.5, 37.05],
  [26.5, 32.0, 29.8, 35.5],
  [26.5, 35.5, 29.8, 37.05],
];

function buildCoreQuery(bbox) {
  const [s, w, n, e] = bbox;
  return `
[out:json][timeout:120];
(
  node["sport"="scuba_diving"]["name"](${s},${w},${n},${e});
  way["sport"="scuba_diving"]["name"](${s},${w},${n},${e});
  relation["sport"="scuba_diving"]["name"](${s},${w},${n},${e});

  node["leisure"="diving"]["name"](${s},${w},${n},${e});
  way["leisure"="diving"]["name"](${s},${w},${n},${e});
  relation["leisure"="diving"]["name"](${s},${w},${n},${e});

  node["tourism"="attraction"]["attraction"="diving"]["name"](${s},${w},${n},${e});
  way["tourism"="attraction"]["attraction"="diving"]["name"](${s},${w},${n},${e});

  node["historic"="wreck"]["name"](${s},${w},${n},${e});
  way["historic"="wreck"]["name"](${s},${w},${n},${e});
  relation["historic"="wreck"]["name"](${s},${w},${n},${e});

  node["natural"="cave_entrance"]["name"](${s},${w},${n},${e});
  way["natural"="cave_entrance"]["name"](${s},${w},${n},${e});
);
out center tags;
`;
}

function buildReefQuery(bbox) {
  const [s, w, n, e] = bbox;
  return `
[out:json][timeout:120];
(
  node["natural"="reef"]["name"](${s},${w},${n},${e});
  way["natural"="reef"]["name"](${s},${w},${n},${e});
  relation["natural"="reef"]["name"](${s},${w},${n},${e});
);
out center tags;
`;
}

function postOverpass(apiUrl, query) {
  const postData = `data=${encodeURIComponent(query)}`;
  return new Promise((resolve, reject) => {
    const req = https.request(
      apiUrl,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
          'Content-Length': Buffer.byteLength(postData),
          'User-Agent': USER_AGENT,
        },
        timeout: 120000,
      },
      (res) => {
        let data = '';
        res.on('data', (c) => {
          data += c;
        });
        res.on('end', () => {
          if (res.statusCode !== 200) {
            reject(new Error(`HTTP ${res.statusCode}: ${data.slice(0, 300)}`));
            return;
          }
          try {
            resolve(JSON.parse(data));
          } catch (err) {
            reject(err);
          }
        });
      }
    );
    req.on('error', reject);
    req.on('timeout', () => {
      req.destroy();
      reject(new Error('timeout'));
    });
    req.write(postData);
    req.end();
  });
}

async function runQuery(query, label, retries = 2) {
  let lastErr = null;
  for (let attempt = 0; attempt <= retries; attempt++) {
    for (const url of OVERPASS_APIS) {
      try {
        if (attempt > 0) {
          process.stdout.write(`  [${label}] retry ${attempt} … `);
        } else {
          process.stdout.write(`  [${label}] ${url.replace('https://', '')} … `);
        }
        const data = await postOverpass(url, query);
        const n = data.elements ? data.elements.length : 0;
        console.log(`${n} элементов`);
        return data;
      } catch (e) {
        lastErr = e;
        if (attempt < retries && /429|504/.test(String(e.message))) {
          await new Promise((r) => setTimeout(r, 8000 * (attempt + 1)));
        }
        console.log(`fail`);
      }
    }
  }
  throw lastErr;
}

function inferRegion(lat, lon, tags) {
  const r =
    tags['addr:state'] ||
    tags['addr:region'] ||
    tags['is_in'] ||
    null;
  if (r) return String(r).substring(0, 100);
  if (lon >= 32.2 && lat <= 31.8 && lat >= 21.5) return 'Red Sea';
  if (lat >= 30.8 && lon <= 30.0) return 'Mediterranean Sea';
  return 'Egypt';
}

function mapSiteTypes(tags) {
  const out = [];
  if (tags.natural === 'reef') out.push('reef');
  if (tags.historic === 'wreck') out.push('wreck');
  if (tags.natural === 'cave_entrance') out.push('cave');
  if (tags.leisure === 'diving' || tags.sport === 'scuba_diving') {
    if (tags['dive:type']) {
      tags['dive:type']
        .split(/[;,]/)
        .map((t) => t.trim().toLowerCase())
        .forEach((t) => {
          if (['reef', 'wreck', 'wall', 'cave', 'drift', 'shore', 'boat'].includes(t))
            out.push(t);
        });
    }
  }
  if (out.length === 0) {
    if (tags.historic === 'wreck') out.push('wreck');
    else out.push('reef');
  }
  return [...new Set(out)];
}

function mapDifficulty(tags) {
  const d = (tags['dive:difficulty'] || '').toLowerCase();
  if (!d) return 2;
  if (d.includes('beginner') || d.includes('easy') || d === '1') return 1;
  if (d.includes('intermediate') || d.includes('medium') || d === '2') return 2;
  if (d.includes('advanced') || d.includes('hard') || d === '3') return 3;
  if (d.includes('expert') || d === '4') return 4;
  return 2;
}

/**
 * Парсинг глубины из типичных тегов OSM (метры).
 * Источники: https://wiki.openstreetmap.org/wiki/Key:dive
 */
function parseDepthString(raw) {
  if (raw == null || raw === '') return null;
  const s = String(raw).replace(/,/g, '.').replace(/\s*m\b/gi, '').trim();
  const rangeTo = s.match(
    /(\d+(?:\.\d+)?)\s+to\s+(\d+(?:\.\d+)?)/i
  );
  if (rangeTo) {
    const a = parseFloat(rangeTo[1]);
    const b = parseFloat(rangeTo[2]);
    if (!Number.isNaN(a) && !Number.isNaN(b)) {
      const lo = Math.round(Math.min(a, b));
      const hi = Math.round(Math.max(a, b));
      return { depthMin: lo, depthMax: hi };
    }
  }
  const rangeDash = s.match(/(\d+(?:\.\d+)?)\s*[-–—]\s*(\d+(?:\.\d+)?)/);
  if (rangeDash) {
    const a = parseFloat(rangeDash[1]);
    const b = parseFloat(rangeDash[2]);
    const lo = Math.round(Math.min(a, b));
    const hi = Math.round(Math.max(a, b));
    return { depthMin: lo, depthMax: hi };
  }
  const single = s.match(/(\d+(?:\.\d+)?)/);
  if (single) {
    const v = Math.round(parseFloat(single[1]));
    if (!Number.isNaN(v)) {
      return { depthMin: null, depthMax: v };
    }
  }
  return null;
}

function mapDepth(tags) {
  let depthMin = null;
  let depthMax = null;

  const keyOrder = [
    'dive:depth',
    'maxdepth',
    'depth',
    'seamark:wreck:depth',
    'seamark:obstruction:depth',
    'wreck:depth',
    'source:maxdepth',
  ];

  for (const k of keyOrder) {
    const v = tags[k];
    if (v == null || v === '') continue;
    const p = parseDepthString(v);
    if (!p) continue;
    if (p.depthMin != null && p.depthMax != null) {
      depthMin = p.depthMin;
      depthMax = p.depthMax;
      break;
    }
    if (p.depthMax != null) {
      depthMax = p.depthMax;
    }
  }

  if (depthMin == null) {
    const minRaw = tags['dive:mindepth'] || tags['mindepth'];
    if (minRaw != null && minRaw !== '') {
      const m = parseDepthString(minRaw);
      if (m && (m.depthMax != null || m.depthMin != null)) {
        depthMin = m.depthMin != null ? m.depthMin : m.depthMax;
      }
    }
  }

  if (depthMin != null && depthMax != null && depthMin > depthMax) {
    const t = depthMin;
    depthMin = depthMax;
    depthMax = t;
  }

  return { depthMin, depthMax };
}

function toImportRecord(el) {
  const tags = el.tags || {};
  if (!tags.name || !String(tags.name).trim()) return null;

  let lat;
  let lon;
  if (el.type === 'node') {
    lat = el.lat;
    lon = el.lon;
  } else if (el.center) {
    lat = el.center.lat;
    lon = el.center.lon;
  } else {
    return null;
  }
  if (
    lat == null ||
    lon == null ||
    Number.isNaN(lat) ||
    Number.isNaN(lon) ||
    Math.abs(lat) > 90 ||
    Math.abs(lon) > 180
  ) {
    return null;
  }

  const { depthMin, depthMax } = mapDepth(tags);
  const desc =
    tags.description ||
    tags['description:en'] ||
    tags['description:ru'] ||
    '';

  return {
    name: String(tags.name).trim().substring(0, 255),
    description: String(desc).substring(0, 5000),
    latitude: parseFloat(lat.toFixed(6)),
    longitude: parseFloat(lon.toFixed(6)),
    country: 'Egypt',
    region: inferRegion(lat, lon, tags),
    siteTypes: mapSiteTypes(tags),
    difficulty: mapDifficulty(tags),
    depthMin,
    depthMax,
    marineLife: [],
    source: 'OpenStreetMap',
    osmId: `${el.type}/${el.id}`,
  };
}

async function main() {
  const merged = [];
  const seen = new Set();

  function ingest(data) {
    if (!data || !data.elements) return;
    for (const el of data.elements) {
      const row = toImportRecord(el);
      if (!row) continue;
      const key = `${row.name.toLowerCase()}|${row.latitude.toFixed(4)}|${row.longitude.toFixed(4)}`;
      if (seen.has(key)) continue;
      seen.add(key);
      merged.push(row);
    }
  }

  console.log('Каркас (дайвинг, обломки, пещеры) по плиткам…');
  for (let i = 0; i < CORE_TILES.length; i++) {
    const q = buildCoreQuery(CORE_TILES[i]);
    try {
      const data = await runQuery(q, `core ${i + 1}/${CORE_TILES.length}`);
      ingest(data);
    } catch (e) {
      console.error(`  Ошибка плитки core ${i + 1}: ${e.message}`);
    }
    if (i < CORE_TILES.length - 1) {
      await new Promise((r) => setTimeout(r, 5000));
    }
  }

  console.log('\nРифы (natural=reef) по Красному морю…');
  for (let i = 0; i < REEF_TILES.length; i++) {
    const q = buildReefQuery(REEF_TILES[i]);
    try {
      const data = await runQuery(q, `reef ${i + 1}/${REEF_TILES.length}`);
      ingest(data);
    } catch (e) {
      console.error(`  Ошибка плитки reef ${i + 1}: ${e.message}`);
    }
    if (i < REEF_TILES.length - 1) {
      await new Promise((r) => setTimeout(r, 5000));
    }
  }

  merged.sort((a, b) => a.name.localeCompare(b.name));
  fs.writeFileSync(OUTPUT, JSON.stringify(merged, null, 2), 'utf8');
  console.log(`\n✅ Уникальных: ${merged.length}`);
  console.log(`   ${OUTPUT}`);
}

if (require.main === module) {
  main().catch((e) => {
    console.error(e);
    process.exit(1);
  });
}

module.exports = {
  mapDepth,
  parseDepthString,
  USER_AGENT,
  OUTPUT,
};
