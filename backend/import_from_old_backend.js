#!/usr/bin/env node
/**
 * Импорт дайвсайтов (и опционально дайвцентров из встроенного списка) из данных старого бэкенда.
 *
 * Дайвсайты — из файла (не хардкод пути):
 *   node import_from_old_backend.js ./path/to/dive-sites.json
 *   node import_from_old_backend.js ./path/to/dive-sites-data.js
 *   DIVESITES_DATA_PATH=./data.json node import_from_old_backend.js
 *
 * Формат файла: JSON-массив объектов или CommonJS module.exports = [ ... ]
 * Поля (гибко): name, description, latitude/longitude или lat/lng, country, region,
 *   diveTypes|siteTypes → site_types, difficultyLevel, depthMin/depthMax, averageRating, reviewCount
 *
 * БД: DB_HOST, DB_PORT, DB_DATABASE, DB_USERNAME, DB_PASSWORD (как в других скриптах).
 */
const fs = require('fs');
const path = require('path');
const { Pool } = require('pg');

function loadEnvFile() {
  try {
    const envFile = fs.readFileSync(path.join(__dirname, '.env'), 'utf8');
    envFile.split('\n').forEach((line) => {
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith('#')) return;
      const eq = trimmed.indexOf('=');
      if (eq <= 0) return;
      const key = trimmed.slice(0, eq).trim();
      const value = trimmed.slice(eq + 1).trim();
      if (key && value && process.env[key] === undefined) {
        process.env[key] = value.replace(/^["']|["']$/g, '');
      }
    });
  } catch {
    // no .env
  }
}

loadEnvFile();

function loadDiveSitesData(filePath) {
  const resolved = path.isAbsolute(filePath)
    ? filePath
    : path.join(process.cwd(), filePath);

  if (!fs.existsSync(resolved)) {
    throw new Error(`Файл не найден: ${resolved}`);
  }

  const ext = path.extname(resolved).toLowerCase();
  if (ext === '.json') {
    const raw = JSON.parse(fs.readFileSync(resolved, 'utf8'));
    return Array.isArray(raw) ? raw : raw.diveSites || raw.sites || [];
  }

  if (ext === '.js' || ext === '.cjs') {
    delete require.cache[require.resolve(resolved)];
    const mod = require(resolved);
    if (Array.isArray(mod)) return mod;
    if (Array.isArray(mod.default)) return mod.default;
    if (Array.isArray(mod.diveSites)) return mod.diveSites;
    throw new Error(
      `${resolved}: ожидается module.exports = [...] или export массива в .default`,
    );
  }

  throw new Error(`Неподдерживаемое расширение: ${ext} (используйте .json или .js)`);
}

function normalizeSite(site) {
  const lat = site.latitude ?? site.lat;
  const lng = site.longitude ?? site.lng;
  const siteTypes =
    site.siteTypes ||
    site.site_types ||
    site.diveTypes ||
    site.dive_types ||
    [];
  const typesArr = Array.isArray(siteTypes) ? siteTypes : [];

  return {
    name: site.name,
    description: site.description || '',
    latitude: lat != null ? Number(lat) : null,
    longitude: lng != null ? Number(lng) : null,
    country: site.country || null,
    region: site.region || null,
    diveTypes: typesArr,
    difficultyLevel: site.difficultyLevel ?? site.difficulty_level ?? 1,
    depthMin: site.depthMin ?? site.depth_min ?? null,
    depthMax: site.depthMax ?? site.depth_max ?? null,
    averageRating: site.averageRating ?? site.average_rating ?? 0,
    reviewCount: site.reviewCount ?? site.review_count ?? 0,
  };
}

const pool = new Pool({
  host: process.env.DB_HOST || 'localhost',
  port: parseInt(process.env.DB_PORT || '5432', 10),
  database: process.env.DB_DATABASE || 'divehub',
  user: process.env.DB_USERNAME || process.env.DB_USER || 'admin',
  password: process.env.DB_PASSWORD || 'admin123',
});

async function importDiveSites(diveSitesData) {
  const normalized = diveSitesData.map(normalizeSite).filter((s) => {
    if (!s.name || s.latitude == null || s.longitude == null) return false;
    if (Number.isNaN(s.latitude) || Number.isNaN(s.longitude)) return false;
    if (Math.abs(s.latitude) > 90 || Math.abs(s.longitude) > 180) return false;
    return true;
  });

  console.log(`📥 Импорт ${normalized.length} валидных дайвсайтов из файла...\n`);

  console.log('🔍 Проверка существующих дайвсайтов...');
  const existingSites = await pool.query(
    `SELECT name, latitude, longitude FROM dive_sites`,
  );

  const existingSet = new Set(
    existingSites.rows.map(
      (row) => `${row.name}|${row.latitude}|${row.longitude}`,
    ),
  );

  console.log(`   Уже в БД: ${existingSites.rows.length}\n`);

  const sitesToImport = normalized.filter((site) => {
    const key = `${site.name}|${site.latitude}|${site.longitude}`;
    return !existingSet.has(key);
  });

  console.log(`📦 К импорту: ${sitesToImport.length} новых\n`);

  if (sitesToImport.length === 0) {
    console.log('✅ Новых дайвсайтов нет.\n');
    return 0;
  }

  let imported = 0;
  let errors = 0;

  const batchSize = 20;
  const totalBatches = Math.ceil(sitesToImport.length / batchSize);

  for (let batchIndex = 0; batchIndex < totalBatches; batchIndex++) {
    const start = batchIndex * batchSize;
    const end = Math.min(start + batchSize, sitesToImport.length);
    const batch = sitesToImport.slice(start, end);

    console.log(
      `📦 Батч ${batchIndex + 1}/${totalBatches} (${start + 1}-${end} из ${sitesToImport.length})...`,
    );

    const values = [];
    const params = [];
    let paramIndex = 1;

    for (const site of batch) {
      values.push(`(
        $${paramIndex++}, $${paramIndex++}, ST_SetSRID(ST_MakePoint($${paramIndex++}, $${paramIndex++}), 4326)::geography,
        $${paramIndex++}, $${paramIndex++}, $${paramIndex++}, $${paramIndex++}, $${paramIndex++}, $${paramIndex++}, $${paramIndex++}, $${paramIndex++}, true
      )`);

      params.push(
        site.name,
        site.description || '',
        site.longitude,
        site.latitude,
        site.country || null,
        site.region || null,
        site.diveTypes || [],
        site.difficultyLevel || 1,
        site.depthMin || null,
        site.depthMax || null,
        site.averageRating || 0,
        site.reviewCount || 0,
      );
    }

    try {
      const query = `
        INSERT INTO dive_sites (
          name, description, location, country, region,
          site_types, difficulty_level, depth_min, depth_max,
          average_rating, review_count, is_active
        ) VALUES ${values.join(', ')}
        RETURNING id
      `;

      const result = await pool.query(query, params);
      imported += result.rows.length;
      console.log(`  ✅ Импортировано ${result.rows.length} дайвсайтов`);
    } catch (error) {
      errors += batch.length;
      console.error(`  ❌ Ошибка при импорте батча:`, error.message);
      for (const site of batch) {
        try {
          await pool.query(
            `INSERT INTO dive_sites (
              name, description, location, country, region,
              site_types, difficulty_level, depth_min, depth_max,
              average_rating, review_count, is_active
            ) VALUES (
              $1, $2, ST_SetSRID(ST_MakePoint($3, $4), 4326)::geography,
              $5, $6, $7, $8, $9, $10, $11, $12, true
            ) RETURNING id`,
            [
              site.name,
              site.description || '',
              site.longitude,
              site.latitude,
              site.country || null,
              site.region || null,
              site.diveTypes || [],
              site.difficultyLevel || 1,
              site.depthMin || null,
              site.depthMax || null,
              site.averageRating || 0,
              site.reviewCount || 0,
            ],
          );
          imported++;
          errors--;
        } catch (individualError) {
          console.error(
            `    ❌ Ошибка при импорте "${site.name}":`,
            individualError.message,
          );
        }
      }
    }
  }

  const skipped = normalized.length - sitesToImport.length;
  console.log(
    `\n✅ Импорт дайвсайтов: ${imported} новых, ${skipped} уже были в БД, ${errors} ошибок (батчей)\n`,
  );
  return imported;
}

async function importDiveCenters() {
  console.log('📥 Импорт дайвцентров (встроенный демо-список)...\n');

  const diveCenters = [
    {
      name: 'Blue Ocean Dive Center',
      description:
        'Professional dive center with certified instructors and modern equipment',
      email: 'info@blueoceandive.com',
      phone: '+52-998-123-4567',
      website: 'https://blueoceandive.com',
      address: '123 Beach Road, Cancun, Mexico',
      latitude: 20.425,
      longitude: -86.9215,
      country: 'Mexico',
      city: 'Cancun',
      services: [
        'courses',
        'equipment rental',
        'guided dives',
        'night dives',
        'wreck dives',
      ],
      averageRating: 4.7,
      reviewCount: 45,
    },
    {
      name: 'Coral Reef Dive Center',
      description: 'Eco-friendly dive center specializing in reef conservation',
      email: 'info@coralreef.com',
      phone: '+501-223-4567',
      website: 'https://coralreef.com',
      address: '456 Ocean Drive, Ambergris Caye, Belize',
      latitude: 17.9167,
      longitude: -87.95,
      country: 'Belize',
      city: 'Ambergris Caye',
      services: ['courses', 'equipment rental', 'guided dives', 'reef tours'],
      averageRating: 4.8,
      reviewCount: 67,
    },
  ];

  let imported = 0;
  let skipped = 0;
  let errors = 0;

  for (const center of diveCenters) {
    try {
      const existing = await pool.query(
        `SELECT id FROM dive_centers WHERE email = $1 OR (name = $2 AND latitude = $3 AND longitude = $4)`,
        [center.email, center.name, center.latitude, center.longitude],
      );

      if (existing.rows.length > 0) {
        skipped++;
        console.log(`  ⏭️  Пропущен: ${center.name}`);
        continue;
      }

      await pool.query(
        `INSERT INTO dive_centers (
          name, description, location, country, city, address,
          email, phone, website, services,
          average_rating, review_count, is_active
        ) VALUES (
          $1, $2, ST_SetSRID(ST_MakePoint($3, $4), 4326)::geography,
          $5, $6, $7, $8, $9, $10, $11, $12, $13, true
        ) RETURNING id`,
        [
          center.name,
          center.description || '',
          center.longitude,
          center.latitude,
          center.country || null,
          center.city || null,
          center.address || null,
          center.email || null,
          center.phone || null,
          center.website || null,
          center.services || [],
          center.averageRating || 0,
          center.reviewCount || 0,
        ],
      );

      imported++;
      console.log(`  ✅ Импортирован: ${center.name}`);
    } catch (error) {
      errors++;
      console.error(`  ❌ Ошибка "${center.name}":`, error.message);
    }
  }

  console.log(
    `\n✅ Дайвцентры: ${imported} новых, ${skipped} пропущено, ${errors} ошибок\n`,
  );
  return imported;
}

async function main() {
  const dataPath = process.argv[2] || process.env.DIVESITES_DATA_PATH;

  if (!dataPath) {
    console.error(`Укажите файл с дайвсайтами:
  node import_from_old_backend.js ./dive-sites.json
или:
  DIVESITES_DATA_PATH=./dive-sites.json node import_from_old_backend.js

Опционально без импорта дайвсайтов — только демо-центры (не рекомендуется):
  node import_from_old_backend.js --centers-only
`);
    process.exit(1);
  }

  let diveSitesData = [];
  if (dataPath !== '--centers-only') {
    diveSitesData = loadDiveSitesData(dataPath);
    if (!Array.isArray(diveSitesData)) {
      console.error('Ожидался массив дайвсайтов');
      process.exit(1);
    }
  }

  try {
    await pool.query('SELECT 1');
    console.log('🔌 Подключено к PostgreSQL\n');

    let sitesImported = 0;
    if (dataPath !== '--centers-only') {
      sitesImported = await importDiveSites(diveSitesData);
    }
    const centersImported = await importDiveCenters();

    console.log('📊 Итоги:');
    console.log(`   Дайвсайтов (новых): ${sitesImported}`);
    console.log(`   Дайвцентров (новых): ${centersImported}`);
  } catch (error) {
    console.error('❌ Ошибка:', error);
    process.exit(1);
  } finally {
    await pool.end();
  }
}

main();
