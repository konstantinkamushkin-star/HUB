// Script to add countries to remaining dive sites using bounding box logic
const { Pool } = require('pg');

const dbConfig = {
  host: process.env.DB_HOST || 'localhost',
  port: parseInt(process.env.DB_PORT || '5432', 10),
  database: process.env.DB_DATABASE || 'divehub',
  user: process.env.DB_USERNAME || 'admin',
  password: process.env.DB_PASSWORD || 'admin123',
};

const pool = new Pool(dbConfig);

// Country bounding boxes [minLat, minLng, maxLat, maxLng]
const COUNTRY_BBOXES = {
  'Egypt': [22.0, 24.5, 31.7, 37.0],
  'Saudi Arabia': [16.0, 34.5, 32.2, 55.7],
  'Sudan': [8.7, 21.8, 22.2, 38.6],
  'Eritrea': [12.4, 36.4, 18.0, 43.1],
  'Djibouti': [10.9, 41.7, 12.7, 43.7],
  'Israel': [29.5, 34.2, 33.3, 35.8],
  'Jordan': [29.2, 34.9, 33.4, 39.3],
  'Cyprus': [34.6, 32.2, 35.7, 34.6],
  'Greece': [34.8, 19.4, 41.7, 29.6],
  'Turkey': [35.8, 25.7, 42.1, 44.8],
  'Thailand': [5.6, 97.3, 20.5, 105.6],
  'Philippines': [4.2, 116.9, 21.1, 126.6],
  'Indonesia': [-11.0, 95.0, 6.0, 141.0],
  'Malaysia': [0.9, 99.6, 7.4, 119.3],
  'Maldives': [-0.7, 72.7, 7.1, 73.8],
  'India': [6.7, 68.1, 35.5, 97.4],
  'Sri Lanka': [5.9, 79.7, 9.8, 81.9],
  'Australia': [-43.6, 113.3, -10.1, 153.6],
  'New Zealand': [-52.6, 165.8, -29.2, 179.1],
  'Fiji': [-20.7, 177.0, -12.5, 180.0],
  'Palau': [2.9, 131.1, 8.2, 134.7],
  'Micronesia': [1.0, 137.3, 10.1, 163.0],
  'Papua New Guinea': [-12.0, 140.8, -0.3, 159.9],
  'Solomon Islands': [-11.9, 155.5, -5.3, 166.9],
  'USA': [18.9, -179.1, 71.4, -66.9],
  'Mexico': [14.5, -118.4, 32.7, -86.7],
  'Belize': [15.9, -89.2, 18.5, -87.8],
  'Honduras': [12.9, -89.4, 16.0, -83.1],
  'Costa Rica': [8.0, -85.9, 11.2, -82.5],
  'Panama': [7.2, -83.1, 9.6, -77.2],
  'Colombia': [-4.2, -79.0, 12.5, -66.9],
  'Ecuador': [-5.0, -81.1, 1.5, -75.2],
  'Peru': [-18.3, -81.3, -0.0, -68.7],
  'Brazil': [-33.7, -73.9, 5.3, -28.8],
  'Venezuela': [0.6, -73.4, 12.2, -59.8],
  'Cuba': [19.8, -84.9, 23.3, -74.1],
  'Jamaica': [17.7, -78.4, 18.5, -76.2],
  'Bahamas': [20.9, -80.7, 27.3, -72.7],
  'Cayman Islands': [19.2, -81.4, 19.7, -79.7],
  'Turks and Caicos': [21.0, -72.5, 21.9, -71.1],
  'Dominican Republic': [17.5, -72.0, 19.9, -68.3],
  'Puerto Rico': [17.9, -67.9, 18.5, -65.2],
  'US Virgin Islands': [17.7, -65.1, 18.4, -64.6],
  'British Virgin Islands': [18.3, -64.8, 18.8, -64.3],
  'Aruba': [12.4, -70.1, 12.6, -69.9],
  'Curacao': [12.0, -69.2, 12.4, -68.7],
  'Bonaire': [12.0, -68.4, 12.3, -68.2],
  'Japan': [24.2, 122.9, 45.5, 145.8],
  'China': [18.2, 73.5, 53.6, 135.0],
  'South Korea': [33.1, 124.6, 38.6, 132.0],
  'Vietnam': [8.6, 102.1, 23.4, 109.5],
  'Cambodia': [10.5, 102.3, 14.7, 107.6],
  'Myanmar': [9.8, 92.2, 28.5, 101.2],
  'Bangladesh': [20.7, 88.0, 26.6, 92.7],
  'Seychelles': [-10.0, 46.2, -3.7, 56.3],
  'Mauritius': [-20.5, 56.5, -10.3, 63.5],
  'Madagascar': [-25.6, 43.2, -11.9, 50.5],
  'Mozambique': [-26.9, 30.2, -10.5, 40.8],
  'Tanzania': [-11.8, 29.3, -0.9, 40.3],
  'Kenya': [-4.7, 33.9, 5.5, 41.9],
  'South Africa': [-34.8, 16.3, -22.1, 32.9],
  'Namibia': [-28.9, 11.7, -16.9, 25.3],
  'Italy': [36.6, 6.6, 47.1, 18.5],
  'Spain': [35.2, -9.3, 43.8, 4.3],
  'France': [41.3, -5.1, 51.1, 9.6],
  'Croatia': [42.4, 13.5, 46.5, 19.4],
  'Malta': [35.8, 14.2, 36.1, 14.6],
  'Tunisia': [30.2, 7.5, 37.3, 11.6],
  'Algeria': [19.0, -8.7, 37.1, 12.0],
  'Morocco': [21.4, -17.0, 35.9, -1.1],
  'Libya': [19.5, 9.3, 33.2, 25.2],
  'Canada': [41.7, -141.0, 83.1, -52.6],
  'Greenland': [59.8, -73.0, 83.6, -12.2],
  'Iceland': [63.4, -24.5, 66.5, -13.5],
  'Norway': [57.9, 4.6, 80.7, 31.3],
  'Sweden': [55.3, 11.0, 69.1, 24.2],
  'Finland': [59.8, 20.6, 70.1, 31.6],
  'Denmark': [54.6, 8.1, 57.7, 12.7],
  'United Kingdom': [49.9, -8.6, 60.8, 1.8],
  'Ireland': [51.4, -10.5, 55.4, -5.9],
  'Portugal': [36.8, -9.5, 42.2, -6.2],
  'Russia': [41.2, 19.6, 81.9, 180.0],
  'Ukraine': [44.4, 22.1, 52.4, 40.2],
  'Romania': [43.7, 20.2, 48.2, 29.7],
  'Bulgaria': [41.2, 22.4, 44.2, 28.6],
  'Albania': [39.6, 19.3, 42.7, 21.1],
  'Montenegro': [41.9, 18.4, 43.6, 20.4],
  'Bosnia and Herzegovina': [42.6, 15.7, 45.2, 19.6],
  'Slovenia': [45.4, 13.4, 46.9, 16.6],
  'Serbia': [42.2, 18.8, 46.2, 23.0],
  'North Macedonia': [40.8, 20.4, 42.4, 23.0],
  'Kosovo': [41.8, 20.0, 43.3, 21.8],
  'Hungary': [45.7, 16.1, 48.6, 22.9],
  'Slovakia': [47.7, 16.8, 49.6, 22.6],
  'Czech Republic': [48.5, 12.1, 51.1, 18.9],
  'Poland': [49.0, 14.1, 54.8, 24.2],
  'Germany': [47.3, 5.9, 55.1, 15.0],
  'Austria': [46.4, 9.5, 49.0, 17.2],
  'Switzerland': [45.8, 5.9, 47.8, 10.5],
  'Liechtenstein': [47.0, 9.4, 47.3, 9.6],
  'Netherlands': [50.7, 3.3, 53.7, 7.2],
  'Belgium': [49.5, 2.5, 51.5, 6.4],
  'Luxembourg': [49.4, 5.7, 50.2, 6.5],
  'Monaco': [43.7, 7.4, 43.8, 7.4],
  'San Marino': [43.9, 12.4, 43.9, 12.5],
  'Vatican City': [41.9, 12.4, 41.9, 12.5],
  'Andorra': [42.4, 1.4, 42.7, 1.8],
  'Monaco': [43.7, 7.4, 43.8, 7.4],
  'Singapore': [1.1, 103.6, 1.5, 104.0],
  'Brunei': [4.0, 114.0, 5.0, 115.4],
  'East Timor': [-9.5, 124.0, -8.1, 127.3],
  'New Caledonia': [-22.7, 163.5, -19.5, 167.1],
  'French Polynesia': [-27.7, -154.8, -7.7, -134.2],
  'Cook Islands': [-22.0, -166.1, -8.9, -157.3],
  'Samoa': [-14.1, -172.8, -13.4, -171.4],
  'Tonga': [-25.3, -179.1, -15.6, -173.9],
  'Vanuatu': [-20.2, 166.4, -13.0, 170.2],
  'Guam': [13.2, 144.6, 13.7, 145.0],
  'Northern Mariana Islands': [14.1, 144.9, 20.6, 146.1],
  'Marshall Islands': [4.6, 160.8, 14.7, 172.0],
  'Kiribati': [-4.7, -174.5, 4.7, -150.2],
  'Nauru': [-0.6, 166.9, -0.5, 167.0],
  'Tuvalu': [-10.8, 175.8, -5.6, 179.9],
  'Wallis and Futuna': [-14.4, -178.2, -13.2, -176.1],
  'American Samoa': [-14.8, -171.1, -11.0, -168.1],
  'Niue': [-19.1, -170.0, -18.9, -169.8],
  'Pitcairn Islands': [-25.1, -130.8, -24.3, -124.7],
  'Easter Island': [-27.2, -109.5, -27.0, -109.2],
  'Galapagos': [-1.4, -92.0, 1.4, -89.2],
  'Falkland Islands': [-52.9, -61.3, -51.0, -57.7],
  'South Georgia': [-54.8, -38.0, -53.9, -35.8],
  'Antarctica': [-90.0, -180.0, -60.0, 180.0],
};

