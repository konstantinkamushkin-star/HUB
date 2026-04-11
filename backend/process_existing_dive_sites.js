// Process and import existing dive sites data
const fs = require('fs');
const path = require('path');
const { verifyDiveSites } = require('./verify_dive_site_coordinates');
const { enrichDiveSites } = require('./enrich_dive_sites');
const { importDiveSites } = require('./import_processed_dive_sites');

const INPUT_FILES = [
  path.join(__dirname, 'dive_sites_data.json'),
  path.join(__dirname, 'dive_sites_base.json'),
];

const OUTPUT_VERIFIED = path.join(__dirname, 'dive_sites_existing_verified.json');
const OUTPUT_ENRICHED = path.join(__dirname, 'dive_sites_existing_enriched.json');
const OUTPUT_FINAL = path.join(__dirname, 'dive_sites_existing_final.json');

// Normalize dive site format
function normalizeSite(site) {
  return {
    name: site.name || 'Unknown',
    lat: site.latitude || site.lat,
    lng: site.longitude || site.lng,
    country: site.country || null,
    region: site.region || null,
    description: site.description || null,
    siteTypes: site.siteTypes || site.site_types || ['reef'],
    difficulty: site.difficulty || site.difficultyLevel || site.difficulty_level || null,
    depthMin: site.depthMin || site.minDepth || site.depth_min || null,
    depthMax: site.depthMax || site.maxDepth || site.depth_max || null,
    marineLife: site.marineLife || site.marine_life || [],
    averageRating: site.averageRating || site.average_rating || 0,
    reviewCount: site.reviewCount || site.review_count || 0,
  };
}

// Merge and deduplicate sites from multiple files
function mergeExistingSites() {
  console.log('📂 Загрузка существующих данных...\n');
  
  const allSites = [];
  const processed = new Set();
  
  for (const file of INPUT_FILES) {
    if (!fs.existsSync(file)) {
      console.warn(`   ⚠️  Файл не найден: ${file}`);
      continue;
    }
    
    try {
      const data = JSON.parse(fs.readFileSync(file, 'utf8'));
      const sites = Array.isArray(data) ? data : [];
      
      console.log(`   📄 ${path.basename(file)}: ${sites.length} дайвсайтов`);
      
      for (const site of sites) {
        const normalized = normalizeSite(site);
        
        // Skip if no coordinates
        if (!normalized.lat || !normalized.lng) {
          continue;
        }
        
        // Create unique key
        const key = `${normalized.name.toLowerCase().trim()}|${normalized.lat.toFixed(4)}|${normalized.lng.toFixed(4)}`;
        
        if (!processed.has(key)) {
          processed.add(key);
          allSites.push(normalized);
        }
      }
    } catch (error) {
      console.error(`   ❌ Ошибка чтения ${file}: ${error.message}`);
    }
  }
  
  console.log(`\n✅ Всего уникальных дайвсайтов: ${allSites.length}\n`);
  return allSites;
}

