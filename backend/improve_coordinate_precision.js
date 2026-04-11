// Script to improve coordinate precision in existing dive sites
// Updates coordinates to 6 decimal places for better accuracy (~10 cm precision)
const { Pool } = require('pg');

const dbConfig = {
  host: process.env.DB_HOST || 'localhost',
  port: parseInt(process.env.DB_PORT || '5432', 10),
  database: process.env.DB_DATABASE || 'divehub',
  user: process.env.DB_USERNAME || 'admin',
  password: process.env.DB_PASSWORD || 'admin123',
};

const pool = new Pool(dbConfig);

async function improvePrecision() {
  console.log('🔧 Улучшение точности координат...\n');
  
  try {
    await pool.query('SELECT 1');
    console.log('✅ Подключение к базе данных успешно\n');
  } catch (error) {
    console.error('❌ Ошибка подключения:', error.message);
    process.exit(1);
  }
  
  // Get all sites with coordinates
  const result = await pool.query(`
    SELECT id, name, latitude, longitude, location
    FROM dive_sites
    WHERE latitude IS NOT NULL AND longitude IS NOT NULL
    ORDER BY name
  `);
  
  console.log(`📋 Найдено ${result.rows.length} дайвсайтов с координатами\n`);
  
  let updated = 0;
  
  for (const site of result.rows) {
    // Round to 6 decimal places for ~10 cm precision
    const preciseLat = parseFloat(site.latitude.toFixed(6));
    const preciseLng = parseFloat(site.longitude.toFixed(6));
    
    // Only update if precision was improved
    const currentLatPrecision = String(site.latitude).split('.')[1]?.length || 0;
    const currentLngPrecision = String(site.longitude).split('.')[1]?.length || 0;
    
    if (currentLatPrecision < 6 || currentLngPrecision < 6) {
      try {
        await pool.query('ALTER TABLE dive_sites DISABLE TRIGGER ALL');
        await pool.query(`
          UPDATE dive_sites
          SET location = ST_SetSRID(ST_MakePoint($1, $2), 4326)::geography
          WHERE id = $3
        `, [preciseLng, preciseLat, site.id]);
        await pool.query('ALTER TABLE dive_sites ENABLE TRIGGER ALL');
        
        updated++;
        if (updated % 50 === 0) {
          console.log(`  ✅ Обновлено ${updated} дайвсайтов...`);
        }
      } catch (error) {
        console.error(`  ❌ Ошибка при обновлении ${site.name}: ${error.message}`);
      }
    }
  }
  
  console.log(`\n✅ Обновлено ${updated} дайвсайтов с улучшенной точностью координат\n`);
  
  // Verify precision
  const verify = await pool.query(`
    SELECT 
      name,
      latitude,
      longitude,
      ST_X(location::geometry) as lng_from_location,
      ST_Y(location::geometry) as lat_from_location
    FROM dive_sites
    WHERE latitude IS NOT NULL
    LIMIT 5
  `);
  
  console.log('📊 Примеры обновленных координат:');
  verify.rows.forEach(row => {
    const latPrecision = String(row.latitude).split('.')[1]?.length || 0;
    const lngPrecision = String(row.longitude).split('.')[1]?.length || 0;
    console.log(`  ${row.name}:`);
    console.log(`    lat: ${row.latitude} (${latPrecision} знаков после запятой)`);
    console.log(`    lng: ${row.longitude} (${lngPrecision} знаков после запятой)`);
  });
  
  await pool.end();
}

improvePrecision().catch(error => {
  console.error('❌ Ошибка:', error);
  process.exit(1);
});
