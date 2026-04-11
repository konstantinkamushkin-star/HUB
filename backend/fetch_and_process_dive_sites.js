// Main script to fetch, verify, enrich, and merge dive sites from multiple sources
// Combines OpenStreetMap and Google Places API data
const fs = require('fs');
const path = require('path');

const { fetchAllDiveSites } = require('./fetch_dive_sites_osm_enhanced');
const { fetchGooglePlacesDiveSites } = require('./fetch_dive_sites_google_places');
const { verifyDiveSites } = require('./verify_dive_site_coordinates');
const { enrichDiveSites } = require('./enrich_dive_sites');

// Output files
const OSM_OUTPUT = path.join(__dirname, 'dive_sites_osm_enhanced.json');
const GOOGLE_OUTPUT = path.join(__dirname, 'dive_sites_google_places.json');
const MERGED_OUTPUT = path.join(__dirname, 'dive_sites_merged.json');
const VERIFIED_OUTPUT = path.join(__dirname, 'dive_sites_verified.json');
const ENRICHED_OUTPUT = path.join(__dirname, 'dive_sites_enriched.json');
const FINAL_OUTPUT = path.join(__dirname, 'dive_sites_final.json');

// Merge dive sites from multiple sources, removing duplicates
function mergeDiveSites(sources) {
  console.log(`\n🔄 Объединение данных из ${sources.length} источников...\n`);
  
  const merged = [];
  const processed = new Set();
  
  for (const source of sources) {
    if (!source || !Array.isArray(source.sites) || source.sites.length === 0) {
      console.log(`   ⚠️  Пропущен источник: ${source.name || 'Unknown'} (нет данных)`);
      continue;
    }
    
    console.log(`   📥 Обработка ${source.sites.length} дайвсайтов из ${source.name}...`);
    
    let added = 0;
    let duplicates = 0;
    
    for (const site of source.sites) {
      // Create unique key: name + coordinates (rounded to 4 decimals = ~10m accuracy)
      const coordKey = `${(site.lat || site.latitude).toFixed(4)}|${(site.lng || site.longitude).toFixed(4)}`;
      const nameKey = (site.name || '').toLowerCase().trim();
      const key = `${nameKey}|${coordKey}`;
      
      // Check for duplicates
      if (processed.has(key)) {
        duplicates++;
        continue;
      }
      
      // Check for nearby duplicates (within 100m)
      let isDuplicate = false;
      for (const existing of merged) {
        const existingLat = existing.lat || existing.latitude;
        const existingLng = existing.lng || existing.longitude;
        const distance = calculateDistance(
          site.lat || site.latitude,
          site.lng || site.longitude,
          existingLat,
          existingLng
        );
        
        // If within 100m and similar name, consider duplicate
        if (distance < 0.1 && 
            nameKey.length > 3 && 
            existing.name && 
            (nameKey.includes(existing.name.toLowerCase().substring(0, 5)) ||
             existing.name.toLowerCase().includes(nameKey.substring(0, 5)))) {
          isDuplicate = true;
          duplicates++;
          break;
        }
      }
      
      if (!isDuplicate) {
        processed.add(key);
        merged.push({
          ...site,
          sources: site.sources ? [...site.sources, source.name] : [source.name],
        });
        added++;
      }
    }
    
    console.log(`      ✅ Добавлено: ${added}, дубликатов: ${duplicates}`);
  }
  
  console.log(`\n✅ Всего уникальных дайвсайтов после объединения: ${merged.length}\n`);
  return merged;
}

