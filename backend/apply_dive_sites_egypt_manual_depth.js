#!/usr/bin/env node
/**
 * Подставляет глубину из ручного справочника (поиск по названию / по osmId).
 *
 * Файл: dive_sites_egypt_depth_overrides.json
 *   — byExactName: ключ = точное поле name как в dive_sites_egypt_osm.json
 *   — byOsmId: приоритетнее, если несколько объектов с одним именем
 *
 *   node apply_dive_sites_egypt_manual_depth.js
 */

const fs = require('fs');
const path = require('path');

const SITES = path.join(__dirname, 'dive_sites_egypt_osm.json');
const OVERRIDES = path.join(__dirname, 'dive_sites_egypt_depth_overrides.json');

function normName(s) {
  return String(s || '')
    .trim()
    .replace(/\s+/g, ' ')
    .toLowerCase();
}

function loadOverrides() {
  if (!fs.existsSync(OVERRIDES)) {
    console.error('Нет файла:', OVERRIDES);
    process.exit(1);
  }
  return JSON.parse(fs.readFileSync(OVERRIDES, 'utf8'));
}

function pickDepth(site, ov) {
  if (ov.byOsmId && ov.byOsmId[site.osmId]) {
    return ov.byOsmId[site.osmId];
  }
  if (ov.byExactName && Object.prototype.hasOwnProperty.call(ov.byExactName, site.name)) {
    return ov.byExactName[site.name];
  }
  const nn = normName(site.name);
  if (ov.byNormalizedName) {
    for (const [k, v] of Object.entries(ov.byNormalizedName)) {
      if (normName(k) === nn) return v;
    }
  }
  return null;
}

function main() {
  const sites = JSON.parse(fs.readFileSync(SITES, 'utf8'));
  const ov = loadOverrides();

  let n = 0;
  for (const site of sites) {
    const d = pickDepth(site, ov);
    if (!d || (d.depthMin == null && d.depthMax == null)) continue;
    site.depthMin =
      d.depthMin != null ? Number(d.depthMin) : site.depthMin ?? null;
    site.depthMax =
      d.depthMax != null ? Number(d.depthMax) : site.depthMax ?? null;
    n++;
  }

  fs.writeFileSync(SITES, JSON.stringify(sites, null, 2), 'utf8');
  console.log(`✅ Обновлено записей с глубиной из справочника: ${n} / ${sites.length}`);
  console.log(`   ${SITES}`);
}

main();
