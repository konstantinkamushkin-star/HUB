// Script to fetch dive sites from OpenStreetMap using Overpass API
// This will get thousands of real dive sites with coordinates and names
const https = require('https');
const fs = require('fs');
const path = require('path');

const OUTPUT_FILE = path.join(__dirname, 'dive_sites_osm.json');

// Overpass API endpoint
const OVERPASS_API = 'https://overpass-api.de/api/interpreter';
// Alternative endpoints if main one is busy:
// const OVERPASS_API = 'https://overpass.kumi.systems/api/interpreter';
// const OVERPASS_API = 'https://overpass.openstreetmap.ru/api/interpreter';

// Overpass QL query to get all dive sites worldwide
// This query searches for:
// - leisure=diving (dive sites)
// - tourism=attraction + attraction=diving
// - amenity=dive_centre (dive centers, often have associated sites)
// - natural=reef (coral reefs)
// - historic=wreck (shipwrecks that are dive sites)
const OVERPASS_QUERY = `
[out:json][timeout:300];
(
  // Dive sites
  node["leisure"="diving"];
  way["leisure"="diving"];
  relation["leisure"="diving"];
  
  // Diving attractions
  node["tourism"="attraction"]["attraction"="diving"];
  way["tourism"="attraction"]["attraction"="diving"];
  relation["tourism"="attraction"]["attraction"="diving"];
  
  // Named reefs (often dive sites)
  node["natural"="reef"]["name"];
  way["natural"="reef"]["name"];
  relation["natural"="reef"]["name"];
  
  // Named shipwrecks (dive sites)
  node["historic"="wreck"]["name"];
  way["historic"="wreck"]["name"];
  relation["historic"="wreck"]["name"];
  
  // Dive centers (for location reference)
  node["amenity"="dive_centre"];
  way["amenity"="dive_centre"];
  relation["amenity"="dive_centre"];
);
out center meta;
`;

// Simplified query for regions - faster, less data
function getRegionalQuery(bbox) {
  // bbox format: [minLat, minLon, maxLat, maxLon]
  // Simplified query - only nodes and ways with names, no relations
  return `
[out:json][timeout:25][bbox:${bbox.join(',')}];
(
  node["leisure"="diving"]["name"];
  way["leisure"="diving"]["name"];
  node["tourism"="attraction"]["attraction"="diving"]["name"];
  way["tourism"="attraction"]["attraction"="diving"]["name"];
  node["natural"="reef"]["name"];
  way["natural"="reef"]["name"];
  node["historic"="wreck"]["name"];
  way["historic"="wreck"]["name"];
);
out center;
`;
}

// Major diving regions to query if global query fails
const DIVING_REGIONS = [
  { name: 'Red Sea', bbox: [12, 32, 30, 45] },
  { name: 'Maldives', bbox: [-1, 72, 8, 75] },
  { name: 'Indonesia', bbox: [-11, 95, 6, 141] },
  { name: 'Philippines', bbox: [5, 117, 20, 127] },
  { name: 'Caribbean', bbox: [10, -90, 28, -60] },
  { name: 'Great Barrier Reef', bbox: [-25, 145, -10, 155] },
  { name: 'Galapagos', bbox: [-2, -92, 2, -89] },
  { name: 'Palau', bbox: [2, 131, 9, 135] },
  { name: 'Micronesia', bbox: [1, 137, 12, 163] },
  { name: 'Thailand', bbox: [6, 97, 21, 106] },
  { name: 'Malaysia', bbox: [1, 100, 8, 120] },
  { name: 'Mediterranean', bbox: [30, -6, 46, 37] },
  { name: 'Hawaii', bbox: [18, -161, 23, -154] },
  { name: 'Florida', bbox: [24, -83, 31, -79] },
  { name: 'California', bbox: [32, -125, 42, -117] },
  { name: 'Fiji', bbox: [-21, 177, -15, 180] },
  { name: 'Seychelles', bbox: [-10, 46, -4, 56] },
  { name: 'South Africa', bbox: [-35, 16, -26, 33] },
  { name: 'Japan', bbox: [24, 122, 46, 146] },
  { name: 'Brazil', bbox: [-35, -50, -3, -32] },
];