// Check if point is in bounding box
function isInBBox(lat, lng, bbox) {
  const [minLat, minLng, maxLat, maxLng] = bbox;
  return lat >= minLat && lat <= maxLat && lng >= minLng && lng <= maxLng;
}

// Get country from coordinates using bounding boxes
function getCountryFromBBox(lat, lng) {
  for (const [country, bbox] of Object.entries(COUNTRY_BBOXES)) {
    if (isInBBox(lat, lng, bbox)) {
      return country;
    }
  }
  return null;
}

// Main function
async function addCountriesByBBox() {
  console.log('🌍 Добавление стран по координатам (bounding boxes)...\n');
  
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
  let notFound = 0;
  
  console.log('🔍 Определение стран по bounding boxes...\n');
  
  for (const site of result.rows) {
    const country = getCountryFromBBox(site.latitude, site.longitude);
    
    if (country) {
      try {
        await pool.query(
          `UPDATE dive_sites SET country = $1 WHERE id = $2`,
          [country, site.id]
        );
        updated++;
        
        if (updated % 50 === 0) {
          console.log(`   ✅ Обновлено ${updated}/${result.rows.length}...`);
        }
      } catch (error) {
        console.error(`   ❌ Ошибка при обновлении ${site.name}: ${error.message}`);
      }
    } else {
      notFound++;
    }
  }
  
  console.log(`\n✅ Обработка завершена!\n`);
  console.log(`📊 Статистика:`);
  console.log(`   Обновлено: ${updated}`);
  console.log(`   Не найдено: ${notFound}\n`);
  
  // Show statistics
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
addCountriesByBBox().catch(error => {
  console.error('❌ Критическая ошибка:', error);
  process.exit(1);
});
