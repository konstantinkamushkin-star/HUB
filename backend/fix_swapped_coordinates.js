// Fix swapped coordinates in database
const { Pool } = require('pg');

const dbConfig = {
  host: process.env.DB_HOST || 'localhost',
  port: parseInt(process.env.DB_PORT || '5432', 10),
  database: process.env.DB_DATABASE || 'divehub',
  user: process.env.DB_USERNAME || 'admin',
  password: process.env.DB_PASSWORD || 'admin123',
};

const pool = new Pool(dbConfig);

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
};

function wouldSwappedBeInRange(lat, lng, country) {
  if (!country || !EXPECTED_RANGES[country]) return false;
  const range = EXPECTED_RANGES[country];
  return lng >= range.lat[0] && lng <= range.lat[1] && 
         lat >= range.lng[0] && lat <= range.lng[1];
}

async function fixSwapped() {
  console.log('🔧 Исправление перепутанных координат...\n');
  
  const result = await pool.query(`
    SELECT id, name, latitude, longitude, country
    FROM dive_sites
    WHERE is_active = true
  `);
  
  const toFix = [];
  
  for (const row of result.rows) {
    const lat = parseFloat(row.latitude);
    const lng = parseFloat(row.longitude);
    
    if (wouldSwappedBeInRange(lat, lng, row.country)) {
      toFix.push({
        id: row.id,
        name: row.name,
        country: row.country,
        oldLat: lat,
        oldLng: lng,
        newLat: lng,
        newLng: lat,
      });
    }
  }
  
  if (toFix.length === 0) {
    console.log('✅ Нет перепутанных координат для исправления\n');
    await pool.end();
    return;
  }
  
  console.log(`Найдено ${toFix.length} дайвсайтов с перепутанными координатами\n`);
  
  let fixed = 0;
  for (const item of toFix) {
    try {
      await pool.query(`
        UPDATE dive_sites
        SET location = ST_SetSRID(ST_MakePoint($1, $2), 4326)::geography,
            updated_at = NOW()
        WHERE id = $3
      `, [item.newLng, item.newLat, item.id]);
      
      console.log(`✅ ${item.name} (${item.country}): [${item.oldLat}, ${item.oldLng}] → [${item.newLat}, ${item.newLng}]`);
      fixed++;
    } catch (error) {
      console.error(`❌ Ошибка для ${item.name}: ${error.message}`);
    }
  }
  
  console.log(`\n✅ Исправлено ${fixed} дайвсайтов\n`);
  await pool.end();
}

fixSwapped().catch(console.error);
