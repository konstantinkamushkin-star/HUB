// Master script to parse all dive site sources using Puppeteer
const puppeteer = require('puppeteer');
const fs = require('fs');

const OUTPUT_FILES = {
  scubago: 'dive_sites_scubago.json',
  diveSite: 'dive_sites_dive_site.json',
  divelogs: 'dive_sites_divelogs.json',
};

const COMBINED_OUTPUT = 'dive_sites_all_parsed.json';
const DELAY_MS = 3000;

// Parse scubago.com
async function parseScubago(browser) {
  console.log('1️⃣  Парсинг scubago.com...\n');
  
  const sites = [];
  const page = await browser.newPage();
  
  try {
    await page.goto('https://www.scubago.com/en/dive-sites', {
      waitUntil: 'networkidle2',
      timeout: 60000
    });
    
    await page.waitForTimeout(5000);
    
    const extracted = await page.evaluate(() => {
      const results = [];
      
      // Look for map markers or dive site data
      const scripts = Array.from(document.querySelectorAll('script'));
      scripts.forEach(script => {
        const content = script.textContent || '';
        
        // Try to find JSON data with coordinates
        const jsonMatches = content.match(/\{[^}]*"lat"[^}]*"lng"[^}]*\}/g);
        if (jsonMatches) {
          jsonMatches.forEach(match => {
            try {
              const data = JSON.parse(match);
              if (data.lat && data.lng) {
                results.push({
                  name: data.name || data.title || 'Unknown',
                  latitude: parseFloat(data.lat),
                  longitude: parseFloat(data.lng),
                  source: 'scubago.com'
                });
              }
            } catch (e) {}
          });
        }
      });
      
      return results;
    });
    
    sites.push(...extracted);
    console.log(`   ✅ Найдено ${extracted.length} дайвсайтов\n`);
    
  } catch (error) {
    console.error(`   ❌ Ошибка: ${error.message}\n`);
  } finally {
    await page.close();
  }
  
  return sites;
}

// Parse dive.site
async function parseDiveSite(browser) {
  console.log('2️⃣  Парсинг dive.site...\n');
  
  const sites = [];
  const page = await browser.newPage();
  
  try {
    await page.goto('https://dive.site', {
      waitUntil: 'networkidle2',
      timeout: 60000
    });
    
    await page.waitForTimeout(5000);
    
    const extracted = await page.evaluate(() => {
      const results = [];
      
      // Look for dive site data
      const scripts = Array.from(document.querySelectorAll('script'));
      scripts.forEach(script => {
        const content = script.textContent || '';
        
        // Try to find coordinates
        const latMatch = content.match(/"lat"\s*:\s*(-?\d+\.?\d*)/);
        const lngMatch = content.match(/"lng"\s*:\s*(-?\d+\.?\d*)/);
        
        if (latMatch && lngMatch) {
          results.push({
            name: 'Dive Site',
            latitude: parseFloat(latMatch[1]),
            longitude: parseFloat(lngMatch[1]),
            source: 'dive.site'
          });
        }
      });
      
      return results;
    });
    
    sites.push(...extracted);
    console.log(`   ✅ Найдено ${extracted.length} дайвсайтов\n`);
    
  } catch (error) {
    console.error(`   ❌ Ошибка: ${error.message}\n`);
  } finally {
    await page.close();
  }
  
  return sites;
}

