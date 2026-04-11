// Script to check and fix swapped coordinates in database
const { Pool } = require('pg');

const dbConfig = {
  host: process.env.DB_HOST || 'localhost',
  port: parseInt(process.env.DB_PORT || '5432', 10),
  database: process.env.DB_DATABASE || 'divehub',
  user: process.env.DB_USERNAME || 'admin',
  password: process.env.DB_PASSWORD || 'admin123',
};

const pool = new Pool(dbConfig);

// Country-specific coordinate ranges
const COUNTRY_RANGES = {
  'Egypt': { latMin: 22, latMax: 30, lngMin: 25, lngMax: 37 },
  'Maldives': { latMin: -1, latMax: 8, lngMin: 72, lngMax: 75 },
  'Indonesia': { latMin: -11, latMax: 6, lngMin: 95, lngMax: 141 },
  'Philippines': { latMin: 5, latMax: 20, lngMin: 117, lngMax: 127 },
  'Thailand': { latMin: 6, latMax: 21, lngMin: 97, lngMax: 106 },
  'Malaysia': { latMin: 1, latMax: 8, lngMin: 100, lngMax: 120 },
  'Australia': { latMin: -44, latMax: -10, lngMin: 113, lngMax: 154 },
  'USA': { latMin: 24, latMax: 50, lngMin: -125, lngMax: -66 },
  'Mexico': { latMin: 14, latMax: 32, lngMin: -118, lngMax: -86 },
  'Cayman Islands': { latMin: 19, latMax: 20, lngMin: -81, lngMax: -79 },
};

// Check if coordinates are in valid range for country
function isInRange(lat, lng, country) {
  if (!country || !COUNTRY_RANGES[country]) {
    // Global check: lat -90 to 90, lng -180 to 180
    return Math.abs(lat) <= 90 && Math.abs(lng) <= 180;
  }
  
  const range = COUNTRY_RANGES[country];
  return lat >= range.latMin && lat <= range.latMax && 
         lng >= range.lngMin && lng <= range.lngMax;
}

// Check if swapped coordinates would be in range
function wouldSwappedBeInRange(lat, lng, country) {
  if (!country || !COUNTRY_RANGES[country]) {
    return Math.abs(lng) <= 90 && Math.abs(lat) <= 180;
  }
  
  const range = COUNTRY_RANGES[country];
  return lng >= range.latMin && lng <= range.latMax && 
         lat >= range.lngMin && lat <= range.lngMax;
}

async function fixCoordinates() {
  console.log('🔍 Проверка координат в базе данных...\n');
  
  // Get all dive sites
  const result = await pool.query(`
    SELECT id, name, latitude, longitude, country, region,
           ST_AsText(location::geometry) as location_wkt
    FROM dive_sites
    WHERE is_active = true
    ORDER BY country, name
  `);
  
  console.log(`📊 Проверка ${result.rows.length} дайвсайтов...\n`);
  
  const issues = [];
  const fixed = [];
  
  for (const row of result.rows) {
    const lat = parseFloat(row.latitude);
    const lng = parseFloat(row.longitude);
    const country = row.country;
    
    // Check if coordinates are obviously invalid
    if (Math.abs(lat) > 90 || Math.abs(lng) > 180) {
      issues.push({
        id: row.id,
        name: row.name,
        country: country,
        lat: lat,
        lng: lng,
        issue: 'out_of_range',
      });
      continue;
    }
    
    // Check if coordinates are in correct range for country
    const inRange = isInRange(lat, lng, country);
    const swappedInRange = wouldSwappedBeInRange(lat, lng, country);
    
    if (!inRange && swappedInRange) {
      // Coordinates are swapped - need to fix
      const newLat = lng;
      const newLng = lat;
      
      fixed.push({
        id: row.id,
        name: row.name,
        country: country,
        oldLat: lat,
        oldLng: lng,
        newLat: newLat,
        newLng: newLng,
      });
    } else if (!inRange && !swappedInRange) {
      // Coordinates are wrong but swapping won't help
      issues.push({
        id: row.id,
        name: row.name,
        country: country,
        lat: lat,
        lng: lng,
        issue: 'invalid_for_country',
      });
    }
  }
  
  console.log(`✅ Проверка завершена:\n`);
  console.log(`   Всего проверено: ${result.rows.length}`);
  console.log(`   Найдено проблем: ${issues.length}`);
  console.log(`   Требуют исправления: ${fixed.length}\n`);
  
  if (fixed.length > 0) {
    console.log(`🔧 Исправление координат...\n`);
    
    for (const item of fixed) {
      try {
        // Update location using PostGIS (this will automatically update latitude/longitude)
        await pool.query(`
          UPDATE dive_sites
          SET location = ST_SetSRID(ST_MakePoint($1, $2), 4326)::geography,
              updated_at = NOW()
          WHERE id = $3
        `, [item.newLng, item.newLat, item.id]);
        
        console.log(`   ✅ ${item.name} (${item.country}): [${item.oldLat}, ${item.oldLng}] → [${item.newLat}, ${item.newLng}]`);
      } catch (error) {
        console.error(`   ❌ Ошибка для ${item.name}: ${error.message}`);
      }
    }
    
    console.log(`\n✅ Исправлено ${fixed.length} дайвсайтов\n`);
  }
  
  if (issues.length > 0) {
    console.log(`⚠️  Проблемные дайвсайты (требуют ручной проверки):\n`);
    issues.slice(0, 10).forEach(item => {
      console.log(`   ${item.name} (${item.country}): lat=${item.lat}, lng=${item.lng} - ${item.issue}`);
    });
    if (issues.length > 10) {
      console.log(`   ... и еще ${issues.length - 10}`);
    }
  }
  
  // Final verification
  console.log(`\n🔍 Финальная проверка...\n`);
  const verifyResult = await pool.query(`
    SELECT COUNT(*) as total,
           COUNT(CASE WHEN ABS(latitude) > 90 OR ABS(longitude) > 180 THEN 1 END) as invalid
    FROM dive_sites
    WHERE is_active = true
  `);
  
  const total = parseInt(verifyResult.rows[0].total);
  const invalid = parseInt(verifyResult.rows[0].invalid);
  
  console.log(`   Всего дайвсайтов: ${total}`);
  console.log(`   Невалидных координат: ${invalid}`);
  
  if (invalid === 0) {
    console.log(`\n✅ Все координаты валидны!\n`);
  } else {
    console.log(`\n⚠️  Найдено ${invalid} дайвсайтов с невалидными координатами\n`);
  }
}

async function main() {
  try {
    await fixCoordinates();
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

module.exports = { fixCoordinates };
