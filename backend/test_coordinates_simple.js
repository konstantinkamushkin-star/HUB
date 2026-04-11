// Simple test to verify coordinates are correct
const { Pool } = require('pg');

const dbConfig = {
  host: process.env.DB_HOST || 'localhost',
  port: parseInt(process.env.DB_PORT || '5432', 10),
  database: process.env.DB_DATABASE || 'divehub',
  user: process.env.DB_USERNAME || 'admin',
  password: process.env.DB_PASSWORD || 'admin123',
};

const pool = new Pool(dbConfig);

async function test() {
  console.log('🧪 Тест координат для проверки отображения на карте\n');
  
  // Test known locations
  const testSites = [
    { name: 'Ras Mohammed', country: 'Egypt', expectedLat: 27.7, expectedLng: 34.3 },
    { name: 'Blue Hole Dahab', country: 'Egypt', expectedLat: 28.5, expectedLng: 34.5 },
    { name: 'Manta Point', country: 'Maldives', expectedLat: 3.9, expectedLng: 73.4 },
  ];
  
  for (const testSite of testSites) {
    const result = await pool.query(`
      SELECT name, latitude, longitude, country,
             ST_AsText(location::geometry) as wkt
      FROM dive_sites
      WHERE name = $1 AND country = $2
      LIMIT 1
    `, [testSite.name, testSite.country]);
    
    if (result.rows.length > 0) {
      const row = result.rows[0];
      const lat = parseFloat(row.latitude);
      const lng = parseFloat(row.longitude);
      
      const latOk = Math.abs(lat - testSite.expectedLat) < 1.0;
      const lngOk = Math.abs(lng - testSite.expectedLng) < 1.0;
      
      console.log(`📍 ${row.name} (${row.country}):`);
      console.log(`   БД: lat=${lat}, lng=${lng}`);
      console.log(`   Ожидается: lat≈${testSite.expectedLat}, lng≈${testSite.expectedLng}`);
      console.log(`   ✅ Правильно: ${latOk && lngOk ? 'ДА' : 'НЕТ'}`);
      console.log(`   WKT: ${row.wkt}`);
      console.log('');
    } else {
      console.log(`⚠️  Не найден: ${testSite.name} (${testSite.country})\n`);
    }
  }
  
  // Check API format
  console.log('📡 Формат API ответа (пример):');
  console.log('   {');
  console.log('     "latitude": 27.7333,  // ← это latitude (широта)');
  console.log('     "longitude": 34.2833  // ← это longitude (долгота)');
  console.log('   }');
  console.log('');
  console.log('✅ Swift должен декодировать:');
  console.log('   latitude → location.latitude');
  console.log('   longitude → location.longitude');
  console.log('');
  console.log('✅ CLLocationCoordinate2D создается как:');
  console.log('   CLLocationCoordinate2D(latitude: lat, longitude: lng)');
  console.log('');
  
  await pool.end();
}

test().catch(console.error);
