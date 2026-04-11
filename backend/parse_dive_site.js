// Parser for dive.site dive sites
const https = require('https');
const http = require('http');
const fs = require('fs');
const { URL } = require('url');

const OUTPUT_FILE = 'dive_sites_dive_site.json';
const DELAY_MS = 2000; // 2 seconds delay between requests

let allDiveSites = [];

// Helper function to make HTTP requests
function makeRequest(url) {
  return new Promise((resolve, reject) => {
    const urlObj = new URL(url);
    const protocol = urlObj.protocol === 'https:' ? https : http;
    
    const options = {
      hostname: urlObj.hostname,
      port: urlObj.port || (urlObj.protocol === 'https:' ? 443 : 80),
      path: urlObj.pathname + urlObj.search,
      method: 'GET',
      headers: {
        'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
        'Accept-Language': 'en-US,en;q=0.9',
      }
    };

    const req = protocol.request(options, (res) => {
      let data = '';
      
      res.on('data', (chunk) => {
        data += chunk;
      });
      
      res.on('end', () => {
        resolve({ statusCode: res.statusCode, data });
      });
    });

    req.on('error', (error) => {
      reject(error);
    });

    req.setTimeout(30000, () => {
      req.destroy();
      reject(new Error('Request timeout'));
    });

    req.end();
  });
}

// Parse dive site from HTML
function parseDiveSiteFromHTML(html, url) {
  const sites = [];
  
  try {
    // Try to extract JSON-LD structured data
    const jsonLdMatch = html.match(/<script[^>]*type=["']application\/ld\+json["'][^>]*>(.*?)<\/script>/gs);
    if (jsonLdMatch) {
      jsonLdMatch.forEach(script => {
        try {
          const jsonStr = script.replace(/<script[^>]*>/, '').replace(/<\/script>/, '');
          const data = JSON.parse(jsonStr);
          if (data['@type'] === 'Place' || data['@type'] === 'TouristAttraction' || data['@type'] === 'DiveSite') {
            if (data.geo && data.geo.latitude && data.geo.longitude) {
              sites.push({
                name: data.name || 'Unknown',
                latitude: parseFloat(data.geo.latitude),
                longitude: parseFloat(data.geo.longitude),
                description: data.description || '',
                url: url,
                source: 'dive.site'
              });
            }
          }
        } catch (e) {
          // Ignore JSON parse errors
        }
      });
    }

    // Try to extract from JavaScript variables
    const latMatch = html.match(/(?:lat|latitude)["']?\s*[:=]\s*["']?(-?\d+\.?\d*)/i);
    const lngMatch = html.match(/(?:lng|lngitude)["']?\s*[:=]\s*["']?(-?\d+\.?\d*)/i);
    
    if (latMatch && lngMatch) {
      const nameMatch = html.match(/<title[^>]*>(.*?)<\/title>/i) || 
                       html.match(/<h1[^>]*>(.*?)<\/h1>/i);
      const name = nameMatch ? nameMatch[1].trim() : 'Unknown';
      
      sites.push({
        name: name,
        latitude: parseFloat(latMatch[1]),
        longitude: parseFloat(lngMatch[1]),
        description: '',
        url: url,
        source: 'dive.site'
      });
    }

    // Try to extract coordinates from map data
    const mapDataMatch = html.match(/center["']?\s*[:=]\s*\[([^\]]+)\]/i);
    if (mapDataMatch) {
      const coords = mapDataMatch[1].split(',').map(c => parseFloat(c.trim()));
      if (coords.length >= 2 && !isNaN(coords[0]) && !isNaN(coords[1])) {
        const nameMatch = html.match(/<title[^>]*>(.*?)<\/title>/i) || 
                         html.match(/<h1[^>]*>(.*?)<\/h1>/i);
        const name = nameMatch ? nameMatch[1].trim() : 'Unknown';
        
        sites.push({
          name: name,
          latitude: coords[0],
          longitude: coords[1],
          description: '',
          url: url,
          source: 'dive.site'
        });
      }
    }

  } catch (error) {
    console.error(`Error parsing HTML from ${url}:`, error.message);
  }

  return sites;
}

// Extract dive site URLs from listing page
function extractDiveSiteUrls(html) {
  const urls = [];
  
  // Try to find links to dive sites
  const linkMatches = html.match(/<a[^>]*href=["']([^"']*\/site[^"']*|https?:\/\/[^"']*dive[^"']*)["'][^>]*>/gi);
  if (linkMatches) {
    linkMatches.forEach(match => {
      const hrefMatch = match.match(/href=["']([^"']+)["']/i);
      if (hrefMatch) {
        let url = hrefMatch[1];
        if (!url.startsWith('http')) {
          url = 'https://dive.site' + url;
        }
        if (url.includes('dive.site') && !urls.includes(url)) {
          urls.push(url);
        }
      }
    });
  }

  return urls;
}

// Sleep function
function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

// Main parsing function
async function parseDiveSite() {
  console.log('🌊 Начинаю парсинг dive.site...\n');

  try {
    // Start with main dive sites page
    const baseUrls = [
      'https://dive.site',
      'https://dive.site/explore',
    ];

    const diveSiteUrls = new Set();

    // Collect dive site URLs from listing pages
    for (const baseUrl of baseUrls) {
      console.log(`📄 Парсинг страницы: ${baseUrl}`);
      
      try {
        const response = await makeRequest(baseUrl);
        if (response.statusCode === 200) {
          const urls = extractDiveSiteUrls(response.data);
          urls.forEach(url => diveSiteUrls.add(url));
          console.log(`   ✅ Найдено ${urls.length} ссылок на дайвсайты`);
        }
        
        await sleep(DELAY_MS);
      } catch (error) {
        console.error(`   ❌ Ошибка при парсинге ${baseUrl}:`, error.message);
      }
    }

    console.log(`\n📋 Всего найдено ${diveSiteUrls.size} уникальных URL дайвсайтов\n`);

    // Parse individual dive site pages
    let parsedCount = 0;
    for (const url of Array.from(diveSiteUrls).slice(0, 200)) { // Limit to 200 sites
      try {
        console.log(`🔍 Парсинг: ${url}`);
        const response = await makeRequest(url);
        
        if (response.statusCode === 200) {
          const sites = parseDiveSiteFromHTML(response.data, url);
          if (sites.length > 0) {
            allDiveSites.push(...sites);
            parsedCount++;
            console.log(`   ✅ Найдено ${sites.length} дайвсайт(ов)`);
          } else {
            console.log(`   ⚠️  Координаты не найдены`);
          }
        }
        
        await sleep(DELAY_MS);
      } catch (error) {
        console.error(`   ❌ Ошибка: ${error.message}`);
      }
    }

    // Save results
    fs.writeFileSync(OUTPUT_FILE, JSON.stringify(allDiveSites, null, 2));
    console.log(`\n✅ Парсинг завершен!`);
    console.log(`   Найдено дайвсайтов: ${allDiveSites.length}`);
    console.log(`   Сохранено в: ${OUTPUT_FILE}\n`);

  } catch (error) {
    console.error('❌ Критическая ошибка:', error);
  }
}

// Run parser
parseDiveSite().catch(console.error);
