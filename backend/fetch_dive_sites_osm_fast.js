// Fast version - only priority regions, simplified queries
const https = require('https');
const fs = require('fs');
const path = require('path');

const OUTPUT_FILE = path.join(__dirname, 'dive_sites_osm_fast.json');
const PROGRESS_FILE = path.join(__dirname, 'dive_sites_osm_progress.json');

// Only priority 1 regions (most important diving destinations)
const PRIORITY_REGIONS = [
  { name: 'Red Sea', bbox: [12, 32, 30, 45] },
  { name: 'Maldives', bbox: [-1, 72, 8, 75] },
  { name: 'Indonesia', bbox: [-11, 95, 6, 141] },
  { name: 'Philippines', bbox: [5, 117, 20, 127] },
  { name: 'Caribbean', bbox: [10, -90, 28, -60] },
  { name: 'Great Barrier Reef', bbox: [-25, 145, -10, 155] },
  { name: 'Galapagos', bbox: [-2, -92, 2, -89] },
  { name: 'Palau', bbox: [2, 131, 9, 135] },
  { name: 'Thailand', bbox: [6, 97, 21, 106] },
  { name: 'Malaysia', bbox: [1, 100, 8, 120] },
  { name: 'Hawaii', bbox: [18, -161, 23, -154] },
  { name: 'Florida', bbox: [24, -83, 31, -79] },
  { name: 'California', bbox: [32, -125, 42, -117] },
  { name: 'Fiji', bbox: [-21, 177, -15, 180] },
  { name: 'Seychelles', bbox: [-10, 46, -4, 56] },
  { name: 'South Africa', bbox: [-35, 16, -26, 33] },
  { name: 'Japan', bbox: [24, 122, 46, 146] },
  { name: 'Mexico', bbox: [14, -118, 32, -86] },
  { name: 'Egypt', bbox: [22, 25, 32, 37] },
  { name: 'Australia', bbox: [-44, 113, -10, 154] },
  { name: 'French Polynesia', bbox: [-28, -155, -8, -134] },
];

// Simplified query - only nodes and ways with names (faster)
function getSimpleQuery(bbox) {
  return `
[out:json][timeout:120][bbox:${bbox.join(',')}];
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

// Make request with longer timeout
function makeRequest(query) {
  return new Promise((resolve, reject) => {
    const apiUrl = 'https://overpass-api.de/api/interpreter';
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
    const req = https.request(apiUrl, options, (res) => {
      res.on('data', (chunk) => {
        data += chunk;
      });
      
      res.on('end', () => {
        if (res.statusCode === 200) {
          try {
            resolve(JSON.parse(data));
          } catch (error) {
            reject(new Error(`JSON parse error: ${error.message}`));
          }
        } else {
          reject(new Error(`HTTP ${res.statusCode}`));
        }
      });
    });
    
    req.on('error', reject);
    req.setTimeout(300000);
    req.on('timeout', () => {
      req.destroy();
      reject(new Error('Timeout after 5 minutes'));
    });
    
    req.write(postData);
    req.end();
  });
}

// Extract sites
function extractSites(osmData) {
  const sites = [];
  const processed = new Set();
  
  if (!osmData.elements) return sites;
  
  for (const el of osmData.elements) {
    if (!el.tags || !el.tags.name) continue;
    
    let lat, lon;
    if (el.type === 'node') {
      lat = el.lat;
      lon = el.lon;
    } else if (el.center) {
      lat = el.center.lat;
      lon = el.center.lon;
    } else {
      continue;
    }
    
    if (!lat || !lon || isNaN(lat) || isNaN(lon)) continue;
    if (Math.abs(lat) > 90 || Math.abs(lon) > 180) continue;
    
    const key = `${el.tags.name.toLowerCase()}|${lat.toFixed(4)}|${lon.toFixed(4)}`;
    if (processed.has(key)) continue;
    processed.add(key);
    
    const siteTypes = [];
    if (el.tags.natural === 'reef') siteTypes.push('reef');
    if (el.tags.historic === 'wreck') siteTypes.push('wreck');
    if (siteTypes.length === 0) siteTypes.push('reef');
    
    sites.push({
      name: el.tags.name.trim(),
      lat: parseFloat(lat.toFixed(6)),
      lng: parseFloat(lon.toFixed(6)),
      source: 'OpenStreetMap',
      country: el.tags['addr:country'] || null,
      region: el.tags['addr:state'] || el.tags['addr:region'] || null,
      siteTypes: siteTypes,
      difficulty: null,
      depthMin: null,
      depthMax: null,
    });
  }
  
  return sites;
}

// Main function
async function main() {
  console.log('🌊 Быстрая загрузка дайвсайтов из OSM (только приоритетные регионы)\n');
  
  let allSites = [];
  let processed = new Set();
  
  // Try to resume
  if (fs.existsSync(PROGRESS_FILE)) {
    try {
      const progress = JSON.parse(fs.readFileSync(PROGRESS_FILE, 'utf8'));
      allSites = progress;
      progress.forEach(s => {
        const key = `${s.name.toLowerCase()}|${s.lat.toFixed(4)}|${s.lng.toFixed(4)}`;
        processed.add(key);
      });
      console.log(`📂 Загружен прогресс: ${allSites.length} дайвсайтов\n`);
    } catch (e) {
      console.warn(`⚠️  Не удалось загрузить прогресс\n`);
    }
  }
  
  for (let i = 0; i < PRIORITY_REGIONS.length; i++) {
    const region = PRIORITY_REGIONS[i];
    try {
      console.log(`[${i + 1}/${PRIORITY_REGIONS.length}] ${region.name}...`);
      const query = getSimpleQuery(region.bbox);
      const data = await makeRequest(query);
      const sites = extractSites(data);
      
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
      
      // Save progress
      fs.writeFileSync(PROGRESS_FILE, JSON.stringify(allSites, null, 2));
      
      // Wait between requests
      if (i < PRIORITY_REGIONS.length - 1) {
        await new Promise(r => setTimeout(r, 3000));
      }
    } catch (error) {
      console.warn(`   ⚠️  Ошибка: ${error.message}`);
    }
  }
  
  // Save final result
  fs.writeFileSync(OUTPUT_FILE, JSON.stringify(allSites, null, 2));
  console.log(`\n✅ Готово! Сохранено ${allSites.length} дайвсайтов в ${OUTPUT_FILE}\n`);
  
  // Clean up progress file
  if (fs.existsSync(PROGRESS_FILE)) {
    fs.unlinkSync(PROGRESS_FILE);
  }
}

if (require.main === module) {
  main().catch(console.error);
}

module.exports = { PRIORITY_REGIONS };