// Parse divelogs.org
async function parseDivelogs(browser) {
  console.log('3️⃣  Парсинг divelogs.org...\n');
  
  const sites = [];
  const page = await browser.newPage();
  
  try {
    await page.goto('https://divelogs.org/explore.php', {
      waitUntil: 'networkidle2',
      timeout: 60000
    });
    
    await page.waitForTimeout(5000);
    
    const extracted = await page.evaluate(() => {
      const results = [];
      
      // Look for dive site markers on map
      const markers = document.querySelectorAll('[data-lat], [data-latitude]');
      markers.forEach(marker => {
        const lat = marker.getAttribute('data-lat') || marker.getAttribute('data-latitude');
        const lng = marker.getAttribute('data-lng') || marker.getAttribute('data-longitude');
        
        if (lat && lng) {
          const name = marker.getAttribute('data-name') || 
                      marker.textContent?.trim() || 
                      'Unknown';
          
          results.push({
            name: name,
            latitude: parseFloat(lat),
            longitude: parseFloat(lng),
            source: 'divelogs.org'
          });
        }
      });
      
      // Also check scripts for data
      const scripts = Array.from(document.querySelectorAll('script'));
      scripts.forEach(script => {
        const content = script.textContent || '';
        const latMatch = content.match(/latitude["']?\s*[:=]\s*["']?(-?\d+\.?\d*)/);
        const lngMatch = content.match(/longitude["']?\s*[:=]\s*["']?(-?\d+\.?\d*)/);
        
        if (latMatch && lngMatch) {
          results.push({
            name: 'Dive Site',
            latitude: parseFloat(latMatch[1]),
            longitude: parseFloat(lngMatch[1]),
            source: 'divelogs.org'
          });
        }
      });
      
      return results;
    });
    
    sites.push(...extracted);
    console.log(`   ✅ Найдено ${extracted.length} дайвсайтов\n`);
    
  } catch (error) {
    console.error(`   ❌ Ошибка: ${error.message}\n`);
  } finally {
    await page.close();
  }
  
  return sites;
}

// Combine and deduplicate
function combineAndDeduplicate(allSites) {
  console.log('🔄 Объединение и удаление дубликатов...\n');
  
  const uniqueSites = [];
  const seen = new Set();
  
  for (const site of allSites) {
    if (!site.latitude || !site.longitude || !site.name) {
      continue;
    }
    
    const key = `${site.name.toLowerCase().trim()}|${site.latitude.toFixed(4)}|${site.longitude.toFixed(4)}`;
    
    if (!seen.has(key)) {
      seen.add(key);
      uniqueSites.push(site);
    }
  }
  
  return uniqueSites;
}

// Main function
async function parseAllSources() {
  console.log('🌊 Парсинг всех источников с помощью Puppeteer\n');
  console.log('='.repeat(60) + '\n');
  
  let browser;
  try {
    browser = await puppeteer.launch({
      headless: true,
      args: ['--no-sandbox', '--disable-setuid-sandbox']
    });
    
    const allSites = [];
    
    // Parse each source
    const scubagoSites = await parseScubago(browser);
    allSites.push(...scubagoSites);
    fs.writeFileSync(OUTPUT_FILES.scubago, JSON.stringify(scubagoSites, null, 2));
    
    await new Promise(resolve => setTimeout(resolve, DELAY_MS));
    
    const diveSiteSites = await parseDiveSite(browser);
    allSites.push(...diveSiteSites);
    fs.writeFileSync(OUTPUT_FILES.diveSite, JSON.stringify(diveSiteSites, null, 2));
    
    await new Promise(resolve => setTimeout(resolve, DELAY_MS));
    
    const divelogsSites = await parseDivelogs(browser);
    allSites.push(...divelogsSites);
    fs.writeFileSync(OUTPUT_FILES.divelogs, JSON.stringify(divelogsSites, null, 2));
    
    // Combine and deduplicate
    const uniqueSites = combineAndDeduplicate(allSites);
    
    fs.writeFileSync(COMBINED_OUTPUT, JSON.stringify(uniqueSites, null, 2));
    
    console.log('='.repeat(60) + '\n');
    console.log('✅ Парсинг завершен!\n');
    console.log(`📊 Статистика:`);
    console.log(`   scubago.com: ${scubagoSites.length} дайвсайтов`);
    console.log(`   dive.site: ${diveSiteSites.length} дайвсайтов`);
    console.log(`   divelogs.org: ${divelogsSites.length} дайвсайтов`);
    console.log(`   Всего уникальных: ${uniqueSites.length} дайвсайтов\n`);
    
  } catch (error) {
    console.error('❌ Критическая ошибка:', error);
  } finally {
    if (browser) {
      await browser.close();
    }
  }
}

// Run
parseAllSources().catch(console.error);
