// Final comprehensive coordinate check
const { Pool } = require('pg');

const dbConfig = {
  host: process.env.DB_HOST || 'localhost',
  port: parseInt(process.env.DB_PORT || '5432', 10),
  database: process.env.DB_DATABASE || 'divehub',
  user: process.env.DB_USERNAME || 'admin',
  password: process.env.DB_PASSWORD || 'admin123',
};

const pool = new Pool(dbConfig);

// Expected coordinate ranges by country
const EXPECTED_RANGES = {
  'Egypt': { lat: [22, 30], lng: [25, 37] },
  'Maldives': { lat: [-1, 8], lng: [72, 75] },
  'Indonesia': { lat: [-11, 6], lng: [95, 141] },
  'Philippines': { lat: [5, 20], lng: [117, 127] },
  'Thailand': { lat: [6, 21], lng: [97, 106] },
  'Malaysia': { lat: [1, 8], lng: [100, 120] },
  'Australia': { lat: [-44, -10], lng: [113, 154] },
  'USA': { lat: [24, 50], lng: [-125, -66] },
  'Mexico': { lat: [14, 32], lng: [-118, -86] },
  'Cayman Islands': { lat: [19, 20], lng: [-81, -79] },
};

function isInExpectedRange(lat, lng, country) {
  if (!country || !EXPECTED_RANGES[country]) return true; // Unknown country, skip check
  const range = EXPECTED_RANGES[country];
  return lat >= range.lat[0] && lat <= range.lat[1] && 
         lng >= range.lng[0] && lng <= range.lng[1];
}

function wouldSwappedBeInRange(lat, lng, country) {
  if (!country || !EXPECTED_RANGES[country]) return false;
  const range = EXPECTED_RANGES[country];
  return lng >= range.lat[0] && lng <= range.lat[1] && 
         lat >= range.lng[0] && lat <= range.lng[1];
}

async function checkAll() {
  console.log('🔍 Финальная проверка всех координат...\n');
  
  const result = await pool.query(`
    SELECT id, name, latitude, longitude, country, region
    FROM dive_sites
    WHERE is_active = true
    ORDER BY country, name
  `);
  
  const issues = [];
  const swapped = [];
  
  for (const row of result.rows) {
    const lat = parseFloat(row.latitude);
    const lng = parseFloat(row.longitude);
    const country = row.country;
    
    // Basic validation
    if (isNaN(lat) || isNaN(lng) || Math.abs(lat) > 90 || Math.abs(lng) > 180) {
      issues.push({ ...row, issue: 'invalid_range', lat, lng });
      continue;
    }
    
    // Check if in expected range for country
    const inRange = isInExpectedRange(lat, lng, country);
    const swappedInRange = wouldSwappedBeInRange(lat, lng, country);
    
    if (!inRange && swappedInRange) {
      swapped.push({ ...row, lat, lng, swappedLat: lng, swappedLng: lat });
    } else if (!inRange) {
      issues.push({ ...row, issue: 'out_of_expected_range', lat, lng });
    }
  }
  
  console.log(`📊 Результаты проверки:\n`);
  console.log(`   Всего дайвсайтов: ${result.rows.length}`);
  console.log(`   Невалидных координат: ${issues.length}`);
  console.log(`   Возможно перепутанных: ${swapped.length}\n`);
  
  if (swapped.length > 0) {
    console.log(`⚠️  Найдены возможно перепутанные координаты:\n`);
    swapped.slice(0, 10).forEach(item => {
      console.log(`   ${item.name} (${item.country}):`);
      console.log(`     Текущие: lat=${item.lat}, lng=${item.lng}`);
      console.log(`     Если перепутаны: lat=${item.swappedLat}, lng=${item.swappedLng}`);
      console.log('');
    });
    
    if (swapped.length > 10) {
      console.log(`   ... и еще ${swapped.length - 10}\n`);
    }
    
    console.log(`💡 Исправить эти координаты? (y/n)`);
    console.log(`   Запустите: node fix_swapped_coordinates.js\n`);
  }
  
  if (issues.length > 0) {
    console.log(`❌ Найдены проблемы:\n`);
    issues.slice(0, 10).forEach(item => {
      console.log(`   ${item.name} (${item.country}): ${item.issue}`);
    });
  }
  
  if (issues.length === 0 && swapped.length === 0) {
    console.log(`✅ Все координаты валидны и в правильных диапазонах!\n`);
  }
  
  // Sample coordinates for testing
  console.log(`📋 Примеры координат для тестирования в приложении:\n`);
  const samples = await pool.query(`
    SELECT name, latitude, longitude, country
    FROM dive_sites
    WHERE is_active = true
      AND country IN ('Egypt', 'Maldives', 'Indonesia')
    ORDER BY country, name
    LIMIT 9
  `);
  
  samples.rows.forEach((row, i) => {
    console.log(`${i + 1}. ${row.name} (${row.country})`);
    console.log(`   API вернет: {"latitude": ${row.latitude}, "longitude": ${row.longitude}}`);
    console.log(`   Ожидается на карте: ~${row.latitude}°N, ~${row.longitude}°E`);
    console.log('');
  });
  
  await pool.end();
}

checkAll().catch(console.error);
