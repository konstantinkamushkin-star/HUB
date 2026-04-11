// Parser for OpenStreetMap dive sites using sport=scuba_diving tag
// Based on Python script approach
const axios = require('axios');
const fs = require('fs');

const OUTPUT_FILE = 'dive_sites_osm_scuba.json';
const CSV_OUTPUT = 'dive_sites_10000.csv';
const MAX_SITES = 10000;

// Major diving regions - split into smaller queries to avoid timeouts
const DIVING_REGIONS = [
  { name: 'Red Sea Egypt', bbox: [24, 32, 30, 36] },
  { name: 'Maldives', bbox: [-1, 72, 8, 75] },
  { name: 'Indonesia', bbox: [-11, 95, 6, 141] },
  { name: 'Philippines', bbox: [5, 117, 20, 127] },
  { name: 'Thailand', bbox: [6, 97, 21, 106] },
  { name: 'Caribbean', bbox: [10, -90, 28, -60] },
  { name: 'Great Barrier Reef', bbox: [-25, 145, -10, 155] },
  { name: 'Galapagos', bbox: [-2, -92, 2, -89] },
  { name: 'Palau', bbox: [2, 131, 9, 135] },
  { name: 'Micronesia', bbox: [1, 137, 12, 163] },
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
  { name: 'Mexico', bbox: [14, -120, 33, -86] },
  { name: 'Australia', bbox: [-45, 110, -10, 155] },
];

// Generate Overpass query for a region
function getRegionalQuery(bbox) {
  const [south, west, north, east] = bbox;
  return `
[out:json][timeout:300];
(
  node["sport"="scuba_diving"](${south},${west},${north},${east});
  way["sport"="scuba_diving"](${south},${west},${north},${east});
  relation["sport"="scuba_diving"](${south},${west},${north},${east});
);
out center;
`;
}

// Classify dive site type from tags
function classifyType(tags) {
  if (!tags) return 'other';
  
  if (tags.historic && tags.historic.includes('wreck')) {
    return 'wreck';
  }
  if (tags.natural === 'reef') {
    return 'reef';
  }
  if (tags.name && tags.name.toLowerCase().includes('cave')) {
    return 'cave';
  }
  if (tags.natural === 'cave') {
    return 'cave';
  }
  if (tags.natural === 'wall') {
    return 'wall';
  }
  
  return 'reef'; // Default
}

// Classify difficulty based on depth
function classifyDifficulty(depth) {
  if (!depth || depth < 0) return 'beginner'; // Unknown depth = beginner
  
  if (depth < 12) {
    return 'beginner';
  }
  if (depth < 25) {
    return 'intermediate';
  }
  if (depth < 40) {
    return 'advanced';
  }
  return 'expert';
}

// Determine environment from tags
function getEnvironment(tags) {
  if (!tags) return 'saltwater';
  
  if (tags.natural === 'lake' || 
      tags.natural === 'water' && tags.water === 'lake' ||
      tags.name && tags.name.toLowerCase().includes('lake')) {
    return 'freshwater';
  }
  
  return 'saltwater';
}

