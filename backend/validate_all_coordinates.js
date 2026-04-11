// Comprehensive coordinate validation and fix script
const { Pool } = require('pg');

const dbConfig = {
  host: process.env.DB_HOST || 'localhost',
  port: parseInt(process.env.DB_PORT || '5432', 10),
  database: process.env.DB_DATABASE || 'divehub',
  user: process.env.DB_USERNAME || 'admin',
  password: process.env.DB_PASSWORD || 'admin123',
};

const pool = new Pool(dbConfig);

async function validateAndFix() {
  console.log('🔍 Полная проверка координат...\n');
  
  // Check all coordinates
  const result = await pool.query(`
    SELECT 
      id, name, latitude, longitude, country, region,
      ST_Y(location::geometry) as lat_from_location,
      ST_X(location::geometry) as lng_from_location,
      ST_AsText(location::geometry) as location_wkt
    FROM dive_sites
    WHERE is_active = true
    ORDER BY country, name
  `);
  
  console.log(`📊 Проверка ${result.rows.length} дайвсайтов...\n`);
  
  const issues = [];
  const mismatches = [];
  
  for (const row of result.rows) {
    const lat = parseFloat(row.latitude);
    const lng = parseFloat(row.longitude);
    const latFromLoc = parseFloat(row.lat_from_location);
    const lngFromLoc = parseFloat(row.lng_from_location);
    
    // Check 1: Basic validity
    if (isNaN(lat) || isNaN(lng) || Math.abs(lat) > 90 || Math.abs(lng) > 180) {
      issues.push({
        id: row.id,
        name: row.name,
        country: row.country,
        issue: 'invalid_range',
        lat: lat,
        lng: lng,
      });
      continue;
    }
    
    // Check 2: Match with location column
    const latMatch = Math.abs(lat - latFromLoc) < 0.0001;
    const lngMatch = Math.abs(lng - lngFromLoc) < 0.0001;
    
    if (!latMatch || !lngMatch) {
      mismatches.push({
        id: row.id,
        name: row.name,
        country: row.country,
        storedLat: lat,
        storedLng: lng,
        locationLat: latFromLoc,
        locationLng: lngFromLoc,
      });
    }
  }
  
  console.log(`✅ Проверка завершена:\n`);
  console.log(`   Всего проверено: ${result.rows.length}`);
  console.log(`   Невалидных координат: ${issues.length}`);
  console.log(`   Несоответствий с location: ${mismatches.length}\n`);
  
  if (issues.length > 0) {
    console.log(`❌ Найдены невалидные координаты:\n`);
    issues.slice(0, 10).forEach(item => {
      console.log(`   ${item.name} (${item.country}): lat=${item.lat}, lng=${item.lng} - ${item.issue}`);
    });
    if (issues.length > 10) {
      console.log(`   ... и еще ${issues.length - 10}`);
    }
  }
  
  if (mismatches.length > 0) {
    console.log(`\n⚠️  Найдены несоответствия между latitude/longitude и location:\n`);
    mismatches.slice(0, 10).forEach(item => {
      console.log(`   ${item.name} (${item.country}):`);
      console.log(`     stored: lat=${item.storedLat}, lng=${item.storedLng}`);
      console.log(`     location: lat=${item.locationLat}, lng=${item.locationLng}`);
    });
    if (mismatches.length > 10) {
      console.log(`   ... и еще ${mismatches.length - 10}`);
    }
    
    // Fix mismatches by updating location (which will auto-update latitude/longitude)
    console.log(`\n🔧 Исправление несоответствий...\n`);
    let fixed = 0;
    for (const item of mismatches) {
      try {
        // Use the values from location column (they are correct)
        await pool.query(`
          UPDATE dive_sites
          SET location = ST_SetSRID(ST_MakePoint($1, $2), 4326)::geography,
              updated_at = NOW()
          WHERE id = $3
        `, [item.locationLng, item.locationLat, item.id]);
        fixed++;
      } catch (error) {
        console.error(`   ❌ Ошибка для ${item.name}: ${error.message}`);
      }
    }
    console.log(`✅ Исправлено ${fixed} дайвсайтов\n`);
  }
  
  // Final summary
  const summary = await pool.query(`
    SELECT 
      COUNT(*) as total,
      COUNT(CASE WHEN ABS(latitude) > 90 OR ABS(longitude) > 180 THEN 1 END) as invalid,
      COUNT(CASE WHEN ABS(latitude - ST_Y(location::geometry)) > 0.0001 OR ABS(longitude - ST_X(location::geometry)) > 0.0001 THEN 1 END) as mismatched
    FROM dive_sites
    WHERE is_active = true
  `);
  
  const total = parseInt(summary.rows[0].total);
  const invalid = parseInt(summary.rows[0].invalid);
  const mismatched = parseInt(summary.rows[0].mismatched);
  
  console.log(`📊 Финальная статистика:\n`);
  console.log(`   Всего дайвсайтов: ${total}`);
  console.log(`   Невалидных координат: ${invalid}`);
  console.log(`   Несоответствий: ${mismatched}`);
  
  if (invalid === 0 && mismatched === 0) {
    console.log(`\n✅ Все координаты валидны и соответствуют location!\n`);
  } else {
    console.log(`\n⚠️  Требуется дополнительная проверка\n`);
  }
  
  // Sample valid coordinates for testing
  console.log(`📋 Примеры валидных координат для тестирования:\n`);
  const samples = await pool.query(`
    SELECT name, latitude, longitude, country
    FROM dive_sites
    WHERE is_active = true
      AND ABS(latitude) <= 90
      AND ABS(longitude) <= 180
      AND country IN ('Egypt', 'Maldives', 'Indonesia', 'Philippines', 'Thailand')
    ORDER BY country, name
    LIMIT 10
  `);
  
  samples.rows.forEach(row => {
    console.log(`   ${row.name} (${row.country}): lat=${row.latitude}, lng=${row.longitude}`);
  });
}

async function main() {
  try {
    await validateAndFix();
  } catch (error) {
    console.error('❌ Ошибка:', error);
    process.exit(1);
  } finally {
    await pool.end();
  }
}

if (require.main === module) {
  main();
}

module.exports = { validateAndFix };
