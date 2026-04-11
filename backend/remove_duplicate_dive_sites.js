// Script to remove duplicate dive sites
const { Pool } = require('pg');

const dbConfig = {
  host: process.env.DB_HOST || 'localhost',
  port: parseInt(process.env.DB_PORT || '5432', 10),
  database: process.env.DB_DATABASE || 'divehub',
  user: process.env.DB_USERNAME || 'admin',
  password: process.env.DB_PASSWORD || 'admin123',
};

const pool = new Pool(dbConfig);

async function removeDuplicates() {
  console.log('🔧 Удаление дубликатов дайвсайтов...\n');
  
  try {
    await pool.query('SELECT 1');
    console.log('✅ Подключение к базе данных успешно\n');
  } catch (error) {
    console.error('❌ Ошибка подключения:', error.message);
    process.exit(1);
  }
  
  // Strategy: Keep the oldest record (by id or created_at) for each unique name+coordinates combination
  // For exact duplicates (same name and coordinates), keep only one
  
  console.log('📋 Поиск дубликатов...\n');
  
  // 1. Find duplicates with very close coordinates (< 10 meters apart)
  // Use PostGIS distance calculation for accuracy
  const exactDuplicates = await pool.query(`
    SELECT 
      a.id as id1, a.name as name1, a.latitude as lat1, a.longitude as lng1,
      b.id as id2, b.name as name2, b.latitude as lat2, b.longitude as lng2,
      ST_Distance(a.location, b.location) as distance_meters
    FROM dive_sites a
    JOIN dive_sites b ON a.id < b.id
    WHERE a.latitude IS NOT NULL AND a.longitude IS NOT NULL
      AND b.latitude IS NOT NULL AND b.longitude IS NOT NULL
      AND a.location IS NOT NULL AND b.location IS NOT NULL
      AND ST_Distance(a.location, b.location) < 10
    ORDER BY distance_meters
  `);
  
  console.log(`Найдено ${exactDuplicates.rows.length} пар дайвсайтов очень близко друг к другу (< 10м)\n`);
  
  const idsToDelete = new Set();
  let deletedExact = 0;
  
  for (const pair of exactDuplicates.rows) {
    // Keep the first one (by id), mark the second for deletion
    if (!idsToDelete.has(pair.id1)) {
      idsToDelete.add(pair.id2);
      console.log(`  ✅ Близкие дайвсайты (< ${Math.round(pair.distance_meters)}м):`);
      console.log(`     Оставлен: ${pair.name1} (${pair.lat1.toFixed(6)}, ${pair.lng1.toFixed(6)})`);
      console.log(`     Удален: ${pair.name2} (${pair.lat2.toFixed(6)}, ${pair.lng2.toFixed(6)})`);
    }
  }
  
  for (const id of idsToDelete) {
    try {
      await pool.query('DELETE FROM dive_sites WHERE id = $1', [id]);
      deletedExact++;
    } catch (error) {
      console.error(`  ❌ Ошибка при удалении ${id}: ${error.message}`);
    }
  }
  
  // 2. Find duplicates with same name (but different coordinates - keep the one closest to known locations)
  const nameDuplicates = await pool.query(`
    SELECT 
      name, 
      COUNT(*) as count,
      array_agg(id ORDER BY created_at) as ids,
      array_agg(latitude ORDER BY created_at) as lats,
      array_agg(longitude ORDER BY created_at) as lngs
    FROM dive_sites
    WHERE name IS NOT NULL
    GROUP BY name
    HAVING COUNT(*) > 1
    ORDER BY count DESC
    LIMIT 50
  `);
  
  console.log(`\nНайдено ${nameDuplicates.rows.length} групп с одинаковыми названиями\n`);
  
  let deletedNames = 0;
  for (const group of nameDuplicates.rows) {
    // For same name, keep the first one (oldest), delete the rest
    const idsToDelete = group.ids.slice(1);
    
    for (const id of idsToDelete) {
      try {
        await pool.query('DELETE FROM dive_sites WHERE id = $1', [id]);
        deletedNames++;
      } catch (error) {
        console.error(`  ❌ Ошибка при удалении ${id}: ${error.message}`);
      }
    }
    
    if (idsToDelete.length > 0) {
      console.log(`  ✅ Удалено ${idsToDelete.length} дубликатов названия "${group.name}"`);
    }
  }
  
  console.log(`\n✅ Удалено дубликатов с одинаковыми координатами: ${deletedExact}`);
  console.log(`✅ Удалено дубликатов с одинаковыми названиями: ${deletedNames}`);
  console.log(`✅ Всего удалено: ${deletedExact + deletedNames}\n`);
  
  // Verify
  const verify = await pool.query(`
    SELECT 
      COUNT(*) as total,
      COUNT(DISTINCT name) as unique_names,
      COUNT(DISTINCT (latitude, longitude)) as unique_coords
    FROM dive_sites
  `);
  
  console.log('📊 Итоговая статистика:');
  console.log(`   Всего дайвсайтов: ${verify.rows[0].total}`);
  console.log(`   Уникальных названий: ${verify.rows[0].unique_names}`);
  console.log(`   Уникальных координат: ${verify.rows[0].unique_coords}\n`);
  
  await pool.end();
}

removeDuplicates().catch(error => {
  console.error('❌ Ошибка:', error);
  process.exit(1);
});