// Main parsing function
async function parseOSMScubaDiving() {
  console.log('🌊 Парсинг дайвсайтов из OpenStreetMap (sport=scuba_diving)...\n');
  console.log(`📋 Будет обработано ${DIVING_REGIONS.length} регионов\n`);
  
  const allSites = [];
  const seen = new Set();
  const OVERPASS_API = 'https://overpass-api.de/api/interpreter';
  
  for (let i = 0; i < DIVING_REGIONS.length; i++) {
    const region = DIVING_REGIONS[i];
    console.log(`📍 [${i + 1}/${DIVING_REGIONS.length}] Регион: ${region.name}...`);
    
    try {
      const query = getRegionalQuery(region.bbox);
      const response = await axios.post(OVERPASS_API, 
        `data=${encodeURIComponent(query)}`,
        {
          headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
          },
          timeout: 120000, // 2 minutes per region
        }
      );
      
      if (!response.data || !response.data.elements) {
        console.log(`   ⚠️  Нет данных\n`);
        continue;
      }
      
      console.log(`   ✅ Получено ${response.data.elements.length} элементов`);
      
      for (const el of response.data.elements) {
      let lat, lon;
      
      if (el.center) {
        lat = el.center.lat;
        lon = el.center.lon;
      } else if (el.lat && el.lon) {
        lat = el.lat;
        lon = el.lon;
      } else {
        continue; // Skip elements without coordinates
      }
      
      // Create unique key for deduplication
      const key = `${lat.toFixed(6)}|${lon.toFixed(6)}`;
      if (seen.has(key)) {
        continue; // Skip duplicates
      }
      seen.add(key);
      
      const tags = el.tags || {};
      const name = tags.name || `site_${el.id}`;
      
      // Extract depth if available in tags
      let depth = null;
      if (tags.depth) {
        depth = parseFloat(tags.depth);
      } else if (tags['depth:min']) {
        depth = parseFloat(tags['depth:min']);
      }
      
        const site = {
          name: name,
          lat: lat,
          lon: lon,
          latitude: lat,
          longitude: lon,
          depth: depth,
          min_depth: depth,
          max_depth: depth,
          avg_depth: depth,
          type: classifyType(tags),
          difficulty: classifyDifficulty(depth),
          environment: getEnvironment(tags),
          tags: tags,
          source: 'OpenStreetMap',
          region: region.name,
          osm_id: el.id,
          osm_type: el.type,
        };
        
        allSites.push(site);
        
        if (allSites.length >= MAX_SITES) {
          console.log(`   ⚠️  Достигнут лимит ${MAX_SITES} дайвсайтов\n`);
          break;
        }
      }
      
      console.log(`   ✅ Добавлено ${response.data.elements.length} дайвсайтов (всего: ${allSites.length})\n`);
      
      // Delay between requests
      if (i < DIVING_REGIONS.length - 1 && allSites.length < MAX_SITES) {
        await new Promise(resolve => setTimeout(resolve, 2000));
      }
      
      if (allSites.length >= MAX_SITES) {
        break;
      }
      
    } catch (error) {
      console.error(`   ❌ Ошибка: ${error.message}\n`);
      // Continue with next region
    }
  }
  
  console.log(`✅ Обработано ${allSites.length} уникальных дайвсайтов из ${DIVING_REGIONS.length} регионов\n`);
  
  // Limit to MAX_SITES
  const sites = allSites.slice(0, MAX_SITES);
  
  // Save JSON
  fs.writeFileSync(OUTPUT_FILE, JSON.stringify(sites, null, 2));
  console.log(`💾 JSON сохранен в: ${OUTPUT_FILE}\n`);
  
  // Save CSV
  const csvHeader = 'Название,Широта,Долгота,МинГлубина(m),МаксГлубина(m),СрГлубина(m),Тип,Сложность,Среда\n';
  const csvRows = sites.map(site => {
      const name = (site.name || '').replace(/,/g, ';').replace(/"/g, '""');
      const minDepth = site.min_depth !== null ? site.min_depth.toFixed(1) : '';
      const maxDepth = site.max_depth !== null ? site.max_depth.toFixed(1) : '';
      const avgDepth = site.avg_depth !== null ? site.avg_depth.toFixed(1) : '';
      const type = site.type || 'other';
      const difficulty = site.difficulty || 'beginner';
      const environment = site.environment || 'saltwater';
      
      return `"${name}",${site.lat},${site.lon},${minDepth},${maxDepth},${avgDepth},"${type}","${difficulty}","${environment}"`;
    });
    
    const csvContent = csvHeader + csvRows.join('\n');
    fs.writeFileSync(CSV_OUTPUT, '\ufeff' + csvContent, 'utf8'); // UTF-8 BOM for Excel
    
    console.log(`💾 CSV сохранен в: ${CSV_OUTPUT}\n`);
    
    // Statistics
    console.log('📊 Статистика:\n');
    const byType = {};
    const byDifficulty = {};
    const byEnvironment = {};
    
    sites.forEach(site => {
      byType[site.type] = (byType[site.type] || 0) + 1;
      byDifficulty[site.difficulty] = (byDifficulty[site.difficulty] || 0) + 1;
      byEnvironment[site.environment] = (byEnvironment[site.environment] || 0) + 1;
    });
    
    console.log('По типам:');
    Object.entries(byType).forEach(([type, count]) => {
      console.log(`   ${type}: ${count}`);
    });
    
    console.log('\nПо сложности:');
    Object.entries(byDifficulty).forEach(([difficulty, count]) => {
      console.log(`   ${difficulty}: ${count}`);
    });
    
    console.log('\nПо среде:');
    Object.entries(byEnvironment).forEach(([env, count]) => {
      console.log(`   ${env}: ${count}`);
    });
    
  console.log(`\n✅ Готово! Всего дайвсайтов: ${sites.length}\n`);
  
  return sites;
}

// Run parser
parseOSMScubaDiving().catch(console.error);
