// Enhanced script to fetch dive sites from OpenStreetMap using Overpass API
// This version includes better data extraction, deduplication, and region-based queries
const https = require('https');
const fs = require('fs');
const path = require('path');

const OUTPUT_FILE = path.join(__dirname, 'dive_sites_osm_enhanced.json');

// Overpass API endpoints (try multiple for reliability)
const OVERPASS_APIS = [
  'https://overpass-api.de/api/interpreter',
  'https://overpass.kumi.systems/api/interpreter',
  'https://overpass.openstreetmap.ru/api/interpreter',
];

// Major diving regions with bounding boxes
const DIVING_REGIONS = [
  { name: 'Red Sea', bbox: [12, 32, 30, 45], priority: 1 },
  { name: 'Maldives', bbox: [-1, 72, 8, 75], priority: 1 },
  { name: 'Indonesia', bbox: [-11, 95, 6, 141], priority: 1 },
  { name: 'Philippines', bbox: [5, 117, 20, 127], priority: 1 },
  { name: 'Caribbean', bbox: [10, -90, 28, -60], priority: 1 },
  { name: 'Great Barrier Reef', bbox: [-25, 145, -10, 155], priority: 1 },
  { name: 'Galapagos', bbox: [-2, -92, 2, -89], priority: 1 },
  { name: 'Palau', bbox: [2, 131, 9, 135], priority: 1 },
  { name: 'Micronesia', bbox: [1, 137, 12, 163], priority: 1 },
  { name: 'Thailand', bbox: [6, 97, 21, 106], priority: 1 },
  { name: 'Malaysia', bbox: [1, 100, 8, 120], priority: 1 },
  { name: 'Mediterranean', bbox: [30, -6, 46, 37], priority: 2 },
  { name: 'Hawaii', bbox: [18, -161, 23, -154], priority: 1 },
  { name: 'Florida', bbox: [24, -83, 31, -79], priority: 1 },
  { name: 'California', bbox: [32, -125, 42, -117], priority: 1 },
  { name: 'Fiji', bbox: [-21, 177, -15, 180], priority: 1 },
  { name: 'Seychelles', bbox: [-10, 46, -4, 56], priority: 1 },
  { name: 'South Africa', bbox: [-35, 16, -26, 33], priority: 1 },
  { name: 'Japan', bbox: [24, 122, 46, 146], priority: 1 },
  { name: 'Brazil', bbox: [-35, -50, -3, -32], priority: 2 },
  { name: 'Mexico', bbox: [14, -118, 32, -86], priority: 1 },
  { name: 'Egypt', bbox: [22, 25, 32, 37], priority: 1 },
  { name: 'Turkey', bbox: [35, 26, 42, 45], priority: 2 },
  { name: 'Croatia', bbox: [42, 13, 47, 20], priority: 2 },
  { name: 'Greece', bbox: [34, 19, 42, 30], priority: 2 },
  { name: 'Spain', bbox: [35, -10, 44, 5], priority: 2 },
  { name: 'Italy', bbox: [36, 6, 47, 19], priority: 2 },
  { name: 'Australia', bbox: [-44, 113, -10, 154], priority: 1 },
  { name: 'New Zealand', bbox: [-48, 166, -34, 179], priority: 2 },
  { name: 'French Polynesia', bbox: [-28, -155, -8, -134], priority: 1 },
];

// Generate regional query optimized for dive sites
function getRegionalQuery(bbox) {
  return `
[out:json][timeout:60][bbox:${bbox.join(',')}];
(
  // Primary dive site tags
  node["leisure"="diving"]["name"];
  way["leisure"="diving"]["name"];
  relation["leisure"="diving"]["name"];
  
  // Diving attractions
  node["tourism"="attraction"]["attraction"="diving"]["name"];
  way["tourism"="attraction"]["attraction"="diving"]["name"];
  relation["tourism"="attraction"]["attraction"="diving"]["name"];
  
  // Named reefs (often dive sites)
  node["natural"="reef"]["name"];
  way["natural"="reef"]["name"];
  relation["natural"="reef"]["name"];
  
  // Named shipwrecks (popular dive sites)
  node["historic"="wreck"]["name"];
  way["historic"="wreck"]["name"];
  relation["historic"="wreck"]["name"];
  
  // Underwater caves
  node["natural"="cave_entrance"]["name"];
  way["natural"="cave_entrance"]["name"];
  
  // Scuba diving specific
  node["sport"="scuba_diving"]["name"];
  way["sport"="scuba_diving"]["name"];
);
out center meta;
`;
}

