#!/usr/bin/env node
/**
 * Дозагрузка тегов OSM по osmId и пересчёт depthMin/depthMax через mapDepth()
 * (без полного повторного bbox-запроса).
 *
 *   node enrich_dive_sites_egypt_osm_depth.js
 */

const https = require('https');
const fs = require('fs');
const path = require('path');

const { mapDepth, USER_AGENT } = require('./fetch_dive_sites_osm_egypt.js');

const INPUT = path.join(__dirname, 'dive_sites_egypt_osm.json');

const OVERPASS_APIS = [
  'https://overpass.openstreetmap.fr/api/interpreter',
  'https://overpass-api.de/api/interpreter',
];

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
            reject(new Error(`HTTP ${res.statusCode}: ${data.slice(0, 200)}`));
            return;
          }
          try {
            resolve(JSON.parse(data));
          } catch (e) {
            reject(e);
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

function buildIdQuery(parts) {
  const body = parts.map(({ type, id }) => `  ${type}(${id});`).join('\n');
  return `[out:json][timeout:90];
(
${body}
);
out tags;`;
}

function chunk(arr, size) {
  const out = [];
  for (let i = 0; i < arr.length; i += size) {
    out.push(arr.slice(i, i + size));
  }
  return out;
}

async function fetchTagsBatch(parts) {
  const q = buildIdQuery(parts);
  let lastErr = null;
  for (const url of OVERPASS_APIS) {
    try {
      return await postOverpass(url, q);
    } catch (e) {
      lastErr = e;
    }
  }
  throw lastErr;
}

async function main() {
  if (!fs.existsSync(INPUT)) {
    console.error('Нет файла:', INPUT);
    process.exit(1);
  }

  const sites = JSON.parse(fs.readFileSync(INPUT, 'utf8'));
  const parsed = sites.map((s) => {
    const m = String(s.osmId || '').match(/^(node|way|relation)\/(\d+)$/);
    if (!m) return null;
    return { site: s, type: m[1], id: parseInt(m[2], 10) };
  });

  const valid = parsed.filter(Boolean);
  const batches = chunk(valid, 25);

  const tagByKey = new Map();
  for (let i = 0; i < batches.length; i++) {
    const b = batches[i];
    process.stdout.write(`batch ${i + 1}/${batches.length} … `);
    const parts = b.map((x) => ({ type: x.type, id: x.id }));
    try {
      const data = await fetchTagsBatch(parts);
      const n = data.elements ? data.elements.length : 0;
      console.log(`${n} объектов`);
      for (const el of data.elements || []) {
        tagByKey.set(`${el.type}/${el.id}`, el.tags || {});
      }
    } catch (e) {
      console.log(`fail: ${e.message}`);
    }
    if (i < batches.length - 1) {
      await new Promise((r) => setTimeout(r, 4000));
    }
  }

  let withDepth = 0;
  for (const s of sites) {
    const tags = tagByKey.get(s.osmId) || {};
    const d = mapDepth(tags);
    s.depthMin = d.depthMin;
    s.depthMax = d.depthMax;
    if (d.depthMin != null || d.depthMax != null) withDepth++;
  }

  fs.writeFileSync(INPUT, JSON.stringify(sites, null, 2), 'utf8');
  console.log(`\n✅ Записано: ${INPUT}`);
  console.log(`   С глубиной (min и/или max): ${withDepth} из ${sites.length}`);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
