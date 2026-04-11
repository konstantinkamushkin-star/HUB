// Parser for scubago.com using Puppeteer (for JavaScript-rendered content)
const puppeteer = require('puppeteer');
const fs = require('fs');

const OUTPUT_FILE = 'dive_sites_scubago.json';
const DELAY_MS = 3000; // 3 seconds delay between requests
const MAX_SITES = 500; // Maximum sites to parse

let allDiveSites = [];

async function parseScubagoWithPuppeteer() {
  console.log('🌊 Начинаю парсинг scubago.com с помощью Puppeteer...\n');

  let browser;
  try {
    browser = await puppeteer.launch({
      headless: true,
      args: ['--no-sandbox', '--disable-setuid-sandbox']
    });

    const page = await browser.newPage();
    await page.setViewport({ width: 1920, height: 1080 });
    await page.setUserAgent('Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36');

    // Navigate to dive sites page
    console.log('📄 Загрузка страницы dive sites...');
    await page.goto('https://www.scubago.com/en/dive-sites', {
      waitUntil: 'networkidle2',
      timeout: 60000
    });

    // Wait for content to load
    await page.waitForTimeout(5000);

    // Try to extract dive sites from the page
    console.log('🔍 Извлечение данных о дайвсайтах...');
    
    const diveSites = await page.evaluate(() => {
      const sites = [];
      
      // Try to find dive site cards or markers
      const siteElements = document.querySelectorAll('[data-dive-site], .dive-site, .site-card, [class*="dive"], [class*="site"]');
      
      siteElements.forEach((element, index) => {
        try {
          // Try to extract coordinates from data attributes
          const lat = element.getAttribute('data-lat') || 
                     element.getAttribute('data-latitude') ||
                     element.querySelector('[data-lat]')?.getAttribute('data-lat');
          const lng = element.getAttribute('data-lng') || 
                     element.getAttribute('data-longitude') ||
                     element.querySelector('[data-lng]')?.getAttribute('data-lng');
          
          if (lat && lng) {
            const name = element.querySelector('h1, h2, h3, .title, .name')?.textContent?.trim() ||
                        element.textContent?.trim().split('\n')[0] ||
                        'Unknown';
            
            sites.push({
              name: name.substring(0, 200),
              latitude: parseFloat(lat),
              longitude: parseFloat(lng),
              source: 'scubago.com'
            });
          }
        } catch (e) {
          // Skip invalid elements
        }
      });

      // Try to extract from JavaScript variables
      const scripts = Array.from(document.querySelectorAll('script'));
      scripts.forEach(script => {
        const content = script.textContent || script.innerHTML;
        
        // Look for coordinate patterns
        const coordPatterns = [
          /latitude["']?\s*[:=]\s*["']?(-?\d+\.?\d*)/gi,
          /longitude["']?\s*[:=]\s*["']?(-?\d+\.?\d*)/gi,
          /lat["']?\s*[:=]\s*["']?(-?\d+\.?\d*)/gi,
          /lng["']?\s*[:=]\s*["']?(-?\d+\.?\d*)/gi,
        ];
        
        // Try to find JSON data
        const jsonMatch = content.match(/\{.*?"lat"[^}]*"lng"[^}]*\}/g);
        if (jsonMatch) {
          jsonMatch.forEach(match => {
            try {
              const data = JSON.parse(match);
              if (data.lat && data.lng) {
                sites.push({
                  name: data.name || data.title || 'Unknown',
                  latitude: parseFloat(data.lat),
                  longitude: parseFloat(data.lng),
                  source: 'scubago.com'
                });
              }
            } catch (e) {
              // Skip invalid JSON
            }
          });
        }
      });

      return sites;
    });

    console.log(`   ✅ Найдено ${diveSites.length} дайвсайтов на странице`);

    // Try to click through pagination or load more
    let pageNum = 1;
    const maxPages = 10;
    
    while (diveSites.length < MAX_SITES && pageNum < maxPages) {
      try {
        // Try to find and click "Load More" or "Next" button
        const loadMoreButton = await page.$('button[class*="load"], button[class*="more"], a[class*="next"], button:has-text("Load More"), button:has-text("More")');
        
        if (loadMoreButton) {
          console.log(`📄 Загрузка следующей страницы...`);
          await loadMoreButton.click();
          await page.waitForTimeout(3000);
          
          const moreSites = await page.evaluate(() => {
            const sites = [];
            const siteElements = document.querySelectorAll('[data-dive-site], .dive-site, .site-card');
            // Extract sites similar to above
            return sites;
          });
          
          diveSites.push(...moreSites);
          console.log(`   ✅ Добавлено еще ${moreSites.length} дайвсайтов`);
        } else {
          break; // No more pages
        }
      } catch (e) {
        console.log(`   ⚠️  Не удалось загрузить следующую страницу`);
        break;
      }
      pageNum++;
    }

    allDiveSites = diveSites.slice(0, MAX_SITES);

    // Save results
    fs.writeFileSync(OUTPUT_FILE, JSON.stringify(allDiveSites, null, 2));
    console.log(`\n✅ Парсинг завершен!`);
    console.log(`   Найдено дайвсайтов: ${allDiveSites.length}`);
    console.log(`   Сохранено в: ${OUTPUT_FILE}\n`);

  } catch (error) {
    console.error('❌ Ошибка:', error.message);
  } finally {
    if (browser) {
      await browser.close();
    }
  }
}

// Run parser
parseScubagoWithPuppeteer().catch(console.error);
