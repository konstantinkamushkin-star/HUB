// Script to fetch dive sites from Google Places API
// Requires GOOGLE_PLACES_API_KEY environment variable
const https = require('https');
const fs = require('fs');
const path = require('path');

const OUTPUT_FILE = path.join(__dirname, 'dive_sites_google_places.json');

// Google Places API configuration
const GOOGLE_PLACES_API_KEY = process.env.GOOGLE_PLACES_API_KEY;
const GOOGLE_PLACES_API_BASE = 'https://maps.googleapis.com/maps/api/place';

// Major diving locations for text search
const DIVING_LOCATIONS = [
  { query: 'dive site Red Sea Egypt', location: { lat: 27.5, lng: 34.0 } },
  { query: 'dive site Maldives', location: { lat: 3.2, lng: 73.2 } },
  { query: 'dive site Bali Indonesia', location: { lat: -8.4, lng: 115.2 } },
  { query: 'dive site Philippines', location: { lat: 12.0, lng: 122.0 } },
  { query: 'dive site Caribbean', location: { lat: 18.0, lng: -75.0 } },
  { query: 'dive site Great Barrier Reef', location: { lat: -18.0, lng: 147.0 } },
  { query: 'dive site Galapagos', location: { lat: -0.8, lng: -90.9 } },
  { query: 'dive site Palau', location: { lat: 7.5, lng: 134.5 } },
  { query: 'dive site Thailand', location: { lat: 8.0, lng: 99.0 } },
  { query: 'dive site Malaysia', location: { lat: 4.2, lng: 101.9 } },
  { query: 'dive site Hawaii', location: { lat: 20.8, lng: -156.3 } },
  { query: 'dive site Florida Keys', location: { lat: 24.6, lng: -81.8 } },
  { query: 'dive site California', location: { lat: 34.0, lng: -119.0 } },
  { query: 'dive site Fiji', location: { lat: -18.0, lng: 178.0 } },
  { query: 'dive site Seychelles', location: { lat: -4.6, lng: 55.5 } },
  { query: 'dive site South Africa', location: { lat: -34.0, lng: 18.5 } },
  { query: 'dive site Japan', location: { lat: 26.2, lng: 127.7 } },
  { query: 'dive site Mexico', location: { lat: 20.0, lng: -87.0 } },
  { query: 'dive site Turkey', location: { lat: 36.8, lng: 30.7 } },
  { query: 'dive site Croatia', location: { lat: 43.5, lng: 16.4 } },
  { query: 'dive site Greece', location: { lat: 37.0, lng: 25.0 } },
  { query: 'dive site Spain', location: { lat: 39.0, lng: -0.3 } },
  { query: 'dive site Italy', location: { lat: 40.8, lng: 14.2 } },
  { query: 'scuba diving site', location: { lat: 0, lng: 0 } }, // Global search
];

// Make Google Places API request
async function makeGooglePlacesRequest(endpoint, params) {
  return new Promise((resolve, reject) => {
    const queryParams = new URLSearchParams({
      ...params,
      key: GOOGLE_PLACES_API_KEY,
    });
    
    const url = `${GOOGLE_PLACES_API_BASE}${endpoint}?${queryParams.toString()}`;
    
    https.get(url, (res) => {
      let data = '';
      
      res.on('data', (chunk) => {
        data += chunk;
      });
      
      res.on('end', () => {
        try {
          const result = JSON.parse(data);
          
          if (result.status === 'OK' || result.status === 'ZERO_RESULTS') {
            resolve(result);
          } else if (result.status === 'OVER_QUERY_LIMIT') {
            reject(new Error('API quota exceeded'));
          } else {
            reject(new Error(`API error: ${result.status} - ${result.error_message || ''}`));
          }
        } catch (error) {
          reject(new Error(`JSON parse error: ${error.message}`));
        }
      });
    }).on('error', (error) => {
      reject(error);
    });
  });
}

// Text search for dive sites
async function textSearch(query, location = null) {
  const params = {
    query: query,
    type: 'scuba_diving', // Google Places type for dive sites
  };
  
  if (location) {
    params.location = `${location.lat},${location.lng}`;
    params.radius = '50000'; // 50km radius
  }
  
  try {
    const result = await makeGooglePlacesRequest('/textsearch/json', params);
    return result.results || [];
  } catch (error) {
    console.warn(`   ⚠️  Ошибка поиска "${query}": ${error.message}`);
    return [];
  }
}

// Get place details (for additional information)
async function getPlaceDetails(placeId) {
  const params = {
    place_id: placeId,
    fields: 'name,geometry,formatted_address,address_components,types,rating,user_ratings_total,website,photos',
  };
  
  try {
    const result = await makeGooglePlacesRequest('/details/json', params);
    return result.result || null;
  } catch (error) {
    console.warn(`   ⚠️  Ошибка получения деталей для ${placeId}: ${error.message}`);
    return null;
  }
}

// Extract country from address components
function extractCountry(addressComponents) {
  if (!addressComponents) return null;
  
  const countryComponent = addressComponents.find(comp => 
    comp.types.includes('country')
  );
  
  return countryComponent ? countryComponent.long_name : null;
}

// Extract region/state from address components
function extractRegion(addressComponents) {
  if (!addressComponents) return null;
  
  const regionComponent = addressComponents.find(comp => 
    comp.types.includes('administrative_area_level_1') || 
    comp.types.includes('administrative_area_level_2')
  );
  
  return regionComponent ? regionComponent.long_name : null;
}