// Calculate distance between two coordinates in kilometers (Haversine formula)
function calculateDistance(lat1, lng1, lat2, lng2) {
  const R = 6371; // Earth radius in km
  const dLat = (lat2 - lat1) * Math.PI / 180;
  const dLng = (lng2 - lng1) * Math.PI / 180;
  const a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
            Math.sin(dLng / 2) * Math.sin(dLng / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return R * c;
}

// Main processing pipeline
async function processDiveSites(options = {}) {
  const {
    skipOSM = false,
    skipGoogle = false,
    skipVerify = false,
    skipEnrich = false,
  } = options;
  
  console.log('🌊 ============================================');
  console.log('🌊 Загрузка и обработка дайвсайтов');
  console.log('🌊 ============================================\n');
  
  const sources = [];
  
  // Step 1: Fetch from OpenStreetMap
  if (!skipOSM) {
    try {
      console.log('📡 Шаг 1: Загрузка из OpenStreetMap...\n');
      
      // Try fast version first
      const fastScript = path.join(__dirname, 'fetch_dive_sites_osm_fast.js');
      if (fs.existsSync(fastScript)) {
        console.log('   Используется быстрая версия (только приоритетные регионы)\n');
        const { execSync } = require('child_process');
        try {
          execSync(`node ${fastScript}`, { stdio: 'inherit', timeout: 1800000 }); // 30 min max
          
          // Load results
          const fastOutput = path.join(__dirname, 'dive_sites_osm_fast.json');
          if (fs.existsSync(fastOutput)) {
            const osmSites = JSON.parse(fs.readFileSync(fastOutput, 'utf8'));
            if (osmSites && osmSites.length > 0) {
              sources.push({ name: 'OpenStreetMap', sites: osmSites });
              fs.writeFileSync(OSM_OUTPUT, JSON.stringify(osmSites, null, 2));
              console.log(`💾 Сохранено в ${OSM_OUTPUT}\n`);
            }
          }
        } catch (error) {
          console.warn(`   ⚠️  Быстрая версия не удалась, пробую полную версию...\n`);
          // Fallback to full version
          const osmSites = await fetchAllDiveSites({ resume: true });
          if (osmSites && osmSites.length > 0) {
            sources.push({ name: 'OpenStreetMap', sites: osmSites });
            fs.writeFileSync(OSM_OUTPUT, JSON.stringify(osmSites, null, 2));
            console.log(`💾 Сохранено в ${OSM_OUTPUT}\n`);
          }
        }
      } else {
        // Use enhanced version
        const osmSites = await fetchAllDiveSites({ resume: true });
        if (osmSites && osmSites.length > 0) {
          sources.push({ name: 'OpenStreetMap', sites: osmSites });
          fs.writeFileSync(OSM_OUTPUT, JSON.stringify(osmSites, null, 2));
          console.log(`💾 Сохранено в ${OSM_OUTPUT}\n`);
        }
      }
    } catch (error) {
      console.error(`❌ Ошибка загрузки из OSM: ${error.message}\n`);
    }
  } else {
    // Try to load existing file
    if (fs.existsSync(OSM_OUTPUT)) {
      console.log(`📂 Загрузка существующих данных OSM из ${OSM_OUTPUT}...\n`);
      const osmSites = JSON.parse(fs.readFileSync(OSM_OUTPUT, 'utf8'));
      sources.push({ name: 'OpenStreetMap', sites: osmSites });
    }
  }
  
  // Step 2: Fetch from Google Places API
  if (!skipGoogle) {
    try {
      console.log('📡 Шаг 2: Загрузка из Google Places API...\n');
      const googleSites = await fetchGooglePlacesDiveSites();
      if (googleSites && googleSites.length > 0) {
        sources.push({ name: 'Google Places', sites: googleSites });
        fs.writeFileSync(GOOGLE_OUTPUT, JSON.stringify(googleSites, null, 2));
        console.log(`💾 Сохранено в ${GOOGLE_OUTPUT}\n`);
      }
    } catch (error) {
      console.error(`❌ Ошибка загрузки из Google Places: ${error.message}\n`);
      console.log('💡 Пропуск Google Places (требуется API ключ)\n');
    }
  } else {
    // Try to load existing file
    if (fs.existsSync(GOOGLE_OUTPUT)) {
      console.log(`📂 Загрузка существующих данных Google Places из ${GOOGLE_OUTPUT}...\n`);
      const googleSites = JSON.parse(fs.readFileSync(GOOGLE_OUTPUT, 'utf8'));
      sources.push({ name: 'Google Places', sites: googleSites });
    }
  }
  
  if (sources.length === 0) {
    console.error('❌ Нет данных для обработки');
    return null;
  }
  
  // Step 3: Merge sources
  console.log('🔄 Шаг 3: Объединение источников...\n');
  const merged = mergeDiveSites(sources);
  fs.writeFileSync(MERGED_OUTPUT, JSON.stringify(merged, null, 2));
  console.log(`💾 Сохранено в ${MERGED_OUTPUT}\n`);
  
  // Step 4: Verify coordinates
  let verified = merged;
  if (!skipVerify) {
    console.log('🔍 Шаг 4: Верификация координат...\n');
    verified = verifyDiveSites(MERGED_OUTPUT, VERIFIED_OUTPUT);
    if (!verified) {
      verified = merged; // Fallback to merged if verification fails
    }
  } else if (fs.existsSync(VERIFIED_OUTPUT)) {
    console.log(`📂 Загрузка верифицированных данных из ${VERIFIED_OUTPUT}...\n`);
    verified = JSON.parse(fs.readFileSync(VERIFIED_OUTPUT, 'utf8'));
  }
  
  // Step 5: Enrich data
  let enriched = verified;
  if (!skipEnrich) {
    console.log('✨ Шаг 5: Обогащение данных...\n');
    const tempFile = path.join(__dirname, 'temp_verified.json');
    fs.writeFileSync(tempFile, JSON.stringify(verified, null, 2));
    enriched = enrichDiveSites(tempFile, ENRICHED_OUTPUT);
    fs.unlinkSync(tempFile); // Clean up temp file
    if (!enriched) {
      enriched = verified; // Fallback
    }
  } else if (fs.existsSync(ENRICHED_OUTPUT)) {
    console.log(`📂 Загрузка обогащенных данных из ${ENRICHED_OUTPUT}...\n`);
    enriched = JSON.parse(fs.readFileSync(ENRICHED_OUTPUT, 'utf8'));
  }
  
  // Final output
  fs.writeFileSync(FINAL_OUTPUT, JSON.stringify(enriched, null, 2));
  console.log(`\n✅ Финальный результат сохранен в ${FINAL_OUTPUT}`);
  console.log(`   Всего дайвсайтов: ${enriched.length}\n`);
  
  // Print final statistics
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
    skipOSM: args.includes('--skip-osm'),
    skipGoogle: args.includes('--skip-google'),
    skipVerify: args.includes('--skip-verify'),
    skipEnrich: args.includes('--skip-enrich'),
  };
  
  try {
    const result = await processDiveSites(options);
    
    if (result && result.length > 0) {
      console.log('\n✅ Все этапы завершены успешно!\n');
      console.log(`📁 Финальный файл: ${FINAL_OUTPUT}`);
      console.log(`   Используйте этот файл для импорта в базу данных\n`);
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

module.exports = { processDiveSites, mergeDiveSites };