function makeOverpassRequest(query, apiUrl = OVERPASS_API) {
  return new Promise((resolve, reject) => {
    const postData = `data=${encodeURIComponent(query)}`;
    
    const options = {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        'Content-Length': Buffer.byteLength(postData),
      },
      timeout: 30000, // 30 seconds timeout
    };
    
    console.log(`📡 Запрос к Overpass API: ${apiUrl}`);
    console.log(`   Размер запроса: ${(postData.length / 1024).toFixed(2)} KB`);
    console.log(`   Ожидание ответа...`);
    
    let data = '';
    let bytesReceived = 0;
    let progressInterval;
    let startTime = Date.now();
    
    // Show progress every 10 seconds
    progressInterval = setInterval(() => {
      const elapsed = ((Date.now() - startTime) / 1000).toFixed(0);
      const mbReceived = (bytesReceived / 1024 / 1024).toFixed(2);
      process.stdout.write(`\r   ⏳ Ожидание... (${elapsed}с, получено: ${mbReceived} MB)`);
    }, 10000);
    
    const req = https.request(apiUrl, options, (res) => {
      console.log(`\n   ✅ Подключено, статус: ${res.statusCode}`);
      console.log(`   📥 Загрузка данных...`);
      
      res.on('data', (chunk) => {
        data += chunk;
        bytesReceived += chunk.length;
        // Show progress every 1MB
        if (bytesReceived % (1024 * 1024) < chunk.length) {
          const mbReceived = (bytesReceived / 1024 / 1024).toFixed(2);
          process.stdout.write(`\r   📥 Загружено: ${mbReceived} MB`);
        }
      });
      
      res.on('end', () => {
        clearInterval(progressInterval);
        const totalMB = (bytesReceived / 1024 / 1024).toFixed(2);
        console.log(`\n   ✅ Загрузка завершена (${totalMB} MB)`);
        
        if (res.statusCode === 200) {
          try {
            console.log(`   🔄 Парсинг JSON...`);
            const result = JSON.parse(data);
            console.log(`   ✅ JSON распарсен успешно`);
            resolve(result);
          } catch (error) {
            reject(new Error(`Ошибка парсинга JSON: ${error.message}`));
          }
        } else {
          reject(new Error(`HTTP ${res.statusCode}: ${data.substring(0, 200)}`));
        }
      });
    });
    
    req.on('error', (error) => {
      clearInterval(progressInterval);
      console.log(`\n   ❌ Ошибка соединения`);
      reject(error);
    });
    
    req.setTimeout(30000); // 30 seconds
    req.on('timeout', () => {
      clearInterval(progressInterval);
      console.log(`\n   ⏱️  Таймаут запроса (30 секунд)`);
      req.destroy();
      reject(new Error('Request timeout after 30s'));
    });
    
    req.write(postData);
    req.end();
  });
}

function extractDiveSites(osmData) {
  const diveSites = [];
  const processed = new Set();
  
  if (!osmData.elements) {
    console.warn('⚠️  Нет элементов в ответе OSM');
    return diveSites;
  }
  
  console.log(`📊 Обработка ${osmData.elements.length} элементов из OSM...`);
  
  for (const element of osmData.elements) {
    // Skip if no name
    if (!element.tags || !element.tags.name) {
      continue;
    }
    
    // Get coordinates
    let lat, lon;
    if (element.type === 'node') {
      lat = element.lat;
      lon = element.lon;
    } else if (element.center) {
      lat = element.center.lat;
      lon = element.center.lon;
    } else {
      continue; // Skip if no coordinates
    }
    
    // Create unique key to avoid duplicates
    const key = `${element.tags.name.toLowerCase()}|${lat.toFixed(4)}|${lon.toFixed(4)}`;
    if (processed.has(key)) {
      continue;
    }
    processed.add(key);
    
    // Extract information
    const site = {
      name: element.tags.name,
      lat: lat,
      lng: lon,
      source: 'OpenStreetMap',
      osmId: `${element.type}/${element.id}`,
      osmType: element.type,
      tags: element.tags,
      // Extract additional info
      country: element.tags['addr:country'] || element.tags.iso3166_1 || null,
      region: element.tags['addr:state'] || element.tags['addr:region'] || null,
      city: element.tags['addr:city'] || null,
      description: element.tags.description || element.tags['description:en'] || null,
      website: element.tags.website || element.tags.url || null,
      // Determine site type from tags
      siteTypes: [],
      difficulty: null,
      depthMin: null,
      depthMax: null,
    };
    
    // Determine site types
    if (element.tags['natural'] === 'reef') {
      site.siteTypes.push('reef');
    }
    if (element.tags['historic'] === 'wreck') {
      site.siteTypes.push('wreck');
    }
    if (element.tags['leisure'] === 'diving') {
      // Try to determine type from other tags
      if (element.tags['dive:type']) {
        const types = element.tags['dive:type'].split(';').map(t => t.trim().toLowerCase());
        site.siteTypes.push(...types);
      }
    }
    if (site.siteTypes.length === 0) {
      site.siteTypes.push('reef'); // Default
    }
    
    // Extract depth information if available
    if (element.tags['dive:depth']) {
      const depthMatch = element.tags['dive:depth'].match(/(\d+)[-–](\d+)/);
      if (depthMatch) {
        site.depthMin = parseInt(depthMatch[1]);
        site.depthMax = parseInt(depthMatch[2]);
      } else {
        const singleDepth = parseInt(element.tags['dive:depth']);
        if (!isNaN(singleDepth)) {
          site.depthMax = singleDepth;
        }
      }
    }
    
    // Extract difficulty if available
    if (element.tags['dive:difficulty']) {
      const diff = element.tags['dive:difficulty'].toLowerCase();
      if (diff.includes('beginner') || diff.includes('easy')) {
        site.difficulty = 1;
      } else if (diff.includes('intermediate') || diff.includes('medium')) {
        site.difficulty = 2;
      } else if (diff.includes('advanced') || diff.includes('hard')) {
        site.difficulty = 3;
      } else if (diff.includes('expert') || diff.includes('very hard')) {
        site.difficulty = 4;
      }
    }
    
    diveSites.push(site);
  }
  
  return diveSites;
}