// Make Overpass API request with retry logic
async function makeOverpassRequest(query, apiUrl = OVERPASS_APIS[0], retries = 3) {
  for (let attempt = 0; attempt < retries; attempt++) {
    try {
      if (attempt > 0) {
        // Try different API endpoint
        const apiIndex = attempt % OVERPASS_APIS.length;
        apiUrl = OVERPASS_APIS[apiIndex];
        console.log(`   🔄 Попытка ${attempt + 1}/${retries} с API: ${apiUrl}`);
        await new Promise(resolve => setTimeout(resolve, 2000 * attempt)); // Exponential backoff
      }
      
      return await new Promise((resolve, reject) => {
        const postData = `data=${encodeURIComponent(query)}`;
        
        const options = {
          method: 'POST',
          headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
            'Content-Length': Buffer.byteLength(postData),
          },
          timeout: 300000, // 5 minutes
        };
        
        let data = '';
        let bytesReceived = 0;
        let startTime = Date.now();
        
        const req = https.request(apiUrl, options, (res) => {
          res.on('data', (chunk) => {
            data += chunk;
            bytesReceived += chunk.length;
          });
          
          res.on('end', () => {
            if (res.statusCode === 200) {
              try {
                const result = JSON.parse(data);
                const elapsed = ((Date.now() - startTime) / 1000).toFixed(1);
                const mbReceived = (bytesReceived / 1024 / 1024).toFixed(2);
                console.log(`   ✅ Получено ${mbReceived} MB за ${elapsed}с`);
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
          reject(error);
        });
        
        req.setTimeout(300000); // 5 minutes
        req.on('timeout', () => {
          req.destroy();
          reject(new Error('Request timeout after 5 minutes'));
        });
        
        req.write(postData);
        req.end();
      });
    } catch (error) {
      if (attempt === retries - 1) {
        throw error;
      }
      console.warn(`   ⚠️  Ошибка: ${error.message}`);
    }
  }
}

// Enhanced extraction with better data processing
function extractDiveSites(osmData) {
  const diveSites = [];
  const processed = new Set();
  
  if (!osmData.elements) {
    return diveSites;
  }
  
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
      continue;
    }
    
    // Validate coordinates
    if (!lat || !lon || isNaN(lat) || isNaN(lon)) {
      continue;
    }
    if (Math.abs(lat) > 90 || Math.abs(lon) > 180) {
      continue;
    }
    
    // Create unique key to avoid duplicates
    const key = `${element.tags.name.toLowerCase().trim()}|${lat.toFixed(4)}|${lon.toFixed(4)}`;
    if (processed.has(key)) {
      continue;
    }
    processed.add(key);
    
    // Extract site types
    const siteTypes = [];
    if (element.tags['natural'] === 'reef') {
      siteTypes.push('reef');
    }
    if (element.tags['historic'] === 'wreck') {
      siteTypes.push('wreck');
    }
    if (element.tags['natural'] === 'cave_entrance') {
      siteTypes.push('cave');
    }
    if (element.tags['leisure'] === 'diving' || element.tags['sport'] === 'scuba_diving') {
      if (element.tags['dive:type']) {
        const types = element.tags['dive:type'].split(/[;,]/).map(t => t.trim().toLowerCase());
        siteTypes.push(...types.filter(t => ['reef', 'wreck', 'wall', 'cave', 'drift', 'shore', 'boat'].includes(t)));
      }
    }
    if (siteTypes.length === 0) {
      // Default based on tags
      if (element.tags['historic'] === 'wreck') {
        siteTypes.push('wreck');
      } else {
        siteTypes.push('reef'); // Default
      }
    }
    
    // Extract depth information
    let depthMin = null;
    let depthMax = null;
    if (element.tags['dive:depth']) {
      const depthStr = element.tags['dive:depth'];
      const depthMatch = depthStr.match(/(\d+)[-–—](\d+)/);
      if (depthMatch) {
        depthMin = parseInt(depthMatch[1]);
        depthMax = parseInt(depthMatch[2]);
      } else {
        const singleDepth = parseInt(depthStr);
        if (!isNaN(singleDepth)) {
          depthMax = singleDepth;
          depthMin = Math.max(0, singleDepth - 10); // Estimate min depth
        }
      }
    }
    
    // Extract difficulty
    let difficulty = null;
    if (element.tags['dive:difficulty']) {
      const diff = element.tags['dive:difficulty'].toLowerCase();
      if (diff.includes('beginner') || diff.includes('easy') || diff === '1') {
        difficulty = 1;
      } else if (diff.includes('intermediate') || diff.includes('medium') || diff === '2') {
        difficulty = 2;
      } else if (diff.includes('advanced') || diff.includes('hard') || diff === '3') {
        difficulty = 3;
      } else if (diff.includes('expert') || diff.includes('very hard') || diff === '4') {
        difficulty = 4;
      }
    }
    
    // Extract country and region
    let country = element.tags['addr:country'] || 
                  element.tags['ISO3166-1'] || 
                  element.tags['iso3166-1'] ||
                  element.tags['country'] ||
                  null;
    
    let region = element.tags['addr:state'] || 
                 element.tags['addr:region'] || 
                 element.tags['region'] ||
                 element.tags['state'] ||
                 null;
    
    // Build site object
    const site = {
      name: element.tags.name.trim(),
      lat: parseFloat(lat.toFixed(6)),
      lng: parseFloat(lon.toFixed(6)),
      source: 'OpenStreetMap',
      osmId: `${element.type}/${element.id}`,
      osmType: element.type,
      country: country,
      region: region,
      city: element.tags['addr:city'] || null,
      description: element.tags.description || 
                   element.tags['description:en'] || 
                   element.tags['description:ru'] ||
                   null,
      website: element.tags.website || element.tags.url || null,
      siteTypes: [...new Set(siteTypes)], // Remove duplicates
      difficulty: difficulty,
      depthMin: depthMin,
      depthMax: depthMax,
      // Additional metadata
      tags: element.tags,
      marineLife: element.tags['dive:marine_life'] ? 
                  element.tags['dive:marine_life'].split(/[;,]/).map(t => t.trim()) : 
                  [],
    };
    
    diveSites.push(site);
  }
  
  return diveSites;
}

// Fetch dive sites from all regions
async function fetchAllDiveSites(options = {}) {
  const { resume = false, startFrom = 0 } = options;
  
  console.log('🌊 Начинаю загрузку дайвсайтов из OpenStreetMap...\n');
  
  let allSites = [];
  let processed = new Set();
  
  // Try to resume from progress file
  if (resume) {
    const progressFile = path.join(__dirname, 'dive_sites_osm_progress.json');
    if (fs.existsSync(progressFile)) {
      try {
        const progress = JSON.parse(fs.readFileSync(progressFile, 'utf8'));
        allSites = progress;
        progress.forEach(site => {
          const key = `${site.name.toLowerCase()}|${site.lat.toFixed(4)}|${site.lng.toFixed(4)}`;
          processed.add(key);
        });
        console.log(`📂 Загружен прогресс: ${allSites.length} дайвсайтов\n`);
      } catch (error) {
        console.warn(`⚠️  Не удалось загрузить прогресс: ${error.message}\n`);
      }
    }
  }
  
  // Sort regions by priority
  const sortedRegions = [...DIVING_REGIONS].sort((a, b) => a.priority - b.priority);
  
  for (let i = startFrom; i < sortedRegions.length; i++) {
    const region = sortedRegions[i];
    try {
      console.log(`[${i + 1}/${sortedRegions.length}] 📡 ${region.name}...`);
      const query = getRegionalQuery(region.bbox);
      
      const data = await makeOverpassRequest(query);
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
      
      // Rate limiting - wait between requests
      if (i < sortedRegions.length - 1) {
        await new Promise(resolve => setTimeout(resolve, 3000));
      }
      
      // Save progress after each region
      if (allSites.length > 0) {
        const progressFile = path.join(__dirname, 'dive_sites_osm_progress.json');
        fs.writeFileSync(progressFile, JSON.stringify(allSites, null, 2));
        console.log(`   💾 Прогресс сохранен (${allSites.length} дайвсайтов)`);
      }
    } catch (error) {
      console.warn(`   ⚠️  ${region.name}: ${error.message}`);
      // Continue with next region
    }
  }
  
  console.log(`\n✅ Всего получено ${allSites.length} уникальных дайвсайтов\n`);
  return allSites;
}

// Main function
async function main() {
  const args = process.argv.slice(2);
  const resume = args.includes('--resume') || args.includes('-r');
  
  try {
    const diveSites = await fetchAllDiveSites({ resume });
    
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
    const withDepth = diveSites.filter(s => s.depthMax !== null).length;
    const withDifficulty = diveSites.filter(s => s.difficulty !== null).length;
    
    for (const site of diveSites) {
      const country = site.country || 'Unknown';
      byCountry[country] = (byCountry[country] || 0) + 1;
      
      for (const type of site.siteTypes) {
        byType[type] = (byType[type] || 0) + 1;
      }
    }
    
    console.log(`\n   По странам (топ 15):`);
    Object.entries(byCountry)
      .sort((a, b) => b[1] - a[1])
      .slice(0, 15)
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
    console.log(`     С глубиной: ${withDepth} (${((withDepth / diveSites.length) * 100).toFixed(1)}%)`);
    console.log(`     Со сложностью: ${withDifficulty} (${((withDifficulty / diveSites.length) * 100).toFixed(1)}%)`);
    
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

module.exports = { fetchAllDiveSites, extractDiveSites, DIVING_REGIONS };
