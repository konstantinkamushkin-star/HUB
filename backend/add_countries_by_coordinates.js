// Script to add countries to dive sites based on coordinates using reverse geocoding
const { Pool } = require('pg');
const axios = require('axios');

const dbConfig = {
  host: process.env.DB_HOST || 'localhost',
  port: parseInt(process.env.DB_PORT || '5432', 10),
  database: process.env.DB_DATABASE || 'divehub',
  user: process.env.DB_USERNAME || 'admin',
  password: process.env.DB_PASSWORD || 'admin123',
};

const pool = new Pool(dbConfig);

// Nominatim API for reverse geocoding (free, no API key needed)
const NOMINATIM_API = 'https://nominatim.openstreetmap.org/reverse';

// Cache for country lookups to avoid duplicate API calls
const countryCache = new Map();

// Delay between API requests (Nominatim requires max 1 request per second)
const DELAY_MS = 1100;

// Get country from coordinates using Nominatim reverse geocoding
async function getCountryFromCoordinates(lat, lng) {
  // Check cache first
  const cacheKey = `${lat.toFixed(4)}|${lng.toFixed(4)}`;
  if (countryCache.has(cacheKey)) {
    return countryCache.get(cacheKey);
  }
  
  try {
    const response = await axios.get(NOMINATIM_API, {
      params: {
        lat: lat,
        lon: lng,
        format: 'json',
        addressdetails: 1,
      },
      headers: {
        'User-Agent': 'DiveHub/1.0 (dive site geocoding)',
      },
      timeout: 10000,
    });
    
    if (response.data && response.data.address) {
      const address = response.data.address;
      
      // Prefer country_code (ISO code) for consistent mapping
      const countryCode = address.country_code?.toUpperCase() || 
                         address['ISO3166-1:alpha2']?.toUpperCase();
      
      if (countryCode) {
        // Map country codes to full English names
        const countryName = mapCountryCodeToName(countryCode);
        countryCache.set(cacheKey, countryName);
        return countryName;
      }
      
      // Fallback to country name if code not available
      if (address.country) {
        const countryName = normalizeCountryName(address.country);
        countryCache.set(cacheKey, countryName);
        return countryName;
      }
    }
    
    countryCache.set(cacheKey, null);
    return null;
    
  } catch (error) {
    console.error(`   ⚠️  Ошибка геокодинга (${lat}, ${lng}): ${error.message}`);
    countryCache.set(cacheKey, null);
    return null;
  }
}

// Normalize country names (handle local language names)
function normalizeCountryName(name) {
  const nameMap = {
    'Ελλάς': 'Greece',
    'Ελλάδα': 'Greece',
    'ประเทศไทย': 'Thailand',
    'Kύπρος': 'Cyprus',
    'Kıbrıs': 'Cyprus',
    'Sesel': 'Seychelles',
    'ދިވެހިރާއްޖެ': 'Maldives',
    'Maldivas': 'Maldives',
    'Filipinas': 'Philippines',
    'Pilipinas': 'Philippines',
    'España': 'Spain',
    'Espagne': 'France',
    'France': 'France',
    'Italia': 'Italy',
    'Deutschland': 'Germany',
    'Nederland': 'Netherlands',
    'Norge': 'Norway',
    'Sverige': 'Sweden',
    'Danmark': 'Denmark',
    'Suomi': 'Finland',
    'Polska': 'Poland',
    'Česká republika': 'Czech Republic',
    'Magyarország': 'Hungary',
    'România': 'Romania',
    'България': 'Bulgaria',
    'Hrvatska': 'Croatia',
    'Slovenija': 'Slovenia',
    'Slovensko': 'Slovakia',
    'Eesti': 'Estonia',
    'Latvija': 'Latvia',
    'Lietuva': 'Lithuania',
    'Россия': 'Russia',
    'Türkiye': 'Turkey',
    'Ελλάδα': 'Greece',
  };
  
  // Check if name is in the map
  if (nameMap[name]) {
    return nameMap[name];
  }
  
  // If it's already in English (common names), return as is
  const englishNames = ['Australia', 'USA', 'United States', 'United Kingdom', 'UK', 'Canada', 'Mexico', 'Brazil', 'Argentina', 'Chile', 'Peru', 'Colombia', 'Venezuela', 'Ecuador', 'Costa Rica', 'Belize', 'Honduras', 'Jamaica', 'Bahamas', 'Cayman Islands', 'Egypt', 'Israel', 'Jordan', 'Saudi Arabia', 'United Arab Emirates', 'Oman', 'Qatar', 'Bahrain', 'Kuwait', 'India', 'China', 'Japan', 'South Korea', 'Indonesia', 'Malaysia', 'Singapore', 'Vietnam', 'Cambodia', 'Laos', 'Myanmar', 'Bangladesh', 'Pakistan', 'Sri Lanka', 'Maldives', 'Philippines', 'Thailand', 'Fiji', 'Palau', 'Micronesia', 'Papua New Guinea', 'Solomon Islands', 'New Zealand', 'South Africa', 'Kenya', 'Tanzania', 'Mozambique', 'Madagascar', 'Mauritius', 'Seychelles'];
  
  if (englishNames.includes(name)) {
    return name;
  }
  
  // Return original if not found
  return name;
}

