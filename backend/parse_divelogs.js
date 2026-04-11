// Parser for divelogs.org dive sites
const https = require('https');
const http = require('http');
const fs = require('fs');
const { URL } = require('url');

const OUTPUT_FILE = 'dive_sites_divelogs.json';
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
          if (data['@type'] === 'Place' || data['@type'] === 'TouristAttraction') {
            if (data.geo && data.geo.latitude && data.geo.longitude) {
              sites.push({
                name: data.name || 'Unknown',
                latitude: parseFloat(data.geo.latitude),
                longitude: parseFloat(data.geo.longitude),
                description: data.description || '',
                url: url,
                source: 'divelogs.org'
              });
            }
          }
        } catch (e) {
          // Ignore JSON parse errors
        }
      });
    }

    // Try to extract coordinates from various patterns
    const latPatterns = [
      /latitude["']?\s*[:=]\s*["']?(-?\d+\.?\d*)/i,
      /lat["']?\s*[:=]\s*["']?(-?\d+\.?\d*)/i,
      /"lat"\s*:\s*(-?\d+\.?\d*)/i,
    ];
    
    const lngPatterns = [
      /longitude["']?\s*[:=]\s*["']?(-?\d+\.?\d*)/i,
      /lng["']?\s*[:=]\s*["']?(-?\d+\.?\d*)/i,
      /lon["']?\s*[:=]\s*["']?(-?\d+\.?\d*)/i,
      /"lng"\s*:\s*(-?\d+\.?\d*)/i,
      /"lon"\s*:\s*(-?\d+\.?\d*)/i,
    ];

    let lat = null;
    let lng = null;

    for (const pattern of latPatterns) {
      const match = html.match(pattern);
      if (match) {
        lat = parseFloat(match[1]);
        break;
      }
    }

    for (const pattern of lngPatterns) {
      const match = html.match(pattern);
      if (match) {
        lng = parseFloat(match[1]);
        break;
      }
    }

    if (lat && lng) {
      const nameMatch = html.match(/<title[^>]*>(.*?)<\/title>/i) || 
                       html.match(/<h1[^>]*>(.*?)<\/h1>/i) ||
                       html.match(/<h2[^>]*>(.*?)<\/h2>/i);
      const name = nameMatch ? nameMatch[1].trim().replace(/\s*-\s*Divelogs\.org.*/i, '') : 'Unknown';
      
      sites.push({
        name: name,
        latitude: lat,
        longitude: lng,
        description: '',
        url: url,
        source: 'divelogs.org'
      });
    }

    // Try to extract from map markers
    const markerMatch = html.match(/new\s+google\.maps\.Marker[^}]*position[^}]*lat[^:]*:\s*(-?\d+\.?\d*)[^}]*lng[^:]*:\s*(-?\d+\.?\d*)/i);
    if (markerMatch) {
      const nameMatch = html.match(/<title[^>]*>(.*?)<\/title>/i) || 
                       html.match(/<h1[^>]*>(.*?)<\/h1>/i);
      const name = nameMatch ? nameMatch[1].trim() : 'Unknown';
      
      sites.push({
        name: name,
        latitude: parseFloat(markerMatch[1]),
        longitude: parseFloat(markerMatch[2]),
        description: '',
        url: url,
        source: 'divelogs.org'
      });
    }

  } catch (error) {
    console.error(`Error parsing HTML from ${url}:`, error.message);
  }

  return sites;
}

// Extract dive site URLs from explore page
function extractDiveSiteUrls(html) {
  const urls = [];
  
  // Try to find links to dive sites
  const linkMatches = html.match(/<a[^>]*href=["']([^"']*\/dive[^"']*|https?:\/\/[^"']*divelogs[^"']*\/[^"']*dive[^"']*)["'][^>]*>/gi);
  if (linkMatches) {
    linkMatches.forEach(match => {
      const hrefMatch = match.match(/href=["']([^"']+)["']/i);
      if (hrefMatch) {
        let url = hrefMatch[1];
        if (!url.startsWith('http')) {
          url = 'https://divelogs.org' + url;
        }
        if (url.includes('divelogs.org') && !urls.includes(url)) {
          urls.push(url);
        }
      }
    });
  }

  // Also try to extract from JavaScript data
  const dataMatch = html.match(/diveSites["']?\s*[:=]\s*\[(.*?)\]/s);
  if (dataMatch) {
    try {
      const sitesData = JSON.parse('[' + dataMatch[1] + ']');
      sitesData.forEach(site => {
        if (site.url || site.id) {
          const url = site.url || `https://divelogs.org/dive/${site.id}`;
          if (!urls.includes(url)) {
            urls.push(url);
          }
        }
      });
    } catch (e) {
      // Ignore parse errors
    }
  }

  return urls;
}

// Sleep function
function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

// Main parsing function
async function parseDivelogs() {
  console.log('🌊 Начинаю парсинг divelogs.org...\n');

  try {
    // Start with explore page
    const baseUrl = 'https://divelogs.org/explore.php';
    
    console.log(`📄 Парсинг страницы: ${baseUrl}`);
    
    const diveSiteUrls = new Set();
    
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
parseDivelogs().catch(console.error);