// Main processing function
async function processExistingSites(options = {}) {
  const {
    skipVerify = false,
    skipEnrich = false,
    skipImport = false,
  } = options;
  
  console.log('🌊 ============================================');
  console.log('🌊 Обработка существующих дайвсайтов');
  console.log('🌊 ============================================\n');
  
  // Step 1: Merge existing data
  const merged = mergeExistingSites();
  
  if (merged.length === 0) {
    console.error('❌ Нет данных для обработки');
    return null;
  }
  
  // Save merged
  const mergedFile = path.join(__dirname, 'dive_sites_existing_merged.json');
  fs.writeFileSync(mergedFile, JSON.stringify(merged, null, 2));
  console.log(`💾 Объединенные данные сохранены в ${mergedFile}\n`);
  
  // Step 2: Verify coordinates
  let verified = merged;
  if (!skipVerify) {
    console.log('🔍 Шаг 2: Верификация координат...\n');
    verified = verifyDiveSites(mergedFile, OUTPUT_VERIFIED);
    if (!verified) {
      verified = merged;
    }
  } else {
    verified = merged;
  }
  
  // Step 3: Enrich data
  let enriched = verified;
  if (!skipEnrich) {
    console.log('✨ Шаг 3: Обогащение данных...\n');
    const tempFile = path.join(__dirname, 'temp_verified.json');
    fs.writeFileSync(tempFile, JSON.stringify(verified, null, 2));
    enriched = enrichDiveSites(tempFile, OUTPUT_ENRICHED);
    fs.unlinkSync(tempFile);
    if (!enriched) {
      enriched = verified;
    }
  } else {
    enriched = verified;
  }
  
  // Step 4: Save final
  fs.writeFileSync(OUTPUT_FINAL, JSON.stringify(enriched, null, 2));
  console.log(`\n✅ Финальный результат сохранен в ${OUTPUT_FINAL}`);
  console.log(`   Всего дайвсайтов: ${enriched.length}\n`);
  
  // Step 5: Import to database
  if (!skipImport) {
    console.log('💾 Шаг 4: Импорт в базу данных...\n');
    try {
      const stats = await importDiveSites(OUTPUT_FINAL, { dryRun: false });
      if (stats && stats.inserted > 0) {
        console.log(`\n✅ Импортировано ${stats.inserted} дайвсайтов в базу данных\n`);
      }
    } catch (error) {
      console.error(`\n❌ Ошибка импорта: ${error.message}`);
      console.log(`\n💡 Вы можете импортировать вручную:`);
      console.log(`   node import_processed_dive_sites.js ${OUTPUT_FINAL}\n`);
    }
  } else {
    console.log('⏭️  Импорт пропущен (используйте --import для импорта)\n');
  }
  
  // Print statistics
  console.log('📊 Финальная статистика:');
  const byCountry = {};
  const byType = {};
  const withDepth = enriched.filter(s => s.depthMax || s.maxDepth).length;
  const withDifficulty = enriched.filter(s => s.difficulty || s.difficultyLevel).length;
  
  for (const site of enriched) {
    const country = site.country || 'Unknown';
    byCountry[country] = (byCountry[country] || 0) + 1;
    
    const types = site.siteTypes || [];
    for (const type of types) {
      byType[type] = (byType[type] || 0) + 1;
    }
  }
  
  console.log(`\n   По странам (топ 10):`);
  Object.entries(byCountry)
    .sort((a, b) => b[1] - a[1])
    .slice(0, 10)
    .forEach(([country, count]) => {
      console.log(`     ${country}: ${count}`);
    });
  
  console.log(`\n   По типам:`);
  Object.entries(byType)
    .sort((a, b) => b[1] - a[1])
    .forEach(([type, count]) => {
      console.log(`     ${type}: ${count}`);
    });
  
  console.log(`\n   Дополнительные данные:`);
  console.log(`     С глубиной: ${withDepth} (${((withDepth / enriched.length) * 100).toFixed(1)}%)`);
  console.log(`     Со сложностью: ${withDifficulty} (${((withDifficulty / enriched.length) * 100).toFixed(1)}%)`);
  
  return enriched;
}

// Main function
async function main() {
  const args = process.argv.slice(2);
  
  const options = {
    skipVerify: args.includes('--skip-verify'),
    skipEnrich: args.includes('--skip-enrich'),
    skipImport: args.includes('--skip-import'),
  };
  
  try {
    const result = await processExistingSites(options);
    
    if (result && result.length > 0) {
      console.log('\n✅ Обработка завершена успешно!\n');
      console.log(`📁 Финальный файл: ${OUTPUT_FINAL}`);
      if (options.skipImport) {
        console.log(`   Для импорта выполните:`);
        console.log(`   node import_processed_dive_sites.js ${OUTPUT_FINAL}\n`);
      }
    } else {
      console.log('\n❌ Не удалось обработать дайвсайты\n');
      process.exit(1);
    }
  } catch (error) {
    console.error('\n❌ Критическая ошибка:', error);
    process.exit(1);
  }
}

// Run if called directly
if (require.main === module) {
  main();
}

module.exports = { processExistingSites, mergeExistingSites };