// Map country codes to full country names
function mapCountryCodeToName(code) {
  const countryMap = {
    'EG': 'Egypt',
    'ID': 'Indonesia',
    'MV': 'Maldives',
    'PH': 'Philippines',
    'TH': 'Thailand',
    'MY': 'Malaysia',
    'AU': 'Australia',
    'US': 'USA',
    'MX': 'Mexico',
    'BZ': 'Belize',
    'HN': 'Honduras',
    'CW': 'Curacao',
    'AW': 'Aruba',
    'KY': 'Cayman Islands',
    'BS': 'Bahamas',
    'TC': 'Turks and Caicos',
    'EC': 'Ecuador',
    'CR': 'Costa Rica',
    'PW': 'Palau',
    'FM': 'Micronesia',
    'ZA': 'South Africa',
    'JP': 'Japan',
    'FJ': 'Fiji',
    'FR': 'France',
    'IT': 'Italy',
    'ES': 'Spain',
    'GR': 'Greece',
    'MT': 'Malta',
    'HR': 'Croatia',
    'CN': 'China',
    'TW': 'Taiwan',
    'KR': 'South Korea',
    'VN': 'Vietnam',
    'SG': 'Singapore',
    'BN': 'Brunei',
    'PG': 'Papua New Guinea',
    'SB': 'Solomon Islands',
    'NC': 'New Caledonia',
    'VU': 'Vanuatu',
    'TO': 'Tonga',
    'CK': 'Cook Islands',
    'PF': 'French Polynesia',
    'GU': 'Guam',
    'MP': 'Northern Mariana Islands',
    'MH': 'Marshall Islands',
    'KI': 'Kiribati',
    'NR': 'Nauru',
    'BR': 'Brazil',
    'CO': 'Colombia',
    'VE': 'Venezuela',
    'DO': 'Dominican Republic',
    'JM': 'Jamaica',
    'BB': 'Barbados',
    'GD': 'Grenada',
    'LC': 'Saint Lucia',
    'VC': 'Saint Vincent and the Grenadines',
    'AG': 'Antigua and Barbuda',
    'DM': 'Dominica',
    'KN': 'Saint Kitts and Nevis',
    'IL': 'Israel',
    'JO': 'Jordan',
    'SA': 'Saudi Arabia',
    'SD': 'Sudan',
    'ER': 'Eritrea',
    'DJ': 'Djibouti',
    'YE': 'Yemen',
    'SC': 'Seychelles',
    'MU': 'Mauritius',
    'RE': 'Reunion',
    'MG': 'Madagascar',
    'MZ': 'Mozambique',
    'TZ': 'Tanzania',
    'KE': 'Kenya',
    'CA': 'Canada',
    'GB': 'United Kingdom',
    'IE': 'Ireland',
    'PT': 'Portugal',
    'NO': 'Norway',
    'SE': 'Sweden',
    'DK': 'Denmark',
    'IS': 'Iceland',
    'RU': 'Russia',
    'TR': 'Turkey',
    'CY': 'Cyprus',
    'LB': 'Lebanon',
    'AE': 'United Arab Emirates',
    'OM': 'Oman',
    'QA': 'Qatar',
    'BH': 'Bahrain',
    'KW': 'Kuwait',
    'IQ': 'Iraq',
    'IR': 'Iran',
    'PK': 'Pakistan',
    'IN': 'India',
    'BD': 'Bangladesh',
    'MM': 'Myanmar',
    'LA': 'Laos',
    'KH': 'Cambodia',
    'CL': 'Chile',
    'PE': 'Peru',
    'AR': 'Argentina',
    'UY': 'Uruguay',
    'NZ': 'New Zealand',
    'CN': 'China',
  };
  
  // If it's already a full name, return as is
  if (code.length > 2) {
    return code;
  }
  
  // Return mapped name or code if not found
  return countryMap[code.toUpperCase()] || code;
}

