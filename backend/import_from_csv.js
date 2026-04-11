// Script to import dive sites from CSV file
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

// Parse CSV file
function parseCSV(filePath) {
  const content = fs.readFileSync(filePath, 'utf8');
  const lines = content.split('\n').filter(line => line.trim());
  
  if (lines.length === 0) {
    return [];
  }
  
  // Parse header
  const header = lines[0].split(',').map(h => h.trim().replace(/^"|"$/g, ''));
  
  // Find column indices
  const nameIdx = header.findIndex(h => h.includes('Название') || h.includes('name'));
  const latIdx = header.findIndex(h => h.includes('Широта') || h.includes('lat'));
  const lngIdx = header.findIndex(h => h.includes('Долгота') || h.includes('lon') || h.includes('lng'));
  const minDepthIdx = header.findIndex(h => h.includes('МинГлубина') || h.includes('min_depth'));
  const maxDepthIdx = header.findIndex(h => h.includes('МаксГлубина') || h.includes('max_depth'));
  const avgDepthIdx = header.findIndex(h => h.includes('СрГлубина') || h.includes('avg_depth'));
  const typeIdx = header.findIndex(h => h.includes('Тип') || h.includes('type'));
  const difficultyIdx = header.findIndex(h => h.includes('Сложность') || h.includes('difficulty'));
  const environmentIdx = header.findIndex(h => h.includes('Среда') || h.includes('environment'));
  
  const sites = [];
  
  // Parse data rows
  for (let i = 1; i < lines.length; i++) {
    const line = lines[i];
    if (!line.trim()) continue;
    
    // Simple CSV parsing (handles quoted values)
    const values = [];
    let current = '';
    let inQuotes = false;
    
    for (let j = 0; j < line.length; j++) {
      const char = line[j];
      if (char === '"') {
        inQuotes = !inQuotes;
      } else if (char === ',' && !inQuotes) {
        values.push(current.trim());
        current = '';
      } else {
        current += char;
      }
    }
    values.push(current.trim());
    
    if (values.length < 3) continue; // Skip invalid rows
    
    const name = values[nameIdx]?.replace(/^"|"$/g, '') || 'Unknown';
    const lat = parseFloat(values[latIdx]);
    const lng = parseFloat(values[lngIdx]);
    
    if (isNaN(lat) || isNaN(lng)) continue;
    
    const minDepth = values[minDepthIdx] ? parseFloat(values[minDepthIdx]) : null;
    const maxDepth = values[maxDepthIdx] ? parseFloat(values[maxDepthIdx]) : null;
    const avgDepth = values[avgDepthIdx] ? parseFloat(values[avgDepthIdx]) : null;
    
    // Map type from Russian to English
    const typeMap = {
      'Обломок': 'wreck',
      'Риф': 'reef',
      'Пещера': 'cave',
      'Другое': 'reef',
    };
    const typeStr = values[typeIdx]?.replace(/^"|"$/g, '') || 'reef';
    const siteType = typeMap[typeStr] || typeStr.toLowerCase();
    
    // Map difficulty from Russian to English
    const difficultyMap = {
      'Новичок': 1,
      'Средний': 2,
      'Продвинутый': 3,
      'Эксперт': 4,
    };
    const difficultyStr = values[difficultyIdx]?.replace(/^"|"$/g, '') || 'beginner';
    const difficulty = difficultyMap[difficultyStr] || 1;
    
    // Map environment
    const environment = values[environmentIdx]?.replace(/^"|"$/g, '') || 'saltwater';
    
    sites.push({
      name: name,
      latitude: lat,
      longitude: lng,
      depthMin: minDepth || avgDepth || 5,
      depthMax: maxDepth || avgDepth || 30,
      siteType: siteType,
      difficulty: difficulty,
      environment: environment,
    });
  }
  
  return sites;
}

// Import sites to database
async function importFromCSV(csvPath) {
  console.log('🌊 Импорт дайвсайтов из CSV...\n');
  console.log(`📄 Файл: ${csvPath}\n`);
  
  if (!fs.existsSync(csvPath)) {
    console.error(`❌ Файл не найден: ${csvPath}`);
    process.exit(1);
  }
  
  try {
    await pool.query('SELECT 1');
    console.log('✅ Подключение к базе данных успешно\n');
  } catch (error) {
    console.error('❌ Ошибка подключения:', error.message);
    process.exit(1);
  }
  
  // Parse CSV
  console.log('📖 Парсинг CSV файла...');
  const sites = parseCSV(csvPath);
  console.log(`✅ Прочитано ${sites.length} дайвсайтов\n`);
  
  if (sites.length === 0) {
    console.log('⚠️  Нет данных для импорта\n');
    await pool.end();
    return;
  }
  
  // Check existing sites
  const existingResult = await pool.query(`
    SELECT id, name, latitude, longitude, location 
    FROM dive_sites
  `);
  
  const existingSites = existingResult.rows.map(row => ({
    id: row.id,
    name: row.name,
    lat: row.latitude,
    lng: row.longitude,
    location: row.location
  }));
  
  console.log(`🔍 Найдено ${existingSites.length} существующих дайвсайтов\n`);
  
  // Filter new sites (check by distance)
  console.log('🔍 Проверка дубликатов...');
  const sitesToImport = [];
  
  for (const site of sites) {
    let isDuplicate = false;
    
    for (const existing of existingSites) {
      if (existing.location && site.latitude && site.longitude) {
        // Check distance using PostGIS
        const distanceResult = await pool.query(`
          SELECT ST_Distance(
            ST_SetSRID(ST_MakePoint($1, $2), 4326)::geography,
            $3
          ) as distance
        `, [site.longitude, site.latitude, existing.location]);
        
        const distance = distanceResult.rows[0].distance;
        
        // If same name and within 10 meters, it's a duplicate
        if (existing.name.toLowerCase().trim() === site.name.toLowerCase().trim() && distance < 10) {
          isDuplicate = true;
          break;
        }
      }
    }
    
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
  
  // Import sites
  let imported = 0;
  let errors = 0;
  
  console.log(`🚀 Импорт дайвсайтов...\n`);
  
  for (let i = 0; i < sitesToImport.length; i++) {
    const site = sitesToImport[i];
    
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
        `${site.name} is a dive site located at ${site.latitude.toFixed(4)}, ${site.longitude.toFixed(4)}.`,
        site.longitude,
        site.latitude,
        null, // country - will be determined later
        null, // region
        null, // address
        [site.siteType || 'reef'], // site_types
        site.difficulty || 2, // difficulty_level
        site.depthMin || 5, // depth_min
        site.depthMax || 30, // depth_max
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
      
      if ((i + 1) % 100 === 0) {
        console.log(`   ✅ Импортировано ${i + 1}/${sitesToImport.length}...`);
      }
    } catch (error) {
      errors++;
      if (errors <= 10) {
        console.error(`   ❌ Ошибка при импорте "${site.name}": ${error.message}`);
      }
    }
  }
  
  console.log(`\n✅ Импорт завершен!`);
  console.log(`   Импортировано: ${imported}`);
  console.log(`   Ошибок: ${errors}\n`);
  
  // Verify
  const verifyResult = await pool.query('SELECT COUNT(*) as count FROM dive_sites');
  console.log(`📊 Всего дайвсайтов в базе: ${verifyResult.rows[0].count}\n`);
  
  await pool.end();
}

// Main
const csvPath = process.argv[2] || path.join(__dirname, 'dive_sites_10000.csv');

importFromCSV(csvPath).catch(error => {
  console.error('❌ Ошибка:', error);
  process.exit(1);
});
