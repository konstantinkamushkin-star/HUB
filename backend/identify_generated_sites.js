// Script to identify and mark generated/fake dive sites
const { Pool } = require('pg');

const dbConfig = {
  host: process.env.DB_HOST || 'localhost',
  port: parseInt(process.env.DB_PORT || '5432', 10),
  database: process.env.DB_DATABASE || 'divehub',
  user: process.env.DB_USERNAME || 'admin',
  password: process.env.DB_PASSWORD || 'admin123',
};

const pool = new Pool(dbConfig);

// Known real dive sites with verified coordinates
const REAL_DIVE_SITES = new Set([
  // Red Sea - Egypt (verified real sites)
  'SS Thistlegorm', 'Ras Mohammed', 'Blue Hole Dahab', 'Brothers Islands',
  'Elphinstone Reef', 'Abu Nuhas', 'Dunraven Wreck', 'Shaab El Erg',
  'Gubal Island', 'Straits of Tiran', 'Jackson Reef', 'Woodhouse Reef',
  'Thomas Reef', 'Gordon Reef', 'Shark Reef', 'Yolanda Reef',
  'Anemone City', 'Eel Garden', 'Shark Observatory', 'Jolanda Reef',
  'Small Giftun', 'Big Giftun', 'Fanadir', 'Sharm El Naga',
  'Abu Ramada', 'Careless Reef', 'Tower', 'Umm Gamar',
  'Shaab Sabina', 'Shaab Claudia', 'Shaab Sharm', 'Shaab Samadai',
  'Dolphin House', 'Sataya Reef', 'Fury Shoal', 'Ras Umm Sid',
  'Near Garden', 'Far Garden', 'White Knight', 'Middle Garden',
  'Ras Katy', 'Ras Za\'atar', 'Ras Ghozlani', 'Ras Nasrani',
  'Naama Bay', 'Old Quay', 'Amphoras', 'Shark Bay',
  'Tiran Island', 'Sanafir Island', 'Abu Galawa', 'Carnatic',
  'Chrisoula K', 'Giannis D', 'Kimon M', 'Salem Express',
  'Rosalie Moller', 'Numidia', 'Aida',
  
  // Add more verified real sites from other regions...
]);

function isLikelyGenerated(site) {
  // Check for signs of generation:
  // 1. Very precise coordinates (14+ decimal places) = Math.random() result
  const latStr = String(site.latitude);
  const lngStr = String(site.longitude);
  const latDecimals = latStr.includes('.') ? latStr.split('.')[1]?.length || 0 : 0;
  const lngDecimals = lngStr.includes('.') ? lngStr.split('.')[1]?.length || 0 : 0;
  
  if (latDecimals > 10 || lngDecimals > 10) {
    return true; // Too precise = likely generated
  }
  
  // 2. Generic names that are commonly generated
  const genericPatterns = [
    /^(North|South|East|West|Big|Small|Little|Deep|Shallow|Red|Blue|Green|White|Black|Hidden|Secret|Twin|Triple|Double)\s+(Island|Reef|Cave|Wall|Passage|Channel|Bay|Cove|Grotto|Hole|Point|Garden|Arch|Bridge|Shoal|Bank|Pinnacle|Drop|Canyon)$/i
  ];
  
  for (const pattern of genericPatterns) {
    if (pattern.test(site.name) && !REAL_DIVE_SITES.has(site.name)) {
      return true;
    }
  }
  
  return false;
}

async function identifyGenerated() {
  console.log('🔍 Идентификация сгенерированных дайвсайтов...\n');
  
  try {
    await pool.query('SELECT 1');
    console.log('✅ Подключение к базе данных успешно\n');
  } catch (error) {
    console.error('❌ Ошибка подключения:', error.message);
    process.exit(1);
  }
  
  // Get all sites
  const result = await pool.query(`
    SELECT id, name, latitude, longitude, country, region, created_at
    FROM dive_sites
    ORDER BY created_at DESC
  `);
  
  console.log(`📋 Проверка ${result.rows.length} дайвсайтов...\n`);
  
  const generated = [];
  const real = [];
  const uncertain = [];
  
  for (const site of result.rows) {
    if (REAL_DIVE_SITES.has(site.name)) {
      real.push(site);
    } else if (isLikelyGenerated(site)) {
      generated.push(site);
    } else {
      uncertain.push(site);
    }
  }
  
  console.log('📊 Результаты анализа:\n');
  console.log(`✅ Реальные (известные): ${real.length}`);
  console.log(`❌ Вероятно сгенерированные: ${generated.length}`);
  console.log(`❓ Неопределенные: ${uncertain.length}\n`);
  
  if (generated.length > 0) {
    console.log('❌ Примеры сгенерированных дайвсайтов:\n');
    generated.slice(0, 20).forEach(site => {
      const latPrec = String(site.latitude).split('.')[1]?.length || 0;
      const lngPrec = String(site.longitude).split('.')[1]?.length || 0;
      console.log(`  - ${site.name} (${site.country})`);
      console.log(`    Координаты: (${site.latitude}, ${site.longitude})`);
      console.log(`    Точность: ${latPrec}/${lngPrec} знаков после запятой`);
      console.log(`    Создан: ${site.created_at}\n`);
    });
    
    if (generated.length > 20) {
      console.log(`   ... и еще ${generated.length - 20} дайвсайтов\n`);
    }
  }
  
  // Save list of generated sites
  const fs = require('fs');
  fs.writeFileSync('generated_dive_sites.json', JSON.stringify(generated.map(s => ({
    id: s.id,
    name: s.name,
    lat: s.latitude,
    lng: s.longitude,
    country: s.country,
    reason: 'Generated (high precision coordinates or generic name pattern)'
  })), null, 2));
  
  console.log(`💾 Список сгенерированных дайвсайтов сохранен в generated_dive_sites.json\n`);
  console.log('⚠️  ВНИМАНИЕ: Эти дайвсайты, вероятно, были сгенерированы и не являются реальными!\n');
  console.log('💡 Рекомендация: Удалить сгенерированные дайвсайты, оставив только реальные.\n');
  
  await pool.end();
}

identifyGenerated().catch(error => {
  console.error('❌ Ошибка:', error);
  process.exit(1);
});