// Sleep function
function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

// Main function
async function addCountriesByCoordinates() {
  console.log('🌍 Добавление стран по координатам...\n');
  
  try {
    await pool.query('SELECT 1');
    console.log('✅ Подключение к базе данных успешно\n');
  } catch (error) {
    console.error('❌ Ошибка подключения:', error.message);
    process.exit(1);
  }
  
  // Get all dive sites without country
  const result = await pool.query(`
    SELECT id, name, latitude, longitude
    FROM dive_sites
    WHERE country IS NULL
    ORDER BY id
  `);
  
  console.log(`📋 Найдено ${result.rows.length} дайвсайтов без страны\n`);
  
  if (result.rows.length === 0) {
    console.log('✅ Все дайвсайты уже имеют страну!\n');
    await pool.end();
    return;
  }
  
  let updated = 0;
  let errors = 0;
  let skipped = 0;
  
  console.log('🌐 Определение стран через Nominatim API...\n');
  console.log('⚠️  Это может занять время (1 запрос в секунду)\n');
  
  for (let i = 0; i < result.rows.length; i++) {
    const site = result.rows[i];
    
    try {
      console.log(`[${i + 1}/${result.rows.length}] ${site.name} (${site.latitude.toFixed(4)}, ${site.longitude.toFixed(4)})...`);
      
      const country = await getCountryFromCoordinates(site.latitude, site.longitude);
      
      if (country) {
        // Update database (updated_at is handled by trigger)
        await pool.query(
          `UPDATE dive_sites SET country = $1 WHERE id = $2`,
          [country, site.id]
        );
        
        console.log(`   ✅ Страна: ${country}\n`);
        updated++;
      } else {
        console.log(`   ⚠️  Страна не определена\n`);
        skipped++;
      }
      
      // Delay between requests (Nominatim rate limit: 1 request per second)
      if (i < result.rows.length - 1) {
        await sleep(DELAY_MS);
      }
      
    } catch (error) {
      console.error(`   ❌ Ошибка: ${error.message}\n`);
      errors++;
    }
  }
  
  console.log('='.repeat(60) + '\n');
  console.log('✅ Обработка завершена!\n');
  console.log(`📊 Статистика:`);
  console.log(`   Обновлено: ${updated}`);
  console.log(`   Пропущено: ${skipped}`);
  console.log(`   Ошибок: ${errors}\n`);
  
  // Show statistics by country
  const statsResult = await pool.query(`
    SELECT country, COUNT(*) as count
    FROM dive_sites
    WHERE country IS NOT NULL
    GROUP BY country
    ORDER BY count DESC
    LIMIT 20
  `);
  
  console.log('📊 Топ стран по количеству дайвсайтов:');
  statsResult.rows.forEach(row => {
    console.log(`   ${row.country}: ${row.count}`);
  });
  console.log();
  
  // Count remaining null countries
  const nullCount = await pool.query(`
    SELECT COUNT(*) as count FROM dive_sites WHERE country IS NULL
  `);
  console.log(`⚠️  Дайвсайтов без страны: ${nullCount.rows[0].count}\n`);
  
  await pool.end();
}

// Run
addCountriesByCoordinates().catch(error => {
  console.error('❌ Критическая ошибка:', error);
  process.exit(1);
});
