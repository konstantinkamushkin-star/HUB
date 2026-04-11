// Script to import processed dive sites into PostgreSQL database
// Handles deduplication, coordinate validation, and data mapping
const { Pool } = require('pg');
const fs = require('fs');
const path = require('path');

function loadEnvFile() {
  try {
    const envFile = fs.readFileSync(path.join(__dirname, '.env'), 'utf8');
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

// На хосте VPS используйте DB_HOST=127.0.0.1, не имя сервиса docker «postgres».
// Database connection configuration
const dbConfig = {
  host: process.env.DB_HOST || 'localhost',
  port: parseInt(process.env.DB_PORT || '5432', 10),
  database: process.env.DB_DATABASE || 'divehub',
  user: process.env.DB_USERNAME || process.env.DB_USER || 'postgres',
  // Как в docker-compose.yml и .env.example; в production задайте в .env.
  password: process.env.DB_PASSWORD || 'postgres',
};

const pool = new Pool(dbConfig);

// Map site types to database format
function mapSiteTypes(siteTypes) {
  if (!siteTypes || !Array.isArray(siteTypes) || siteTypes.length === 0) {
    return ['reef']; // Default
  }
  
  // Valid types: reef, wreck, wall, cave, drift, shore, boat, other
  const validTypes = ['reef', 'wreck', 'wall', 'cave', 'drift', 'shore', 'boat', 'other'];
  const mapped = siteTypes
    .map(t => t.toLowerCase().trim())
    .filter(t => validTypes.includes(t));
  
  return mapped.length > 0 ? mapped : ['reef'];
}

// Map difficulty to database format (1-4)
function mapDifficulty(difficulty) {
  if (difficulty === null || difficulty === undefined) {
    return 1; // Default: beginner
  }
  
  const diff = parseInt(difficulty);
  if (isNaN(diff) || diff < 1 || diff > 4) {
    return 1; // Default: beginner
  }
  
  return diff;
}

// Prepare dive site for database insertion
function prepareDiveSite(site) {
  const lat = site.lat || site.latitude;
  const lng = site.lng || site.longitude;
  
  // Validate coordinates
  if (!lat || !lng || isNaN(lat) || isNaN(lng)) {
    return null;
  }
  if (Math.abs(lat) > 90 || Math.abs(lng) > 180) {
    return null;
  }
  
  return {
    name: (site.name || 'Unknown Dive Site').trim().substring(0, 255),
    description: (site.description || '').substring(0, 5000),
    latitude: parseFloat(lat.toFixed(6)),
    longitude: parseFloat(lng.toFixed(6)),
    country: (site.country || '').substring(0, 100),
    region: (site.region || '').substring(0, 100),
    address: site.address ? site.address.substring(0, 500) : null,
    siteTypes: mapSiteTypes(site.siteTypes),
    difficultyLevel: mapDifficulty(site.difficulty || site.difficultyLevel),
    depthMin: site.depthMin || site.minDepth || null,
    depthMax: site.depthMax || site.maxDepth || null,
    averageRating: site.averageRating || 0,
    reviewCount: site.reviewCount || 0,
    marineLife: site.marineLife && Array.isArray(site.marineLife) ? site.marineLife : [],
  };
}

// Check if dive site already exists (by coordinates)
async function siteExists(lat, lng, name) {
  const result = await pool.query(
    `SELECT id FROM dive_sites 
     WHERE ABS(latitude - $1) < 0.0001 
       AND ABS(longitude - $2) < 0.0001
       AND LOWER(name) = LOWER($3)
     LIMIT 1`,
    [lat, lng, name]
  );
  
  return result.rows.length > 0;
}

// Insert dive site into database
async function insertDiveSite(site) {
  // Use PostGIS location column directly (latitude/longitude are generated columns)
  const query = `
    INSERT INTO dive_sites (
      name, description, location,
      country, region, address,
      site_types, difficulty_level,
      depth_min, depth_max,
      average_rating, review_count,
      marine_life,
      is_active, created_at, updated_at
    ) VALUES (
      $1, $2, ST_SetSRID(ST_MakePoint($3, $4), 4326)::geography,
      $5, $6, $7,
      $8, $9,
      $10, $11,
      $12, $13,
      $14,
      true, NOW(), NOW()
    )
    RETURNING id, name
  `;
  
  const values = [
    site.name,
    site.description || null,
    site.longitude, // Note: PostGIS uses (longitude, latitude) order
    site.latitude,
    site.country || null,
    site.region || null,
    site.address || null,
    site.siteTypes,
    site.difficultyLevel,
    site.depthMin,
    site.depthMax,
    site.averageRating,
    site.reviewCount,
    site.marineLife,
  ];
  
  try {
    const result = await pool.query(query, values);
    return result.rows[0];
  } catch (error) {
    // Check if it's a duplicate key error (shouldn't happen with our check, but just in case)
    if (error.code === '23505') {
      return null; // Duplicate
    }
    throw error;
  }
}

// Import dive sites from JSON file
async function importDiveSites(inputFile, options = {}) {
  const {
    skipDuplicates = true,
    batchSize = 100,
    dryRun = false,
  } = options;
  
  console.log('🌊 ============================================');
  console.log('🌊 Импорт дайвсайтов в базу данных');
  console.log('🌊 ============================================\n');
  
  if (!fs.existsSync(inputFile)) {
    console.error(`❌ Файл не найден: ${inputFile}`);
    return null;
  }
  
  console.log(`📂 Загрузка данных из: ${inputFile}\n`);
  const data = JSON.parse(fs.readFileSync(inputFile, 'utf8'));
  const sites = Array.isArray(data) ? data : [];
  
  console.log(`📊 Найдено ${sites.length} дайвсайтов для импорта\n`);
  
  if (dryRun) {
    console.log('🔍 Режим проверки (dry run) - изменения не будут сохранены\n');
  }
  
  // Test database connection
  try {
    await pool.query('SELECT 1');
    console.log('✅ Подключение к базе данных установлено\n');
  } catch (error) {
    console.error('❌ Ошибка подключения к базе данных:', error.message);
    console.error('\n💡 Проверьте настройки подключения:');
    console.error('   DB_HOST, DB_PORT, DB_DATABASE, DB_USERNAME, DB_PASSWORD');
    process.exit(1);
  }
  
  const stats = {
    total: sites.length,
    prepared: 0,
    skipped: 0,
    inserted: 0,
    duplicates: 0,
    errors: 0,
    errorsList: [],
  };
  
  // Process in batches
  for (let i = 0; i < sites.length; i += batchSize) {
    const batch = sites.slice(i, i + batchSize);
    const batchNum = Math.floor(i / batchSize) + 1;
    const totalBatches = Math.ceil(sites.length / batchSize);
    
    console.log(`📦 Обработка батча ${batchNum}/${totalBatches} (${batch.length} дайвсайтов)...`);
    
    for (const site of batch) {
      try {
        // Prepare site
        const prepared = prepareDiveSite(site);
        if (!prepared) {
          stats.skipped++;
          continue;
        }
        
        stats.prepared++;
        
        // Check for duplicates
        if (skipDuplicates && !dryRun) {
          const exists = await siteExists(prepared.latitude, prepared.longitude, prepared.name);
          if (exists) {
            stats.duplicates++;
            continue;
          }
        }
        
        // Insert into database
        if (!dryRun) {
          const result = await insertDiveSite(prepared);
          if (result) {
            stats.inserted++;
          } else {
            stats.duplicates++;
          }
        } else {
          stats.inserted++; // Count as inserted in dry run
        }
      } catch (error) {
        stats.errors++;
        stats.errorsList.push({
          site: site.name || 'Unknown',
          error: error.message,
        });
        console.warn(`   ⚠️  Ошибка для "${site.name || 'Unknown'}": ${error.message}`);
      }
    }
    
    // Progress update
    const progress = ((i + batch.length) / sites.length * 100).toFixed(1);
    console.log(`   Прогресс: ${progress}% (подготовлено: ${stats.prepared}, вставлено: ${stats.inserted}, пропущено: ${stats.skipped})`);
  }
  
  console.log(`\n✅ Импорт завершен:\n`);
  console.log(`   Всего обработано: ${stats.total}`);
  console.log(`   Подготовлено: ${stats.prepared}`);
  console.log(`   Вставлено: ${stats.inserted}`);
  console.log(`   Дубликатов: ${stats.duplicates}`);
  console.log(`   Пропущено: ${stats.skipped}`);
  console.log(`   Ошибок: ${stats.errors}`);
  
  if (stats.errors > 0 && stats.errorsList.length > 0) {
    console.log(`\n   Первые ошибки:`);
    stats.errorsList.slice(0, 5).forEach(({ site, error }) => {
      console.log(`     ${site}: ${error}`);
    });
  }
  
  // Get final count from database
  if (!dryRun) {
    try {
      const countResult = await pool.query('SELECT COUNT(*) as count FROM dive_sites WHERE is_active = true');
      const totalInDb = parseInt(countResult.rows[0].count);
      console.log(`\n📊 Всего дайвсайтов в базе данных: ${totalInDb}`);
    } catch (error) {
      console.warn(`   ⚠️  Не удалось получить общее количество: ${error.message}`);
    }
  }
  
  return stats;
}

// Main function
async function main() {
  const args = process.argv.slice(2);
  
  if (args.length === 0) {
    console.log('Использование: node import_processed_dive_sites.js <input_file> [options]');
    console.log('');
    console.log('Примеры:');
    console.log('  node import_processed_dive_sites.js dive_sites_final.json');
    console.log('  node import_processed_dive_sites.js dive_sites_final.json --dry-run');
    console.log('  node import_processed_dive_sites.js dive_sites_final.json --no-skip-duplicates');
    console.log('');
    console.log('Опции:');
    console.log('  --dry-run              Проверка без сохранения в БД');
    console.log('  --no-skip-duplicates   Импортировать дубликаты');
    console.log('  --batch-size=N         Размер батча (по умолчанию: 100)');
    process.exit(1);
  }
  
  const inputFile = args[0];
  const options = {
    skipDuplicates: !args.includes('--no-skip-duplicates'),
    dryRun: args.includes('--dry-run'),
    batchSize: parseInt(args.find(a => a.startsWith('--batch-size='))?.split('=')[1] || '100'),
  };
  
  try {
    const stats = await importDiveSites(inputFile, options);
    
    if (stats && stats.inserted > 0) {
      console.log('\n✅ Импорт завершен успешно!\n');
    } else {
      console.log('\n⚠️  Не удалось импортировать дайвсайты\n');
      process.exit(1);
    }
  } catch (error) {
    console.error('\n❌ Критическая ошибка:', error);
    process.exit(1);
  } finally {
    await pool.end();
  }
}

// Run if called directly
if (require.main === module) {
  main();
}

module.exports = { importDiveSites, prepareDiveSite, insertDiveSite };
