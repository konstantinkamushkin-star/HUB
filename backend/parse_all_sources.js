// Master script to parse all dive site sources and combine results
const fs = require('fs');
const { exec } = require('child_process');
const { promisify } = require('util');

const execAsync = promisify(exec);

const OUTPUT_FILES = {
  scubago: 'dive_sites_scubago.json',
  diveSite: 'dive_sites_dive_site.json',
  divelogs: 'dive_sites_divelogs.json',
};

const COMBINED_OUTPUT = 'dive_sites_all_parsed.json';

// Load and combine all parsed data
function combineAllSources() {
  console.log('🔄 Объединение данных из всех источников...\n');

  const allSites = [];
  const sources = [];

  // Load scubago data
  if (fs.existsSync(OUTPUT_FILES.scubago)) {
    try {
      const data = JSON.parse(fs.readFileSync(OUTPUT_FILES.scubago, 'utf8'));
      allSites.push(...data);
      sources.push({ name: 'scubago.com', count: data.length });
      console.log(`✅ Загружено ${data.length} дайвсайтов из scubago.com`);
    } catch (error) {
      console.error(`❌ Ошибка при загрузке ${OUTPUT_FILES.scubago}:`, error.message);
    }
  } else {
    console.log(`⚠️  Файл ${OUTPUT_FILES.scubago} не найден`);
  }

  // Load dive.site data
  if (fs.existsSync(OUTPUT_FILES.diveSite)) {
    try {
      const data = JSON.parse(fs.readFileSync(OUTPUT_FILES.diveSite, 'utf8'));
      allSites.push(...data);
      sources.push({ name: 'dive.site', count: data.length });
      console.log(`✅ Загружено ${data.length} дайвсайтов из dive.site`);
    } catch (error) {
      console.error(`❌ Ошибка при загрузке ${OUTPUT_FILES.diveSite}:`, error.message);
    }
  } else {
    console.log(`⚠️  Файл ${OUTPUT_FILES.diveSite} не найден`);
  }

  // Load divelogs data
  if (fs.existsSync(OUTPUT_FILES.divelogs)) {
    try {
      const data = JSON.parse(fs.readFileSync(OUTPUT_FILES.divelogs, 'utf8'));
      allSites.push(...data);
      sources.push({ name: 'divelogs.org', count: data.length });
      console.log(`✅ Загружено ${data.length} дайвсайтов из divelogs.org`);
    } catch (error) {
      console.error(`❌ Ошибка при загрузке ${OUTPUT_FILES.divelogs}:`, error.message);
    }
  } else {
    console.log(`⚠️  Файл ${OUTPUT_FILES.divelogs} не найден`);
  }

  console.log(`\n📊 Статистика по источникам:`);
  sources.forEach(source => {
    console.log(`   ${source.name}: ${source.count} дайвсайтов`);
  });

  // Remove duplicates based on coordinates and name
  console.log(`\n🔍 Удаление дубликатов...`);
  const uniqueSites = [];
  const seen = new Set();

  for (const site of allSites) {
    if (!site.latitude || !site.longitude || !site.name) {
      continue; // Skip invalid sites
    }

    // Create a key based on coordinates (rounded to 4 decimal places) and name
    const latKey = site.latitude.toFixed(4);
    const lngKey = site.longitude.toFixed(4);
    const nameKey = site.name.toLowerCase().trim();
    const key = `${nameKey}|${latKey}|${lngKey}`;

    if (!seen.has(key)) {
      seen.add(key);
      uniqueSites.push(site);
    }
  }

  console.log(`   Всего дайвсайтов: ${allSites.length}`);
  console.log(`   Уникальных дайвсайтов: ${uniqueSites.length}`);
  console.log(`   Дубликатов удалено: ${allSites.length - uniqueSites.length}\n`);

  // Save combined data
  fs.writeFileSync(COMBINED_OUTPUT, JSON.stringify(uniqueSites, null, 2));
  console.log(`✅ Объединенные данные сохранены в: ${COMBINED_OUTPUT}\n`);

  return uniqueSites;
}

// Main function
async function parseAllSources() {
  console.log('🌊 Парсинг всех источников дайвсайтов\n');
  console.log('='.repeat(60) + '\n');

  // Parse scubago.com
  console.log('1️⃣  Парсинг scubago.com...\n');
  try {
    await execAsync('node parse_scubago.js');
  } catch (error) {
    console.error('Ошибка при парсинге scubago.com:', error.message);
  }

  console.log('\n' + '='.repeat(60) + '\n');

  // Parse dive.site
  console.log('2️⃣  Парсинг dive.site...\n');
  try {
    await execAsync('node parse_dive_site.js');
  } catch (error) {
    console.error('Ошибка при парсинге dive.site:', error.message);
  }

  console.log('\n' + '='.repeat(60) + '\n');

  // Parse divelogs.org
  console.log('3️⃣  Парсинг divelogs.org...\n');
  try {
    await execAsync('node parse_divelogs.js');
  } catch (error) {
    console.error('Ошибка при парсинге divelogs.org:', error.message);
  }

  console.log('\n' + '='.repeat(60) + '\n');

  // Combine all sources
  const combinedSites = combineAllSources();

  console.log('✅ Парсинг всех источников завершен!\n');
  console.log(`📊 Итого уникальных дайвсайтов: ${combinedSites.length}\n`);
}

// Run
parseAllSources().catch(console.error);
