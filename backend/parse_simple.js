// Simplified parser that tries to find API endpoints or uses OpenStreetMap as fallback
const fs = require('fs');
const axios = require('axios');

const OUTPUT_FILE = 'dive_sites_parsed.json';

// Since these sites likely use JavaScript, let's focus on OpenStreetMap Overpass API
// which we know works and has real dive site data
async function parseFromOSM() {
  console.log('🌊 Парсинг дайвсайтов из OpenStreetMap (Overpass API)...\n');
  
  const allSites = [];
  
  // Regions to query (popular diving destinations)
  // Split large regions into smaller ones to avoid timeouts
  const regions = [
    { name: 'Red Sea Egypt North', bbox: [27.0, 33.0, 29.0, 35.0] },
    { name: 'Red Sea Egypt South', bbox: [24.0, 34.0, 27.0, 36.0] },
    { name: 'Maldives North', bbox: [4.0, 72.0, 7.0, 74.0] },
    { name: 'Maldives South', bbox: [0.0, 72.0, 4.0, 74.0] },
    { name: 'Indonesia Bali', bbox: [-9.0, 114.0, -8.0, 116.0] },
    { name: 'Indonesia Komodo', bbox: [-9.0, 119.0, -8.0, 120.0] },
    { name: 'Indonesia Raja Ampat', bbox: [-1.0, 130.0, 1.0, 131.0] },
    { name: 'Philippines North', bbox: [10.0, 119.0, 20.0, 125.0] },
    { name: 'Philippines South', bbox: [5.0, 119.0, 10.0, 127.0] },
    { name: 'Thailand Andaman', bbox: [6.0, 97.0, 10.0, 99.0] },
    { name: 'Thailand Gulf', bbox: [9.0, 99.0, 13.0, 101.0] },
    { name: 'Caribbean East', bbox: [10.0, -75.0, 20.0, -60.0] },
    { name: 'Caribbean West', bbox: [15.0, -90.0, 25.0, -75.0] },
    { name: 'Great Barrier Reef North', bbox: [-15.0, 145.0, -10.0, 150.0] },
    { name: 'Great Barrier Reef South', bbox: [-25.0, 150.0, -20.0, 155.0] },
    { name: 'Mediterranean East', bbox: [30.0, 20.0, 40.0, 36.0] },
    { name: 'Mediterranean West', bbox: [35.0, -5.0, 45.0, 20.0] },
    { name: 'Galapagos', bbox: [-1.0, -92.0, 1.0, -89.0] },
    { name: 'Palau', bbox: [6.0, 134.0, 8.0, 135.0] },
    { name: 'Micronesia', bbox: [5.0, 150.0, 10.0, 155.0] },
    { name: 'Malaysia Sipadan', bbox: [4.0, 118.0, 5.0, 119.0] },
    { name: 'Hawaii', bbox: [19.0, -160.0, 22.0, -154.0] },
    { name: 'Florida Keys', bbox: [24.0, -82.0, 25.0, -80.0] },
    { name: 'Fiji', bbox: [-19.0, 177.0, -16.0, 180.0] },
    { name: 'Seychelles', bbox: [-5.0, 55.0, -4.0, 56.0] },
    { name: 'South Africa', bbox: [-30.0, 30.0, -26.0, 33.0] },
    { name: 'Japan Okinawa', bbox: [24.0, 123.0, 27.0, 128.0] },
    { name: 'Brazil Fernando', bbox: [-4.0, -33.0, -3.0, -32.0] },
  ];
  
  for (const region of regions) {
    console.log(`📍 Парсинг региона: ${region.name}...`);
    
    try {
      const [south, west, north, east] = region.bbox;
      
      const query = `
        [out:json][timeout:25];
        (
          node["leisure"="diving"](${south},${west},${north},${east});
          node["amenity"="dive_centre"](${south},${west},${north},${east});
          node["tourism"="dive_site"](${south},${west},${north},${east});
          way["leisure"="diving"](${south},${west},${north},${east});
          relation["leisure"="diving"](${south},${west},${north},${east});
        );
        out center;
      `;
      
      const response = await axios.post('https://overpass-api.de/api/interpreter', query, {
        headers: {
          'Content-Type': 'text/plain',
        },
        timeout: 30000,
      });
      
      if (response.data && response.data.elements) {
        const sites = response.data.elements
          .filter(el => el.lat && el.lon)
          .map(el => ({
            name: el.tags?.name || el.tags?.ref || 'Dive Site',
            latitude: el.lat || el.center?.lat,
            longitude: el.lon || el.center?.lon,
            description: el.tags?.description || '',
            source: 'OpenStreetMap',
            region: region.name,
          }));
        
        allSites.push(...sites);
        console.log(`   ✅ Найдено ${sites.length} дайвсайтов\n`);
      }
      
      // Delay between requests
      await new Promise(resolve => setTimeout(resolve, 2000));
      
    } catch (error) {
      console.error(`   ❌ Ошибка: ${error.message}`);
      // Retry once after delay
      if (error.message.includes('timeout') || error.message.includes('504') || error.message.includes('aborted')) {
        console.log(`   🔄 Повторная попытка через 5 секунд...`);
        await new Promise(resolve => setTimeout(resolve, 5000));
        try {
          const response = await axios.post('https://overpass-api.de/api/interpreter', query, {
            headers: { 'Content-Type': 'text/plain' },
            timeout: 30000,
          });
          if (response.data && response.data.elements) {
            const sites = response.data.elements
              .filter(el => el.lat && el.lon)
              .map(el => ({
                name: el.tags?.name || el.tags?.ref || 'Dive Site',
                latitude: el.lat || el.center?.lat,
                longitude: el.lon || el.center?.lon,
                description: el.tags?.description || '',
                source: 'OpenStreetMap',
                region: region.name,
              }));
            allSites.push(...sites);
            console.log(`   ✅ Найдено ${sites.length} дайвсайтов (повторная попытка)\n`);
          }
        } catch (retryError) {
          console.error(`   ❌ Повторная попытка не удалась: ${retryError.message}\n`);
        }
      } else {
        console.log('');
      }
    }
  }
  
  // Remove duplicates
  const uniqueSites = [];
  const seen = new Set();
  
  for (const site of allSites) {
    if (!site.latitude || !site.longitude) continue;
    
    const key = `${site.name.toLowerCase().trim()}|${site.latitude.toFixed(4)}|${site.longitude.toFixed(4)}`;
    
    if (!seen.has(key)) {
      seen.add(key);
      uniqueSites.push(site);
    }
  }
  
  fs.writeFileSync(OUTPUT_FILE, JSON.stringify(uniqueSites, null, 2));
  
  console.log('✅ Парсинг завершен!');
  console.log(`   Найдено уникальных дайвсайтов: ${uniqueSites.length}`);
  console.log(`   Сохранено в: ${OUTPUT_FILE}\n`);
  
  return uniqueSites;
}

// Try to find API endpoints for the sites
async function tryAPIs() {
  console.log('🔍 Попытка найти API endpoints...\n');
  
  const apis = [
    'https://www.scubago.com/api/dive-sites',
    'https://api.scubago.com/dive-sites',
    'https://dive.site/api/sites',
    'https://api.dive.site/sites',
    'https://divelogs.org/api/dives',
  ];
  
  for (const apiUrl of apis) {
    try {
      console.log(`   Проверка: ${apiUrl}`);
      const response = await axios.get(apiUrl, { timeout: 5000 });
      console.log(`   ✅ API найден! Статус: ${response.status}`);
      return response.data;
    } catch (error) {
      console.log(`   ❌ Недоступен (${error.message})`);
    }
  }
  
  console.log('   ⚠️  Публичные API не найдены\n');
  return null;
}

// Main function
async function main() {
  console.log('🌊 Парсинг дайвсайтов из различных источников\n');
  console.log('='.repeat(60) + '\n');
  
  // Try APIs first
  const apiData = await tryAPIs();
  
  // Fallback to OSM
  if (!apiData) {
    await parseFromOSM();
  }
}

main().catch(console.error);
