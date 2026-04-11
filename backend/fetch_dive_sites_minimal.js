// Minimal version - only top 5 regions, very fast
const https = require('https');
const fs = require('fs');
const path = require('path');

const OUTPUT_FILE = path.join(__dirname, 'dive_sites_minimal.json');

// Top 5 diving regions only
const TOP_REGIONS = [
  { name: 'Red Sea', bbox: [12, 32, 30, 45] },
  { name: 'Maldives', bbox: [-1, 72, 8, 75] },
  { name: 'Indonesia', bbox: [-11, 95, 6, 141] },
  { name: 'Philippines', bbox: [5, 117, 20, 127] },
  { name: 'Caribbean', bbox: [10, -90, 28, -60] },
];

// Very simple query - only leisure=diving with names
function getMinimalQuery(bbox) {
  return `
[out:json][timeout:60][bbox:${bbox.join(',')}];
(
  node["leisure"="diving"]["name"];
  way["leisure"="diving"]["name"];
);
out center;
`;
}

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
      timeout: 120000, // 2 minutes
    };
    
    let data = '';
    const req = https.request(apiUrl, options, (res) => {
      res.on('data', (chunk) => { data += chunk; });
      res.on('end', () => {
        if (res.statusCode === 200) {
          try {
            resolve(JSON.parse(data));
          } catch (e) {
            reject(new Error(`JSON error: ${e.message}`));
          }
        } else {
          reject(new Error(`HTTP ${res.statusCode}`));
        }
      });
    });
    
    req.on('error', reject);
    req.setTimeout(120000);
    req.on('timeout', () => {
      req.destroy();
      reject(new Error('Timeout'));
    });
    
    req.write(postData);
    req.end();
  });
}

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
    
    sites.push({
      name: el.tags.name.trim(),
      lat: parseFloat(lat.toFixed(6)),
      lng: parseFloat(lon.toFixed(6)),
      source: 'OpenStreetMap',
      country: el.tags['addr:country'] || null,
      region: el.tags['addr:state'] || el.tags['addr:region'] || null,
      siteTypes: ['reef'],
      difficulty: null,
      depthMin: null,
      depthMax: null,
    });
  }
  
  return sites;
}

async function main() {
  console.log('🌊 Минимальная загрузка (топ 5 регионов)...\n');
  
  const allSites = [];
  const processed = new Set();
  
  for (let i = 0; i < TOP_REGIONS.length; i++) {
    const region = TOP_REGIONS[i];
    try {
      console.log(`[${i + 1}/${TOP_REGIONS.length}] ${region.name}...`);
      const query = getMinimalQuery(region.bbox);
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
      
      if (i < TOP_REGIONS.length - 1) {
        await new Promise(r => setTimeout(r, 2000));
      }
    } catch (error) {
      console.warn(`   ⚠️  ${error.message}`);
    }
  }
  
  fs.writeFileSync(OUTPUT_FILE, JSON.stringify(allSites, null, 2));
  console.log(`\n✅ Сохранено ${allSites.length} дайвсайтов в ${OUTPUT_FILE}\n`);
}

if (require.main === module) {
  main().catch(console.error);
}

module.exports = { TOP_REGIONS };