// Convert Google Places result to dive site format
function convertToDiveSite(place, details = null) {
  const location = place.geometry?.location || details?.geometry?.location;
  if (!location || !location.lat || !location.lng) {
    return null;
  }
  
  const addressComponents = place.address_components || details?.address_components || [];
  const country = extractCountry(addressComponents);
  const region = extractRegion(addressComponents);
  
  // Determine site types from Google types
  const siteTypes = [];
  const placeTypes = place.types || details?.types || [];
  
  if (placeTypes.some(t => t.includes('wreck'))) {
    siteTypes.push('wreck');
  }
  if (placeTypes.some(t => t.includes('reef'))) {
    siteTypes.push('reef');
  }
  if (siteTypes.length === 0) {
    siteTypes.push('reef'); // Default
  }
  
  return {
    name: place.name || details?.name || 'Unknown Dive Site',
    lat: parseFloat(location.lat.toFixed(6)),
    lng: parseFloat(location.lng.toFixed(6)),
    source: 'Google Places API',
    googlePlaceId: place.place_id || details?.place_id,
    country: country,
    region: region,
    address: place.formatted_address || details?.formatted_address || null,
    website: place.website || details?.website || null,
    siteTypes: siteTypes,
    averageRating: place.rating || details?.rating || null,
    reviewCount: place.user_ratings_total || details?.user_ratings_total || 0,
    difficulty: null, // Not available from Google Places
    depthMin: null,
    depthMax: null,
    photos: place.photos || details?.photos || [],
  };
}

// Fetch dive sites from Google Places
async function fetchGooglePlacesDiveSites() {
  if (!GOOGLE_PLACES_API_KEY) {
    console.error('❌ GOOGLE_PLACES_API_KEY не установлен в переменных окружения');
    console.error('   Установите: export GOOGLE_PLACES_API_KEY=your_api_key');
    return [];
  }
  
  console.log('🌊 Начинаю загрузку дайвсайтов из Google Places API...\n');
  
  const allSites = [];
  const processedPlaceIds = new Set();
  const processedCoordinates = new Set();
  
  for (let i = 0; i < DIVING_LOCATIONS.length; i++) {
    const location = DIVING_LOCATIONS[i];
    try {
      console.log(`[${i + 1}/${DIVING_LOCATIONS.length}] 📡 Поиск: "${location.query}"...`);
      
      const results = await textSearch(location.query, location.location);
      console.log(`   ✅ Найдено ${results.length} результатов`);
      
      // Process each result
      for (const place of results) {
        // Skip if already processed
        if (place.place_id && processedPlaceIds.has(place.place_id)) {
          continue;
        }
        
        // Get additional details
        let details = null;
        if (place.place_id) {
          details = await getPlaceDetails(place.place_id);
          await new Promise(resolve => setTimeout(resolve, 100)); // Rate limiting
        }
        
        const site = convertToDiveSite(place, details);
        if (!site) {
          continue;
        }
        
        // Check for duplicate coordinates
        const coordKey = `${site.lat.toFixed(4)}|${site.lng.toFixed(4)}`;
        if (processedCoordinates.has(coordKey)) {
          continue;
        }
        
        processedCoordinates.add(coordKey);
        if (place.place_id) {
          processedPlaceIds.add(place.place_id);
        }
        
        allSites.push(site);
      }
      
      console.log(`   ✅ Всего уникальных: ${allSites.length}`);
      
      // Rate limiting - wait between searches
      if (i < DIVING_LOCATIONS.length - 1) {
        await new Promise(resolve => setTimeout(resolve, 1000));
      }
    } catch (error) {
      console.warn(`   ⚠️  Ошибка для "${location.query}": ${error.message}`);
      // Continue with next location
    }
  }
  
  console.log(`\n✅ Всего получено ${allSites.length} уникальных дайвсайтов из Google Places\n`);
  return allSites;
}

// Main function
async function main() {
  try {
    const diveSites = await fetchGooglePlacesDiveSites();
    
    if (diveSites.length === 0) {
      console.log('❌ Не удалось получить дайвсайты');
      if (!GOOGLE_PLACES_API_KEY) {
        console.log('\n💡 Для использования этого скрипта:');
        console.log('   1. Получите API ключ: https://console.cloud.google.com/');
        console.log('   2. Включите Places API');
        console.log('   3. Установите: export GOOGLE_PLACES_API_KEY=your_key');
      }
      process.exit(1);
    }
    
    // Save to file
    fs.writeFileSync(OUTPUT_FILE, JSON.stringify(diveSites, null, 2));
    console.log(`💾 Сохранено ${diveSites.length} дайвсайтов в ${OUTPUT_FILE}\n`);
    
    // Print statistics
    console.log('📊 Статистика:');
    const byCountry = {};
    const withRating = diveSites.filter(s => s.averageRating !== null).length;
    
    for (const site of diveSites) {
      const country = site.country || 'Unknown';
      byCountry[country] = (byCountry[country] || 0) + 1;
    }
    
    console.log(`\n   По странам (топ 10):`);
    Object.entries(byCountry)
      .sort((a, b) => b[1] - a[1])
      .slice(0, 10)
      .forEach(([country, count]) => {
        console.log(`     ${country}: ${count}`);
      });
    
    console.log(`\n   С рейтингом: ${withRating} (${((withRating / diveSites.length) * 100).toFixed(1)}%)`);
    
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

module.exports = { fetchGooglePlacesDiveSites, convertToDiveSite };