async function fetchGlobalDiveSites(skipGlobal = false) {
  console.log('🌊 Начинаю загрузку дайвсайтов из OpenStreetMap...\n');
  
  // Check command line argument to skip global query
  const args = process.argv.slice(2);
  if (args.includes('--skip-global') || args.includes('-s')) {
    skipGlobal = true;
    console.log('⏭️  Пропускаю глобальный запрос (используется --skip-global)\n');
  }
  
  if (!skipGlobal) {
    try {
      // Try global query first
      console.log('📡 Попытка глобального запроса...');
      console.log('   ⚠️  Внимание: глобальный запрос может занять несколько минут');
      console.log('   💡 Если зависает, используйте: node fetch_dive_sites_osm.js --skip-global\n');
      const globalData = await makeOverpassRequest(OVERPASS_QUERY, OVERPASS_API);
      const sites = extractDiveSites(globalData);
      
      if (sites.length > 0) {
        console.log(`✅ Получено ${sites.length} дайвсайтов из глобального запроса\n`);
        return sites;
      }
    } catch (error) {
      console.warn(`\n⚠️  Глобальный запрос не удался: ${error.message}`);
      console.log('📡 Пробую региональные запросы...\n');
    }
  } else {
    console.log('📡 Используются только региональные запросы...\n');
  }
  
  // If global query fails, try regional queries
  const allSites = [];
  const processed = new Set();
  
  for (let i = 0; i < DIVING_REGIONS.length; i++) {
    const region = DIVING_REGIONS[i];
    try {
      console.log(`[${i + 1}/${DIVING_REGIONS.length}] 📡 ${region.name}...`);
      const query = getRegionalQuery(region.bbox);
      
      // Add timeout wrapper
      const data = await Promise.race([
        makeOverpassRequest(query),
        new Promise((_, reject) => 
          setTimeout(() => reject(new Error('Timeout after 35s')), 35000)
        )
      ]);
      
      const sites = extractDiveSites(data);
      
      // Filter duplicates
      let newCount = 0;
      for (const site of sites) {
        const key = `${site.name.toLowerCase()}|${site.lat.toFixed(4)}|${site.lng.toFixed(4)}`;
        if (!processed.has(key)) {
          processed.add(key);
          allSites.push(site);
          newCount++;
        }
      }
      
      console.log(`   ✅ ${sites.length} найдено (новых: ${newCount}, всего: ${allSites.length})`);
      
      // Small delay to avoid rate limiting
      if (i < DIVING_REGIONS.length - 1) {
        await new Promise(resolve => setTimeout(resolve, 1000));
      }
    } catch (error) {
      console.warn(`   ⚠️  ${region.name}: ${error.message}`);
      // Continue with next region
    }
  }
  
  console.log(`\n✅ Всего получено ${allSites.length} уникальных дайвсайтов\n`);
  return allSites;
}

async function main() {
  try {
    const diveSites = await fetchGlobalDiveSites();
    
    if (diveSites.length === 0) {
      console.log('❌ Не удалось получить дайвсайты');
      process.exit(1);
    }
    
    // Save to file
    fs.writeFileSync(OUTPUT_FILE, JSON.stringify(diveSites, null, 2));
    console.log(`💾 Сохранено ${diveSites.length} дайвсайтов в ${OUTPUT_FILE}\n`);
    
    // Print statistics
    console.log('📊 Статистика:');
    const byCountry = {};
    const byType = {};
    
    for (const site of diveSites) {
      const country = site.country || 'Unknown';
      byCountry[country] = (byCountry[country] || 0) + 1;
      
      for (const type of site.siteTypes) {
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
    
    console.log('\n✅ Готово!\n');
  } catch (error) {
    console.error('❌ Ошибка:', error);
    process.exit(1);
  }
}

// Run if called directly
if (require.main === module) {
  main();
}

module.exports = { fetchGlobalDiveSites, extractDiveSites };
