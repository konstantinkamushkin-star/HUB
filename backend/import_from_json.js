// Script to import dive sites from JSON files into database
// Supports multiple JSON files with dive sites
const { Pool } = require('pg');
const fs = require('fs');
const path = require('path');

const dbConfig = {
  host: process.env.DB_HOST || 'localhost',
  port: parseInt(process.env.DB_PORT || '5432', 10),
  database: process.env.DB_DATABASE || 'divehub',
  user: process.env.DB_USERNAME || 'admin',
  password: process.env.DB_PASSWORD || 'admin123',
};

const pool = new Pool(dbConfig);

// JSON files to import (in order of priority)
const JSON_FILES = [
  'dive_sites_extended_2000.json', // Extended list
  'dive_sites_combined.json', // Combined from all sources
  'dive_sites_all_sources.json',
  'dive_sites_from_existing.json', // From import_dive_sites_worldwide.js
  'dive_sites_comprehensive.json',
  'dive_sites_from_sql.json',
  'dive_sites_osm.json',
  // Add more files as needed
];

function loadDiveSitesFromFile(filePath) {
  if (!fs.existsSync(filePath)) {
    console.log(`⚠️  Файл не найден: ${filePath}`);
    return [];
  }
  
  try {
    const data = fs.readFileSync(filePath, 'utf8');
    const sites = JSON.parse(data);
    console.log(`✅ Загружено ${sites.length} дайвсайтов из ${path.basename(filePath)}`);
    return Array.isArray(sites) ? sites : [];
  } catch (error) {
    console.error(`❌ Ошибка чтения ${filePath}: ${error.message}`);
    return [];
  }
}

function normalizeSite(site) {
  return {
    name: site.name || 'Unnamed',
    lat: parseFloat(site.lat) || parseFloat(site.latitude) || 0,
    lng: parseFloat(site.lng) || parseFloat(site.longitude) || 0,
    country: site.country || null,
    region: site.region || null,
    siteTypes: Array.isArray(site.siteTypes) ? site.siteTypes : (site.siteTypes ? [site.siteTypes] : ['reef']),
    difficulty: site.difficulty || site.difficulty_level || 2,
    depthMin: site.depthMin || site.depth_min || 5,
    depthMax: site.depthMax || site.depth_max || 30,
    description: site.description || null,
    source: site.source || 'Unknown',
  };
}

async function importDiveSites() {
  console.log('🌊 Импорт дайвсайтов из JSON файлов...\n');
  
  try {
    await pool.query('SELECT 1');
    console.log('✅ Подключение к базе данных успешно\n');
  } catch (error) {
    console.error('❌ Ошибка подключения:', error.message);
    process.exit(1);
  }
  
  // Load all sites from JSON files
  const allSites = [];
  for (const file of JSON_FILES) {
    const filePath = path.join(__dirname, file);
    const sites = loadDiveSitesFromFile(filePath);
    allSites.push(...sites.map(normalizeSite));
  }
  
  if (allSites.length === 0) {
    console.log('❌ Нет дайвсайтов для импорта');
    process.exit(1);
  }
  
  console.log(`\n📊 Всего загружено: ${allSites.length} дайвсайтов\n`);
  
  // Check existing sites
  console.log('🔍 Проверка существующих дайвсайтов...');
  const existingResult = await pool.query(
    `SELECT id, name, latitude, longitude, location FROM dive_sites WHERE location IS NOT NULL`
  );
  console.log(`   Найдено ${existingResult.rows.length} существующих дайвсайтов\n`);
  
  // Filter new sites - check by distance using PostGIS (10 meters threshold)
  // This allows sites to be close to each other, but marks duplicates if same name and < 10m apart
  const sitesToImport = [];
  
  for (const site of allSites) {
    if (!site.lat || !site.lng) continue;
    
    // Check if there's an existing site with same name and within 10 meters
    const isDuplicate = await (async () => {
      for (const existing of existingResult.rows) {
        if (existing.name.toLowerCase().trim() === site.name.toLowerCase().trim()) {
          // Use PostGIS to calculate exact distance
          const distanceResult = await pool.query(`
            SELECT ST_Distance($1::geography, $2::geography) as distance
          `, [
            existing.location,
            `POINT(${site.lng} ${site.lat})`
          ]);
          
          const distance = distanceResult.rows[0].distance;
          if (distance < 10) { // 10 meters threshold
            return true;
          }
        }
      }
      return false;
    })();
    
    if (!isDuplicate) {
      sitesToImport.push(site);
    }
  }
  
  console.log(`📦 К импорту: ${sitesToImport.length} новых дайвсайтов\n`);
  
  if (sitesToImport.length === 0) {
    console.log('✅ Все дайвсайты уже импортированы!\n');
    await pool.end();
    return;
  }
  
  // Import in batches
  let imported = 0;
  const batchSize = 20; // Reduced batch size to avoid parameter limit
  const totalBatches = Math.ceil(sitesToImport.length / batchSize);
  
  console.log(`🚀 Импорт батчами по ${batchSize}...\n`);
  
  for (let i = 0; i < totalBatches; i++) {
    const batch = sitesToImport.slice(i * batchSize, (i + 1) * batchSize);
    
    // Import one by one to avoid parameter issues
    for (const site of batch) {
      try {
        const query = `
          INSERT INTO dive_sites (
            name, description, location, country, region, address,
            site_types, difficulty_level, depth_min, depth_max,
            water_temp_min, water_temp_max, seasonality, access_type, price_from,
            average_rating, review_count, photo_urls, video_urls, marine_life,
            is_active, ai_summary, affiliated_centers
          ) VALUES ($1, $2, ST_SetSRID(ST_MakePoint($3, $4), 4326)::geography, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16, $17, $18, $19, $20, $21, $22, $23, $24)
          ON CONFLICT DO NOTHING
        `;
        
        const values = [
          site.name,
          site.description || `${site.name} is a dive site located in ${site.region || 'the region'}, ${site.country || 'unknown country'}.`,
          site.lng,
          site.lat,
          site.country,
          site.region,
          null, // address
          site.siteTypes || ['reef'],
          site.difficulty || 2,
          site.depthMin || 5,
          site.depthMax || 30,
          null, null, null, // water temp, seasonality
          ['boat'], // access_type
          null, // price_from
          0, 0, // rating, review_count
          [], [], // photos, videos
          [], // marine_life
          true, // is_active
          null, // ai_summary
          [] // affiliated_centers
        ];
        
        await pool.query(query, values);
        imported++;
      } catch (error) {
        console.error(`   ❌ Ошибка при импорте "${site.name}": ${error.message}`);
      }
    }
    
    console.log(`[${i + 1}/${totalBatches}] ✅ Обработано ${batch.length} (импортировано: ${imported})`);
  }
  
  console.log(`\n✅ Импорт завершен! Импортировано: ${imported}\n`);
  
  // Verify
  const verifyResult = await pool.query(`SELECT COUNT(*) as count FROM dive_sites`);
  console.log(`📊 Всего дайвсайтов в базе: ${verifyResult.rows[0].count}\n`);
  
  await pool.end();
}

importDiveSites().catch(error => {
  console.error('❌ Ошибка:', error);
  process.exit(1);
});
