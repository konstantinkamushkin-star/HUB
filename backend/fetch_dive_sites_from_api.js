const https = require('https');
const fs = require('fs');
const { Pool } = require('pg');

const API_KEY = 'fa6fa59858msh2fca808ec049113p1b713fjsnb24a63b4f2d1';
const API_HOST = 'world-scuba-diving-sites-api.p.rapidapi.com';

// Регионы мира для покрытия максимума дайвсайтов за минимум запросов
// Каждый регион покрывает большой географический район
const REGIONS = [
  // Европа
  { name: 'Europe', southWestLat: 35, northEastLat: 72, southWestLng: -12, northEastLng: 40 },
  
  // Африка
  { name: 'Africa', southWestLat: -35, northEastLat: 38, southWestLng: -18, northEastLng: 52 },
  
  // Азия (включая Индонезию, Филиппины, Таиланд, Мальдивы)
  { name: 'Asia', southWestLat: -11, northEastLat: 55, southWestLng: 73, northEastLng: 145 },
  
  // Австралия и Океания
  { name: 'Australia_Oceania', southWestLat: -50, northEastLat: 0, southWestLng: 110, northEastLng: 180 },
  
  // Северная Америка
  { name: 'North_America', southWestLat: 10, northEastLat: 72, southWestLng: -180, northEastLng: -50 },
  
  // Южная Америка
  { name: 'South_America', southWestLat: -56, northEastLat: 13, southWestLng: -82, northEastLng: -34 },
  
  // Карибский бассейн (более детально)
  { name: 'Caribbean', southWestLat: 10, northEastLat: 28, southWestLng: -90, northEastLng: -60 },
  
  // Красное море (Египет, Иордания, Израиль)
  { name: 'Red_Sea', southWestLat: 22, northEastLat: 30, southWestLng: 32, northEastLng: 36 },
  
  // Средиземное море
  { name: 'Mediterranean', southWestLat: 30, northEastLat: 46, southWestLng: -6, northEastLng: 36 },
];

const pool = new Pool({
  host: process.env.DB_HOST || 'localhost',
  port: 5432,
  database: process.env.DB_NAME || 'divehub',
  user: process.env.DB_USERNAME || 'admin',
  password: process.env.DB_PASSWORD || '',
});

function makeRequest(region) {
  return new Promise((resolve, reject) => {
    const url = `https://${API_HOST}/divesites/gps?southWestLat=${region.southWestLat}&northEastLat=${region.northEastLat}&southWestLng=${region.southWestLng}&northEastLng=${region.northEastLng}`;
    
    const options = {
      hostname: API_HOST,
      path: `/divesites/gps?southWestLat=${region.southWestLat}&northEastLat=${region.northEastLat}&southWestLng=${region.southWestLng}&northEastLng=${region.northEastLng}`,
      method: 'GET',
      headers: {
        'x-rapidapi-host': API_HOST,
        'x-rapidapi-key': API_KEY,
      },
    };

    const req = https.request(options, (res) => {
      let data = '';

      res.on('data', (chunk) => {
        data += chunk;
      });

      res.on('end', () => {
        try {
          const json = JSON.parse(data);
          resolve(json);
        } catch (error) {
          reject(new Error(`Failed to parse JSON: ${error.message}\nResponse: ${data.substring(0, 200)}`));
        }
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

async function fetchAllDiveSites() {
  const allSites = [];
  const errors = [];

  console.log(`Начинаю получение дайвсайтов из ${REGIONS.length} регионов...\n`);

  for (let i = 0; i < REGIONS.length; i++) {
    const region = REGIONS[i];
    console.log(`[${i + 1}/${REGIONS.length}] Запрос региона: ${region.name}...`);

    try {
      const response = await makeRequest(region);
      
      // Проверяем структуру ответа
      if (Array.isArray(response)) {
        console.log(`  ✅ Получено ${response.length} дайвсайтов`);
        allSites.push(...response);
      } else if (response.data && Array.isArray(response.data)) {
        console.log(`  ✅ Получено ${response.data.length} дайвсайтов`);
        allSites.push(...response.data);
      } else if (response.divesites && Array.isArray(response.divesites)) {
        console.log(`  ✅ Получено ${response.divesites.length} дайвсайтов`);
        allSites.push(...response.divesites);
      } else {
        console.log(`  ⚠️  Неожиданная структура ответа:`, JSON.stringify(response).substring(0, 200));
        errors.push({ region: region.name, error: 'Unexpected response structure', response });
      }

      // Небольшая задержка между запросами, чтобы не перегружать API
      if (i < REGIONS.length - 1) {
        await new Promise(resolve => setTimeout(resolve, 1000));
      }
    } catch (error) {
      console.log(`  ❌ Ошибка: ${error.message}`);
      errors.push({ region: region.name, error: error.message });
    }
  }

  console.log(`\n✅ Всего получено дайвсайтов: ${allSites.length}`);
  console.log(`❌ Ошибок: ${errors.length}`);

  if (errors.length > 0) {
    console.log('\nОшибки:');
    errors.forEach(e => console.log(`  - ${e.region}: ${e.error}`));
  }

  return { sites: allSites, errors };
}

async function normalizeSite(site) {
  // Нормализуем структуру дайвсайта в зависимости от формата API
  let normalized = {
    name: site.name || site.diveSiteName || site.title || 'Unknown',
    latitude: parseFloat(site.latitude || site.lat || site.coordinates?.latitude || 0),
    longitude: parseFloat(site.longitude || site.lng || site.coordinates?.longitude || 0),
    description: site.description || site.desc || site.about || '',
    country: site.country || site.countryName || '',
    region: site.region || site.area || site.location || '',
    depth_min: site.depthMin || site.minDepth || site.depth?.min || null,
    depth_max: site.depthMax || site.maxDepth || site.depth?.max || null,
    difficulty: site.difficulty || site.difficultyLevel || 1,
    site_types: site.siteTypes || site.types || site.type || [],
    water_temp_min: site.waterTempMin || site.tempMin || null,
    water_temp_max: site.waterTempMax || site.tempMax || null,
    average_rating: site.averageRating || site.rating || 0,
    review_count: site.reviewCount || site.reviews || 0,
    marine_life: site.marineLife || site.fish || [],
  };

  // Проверяем валидность координат
  if (!normalized.latitude || !normalized.longitude || 
      isNaN(normalized.latitude) || isNaN(normalized.longitude) ||
      normalized.latitude < -90 || normalized.latitude > 90 ||
      normalized.longitude < -180 || normalized.longitude > 180) {
    return null;
  }

  return normalized;
}

async function importToDatabase(sites) {
  const client = await pool.connect();
  let imported = 0;
  let skipped = 0;
  let errors = 0;

  try {
    await client.query('BEGIN');

    console.log(`\nНачинаю импорт ${sites.length} дайвсайтов в базу данных...\n`);

    for (let i = 0; i < sites.length; i++) {
      const site = sites[i];
      
      if (i % 100 === 0 && i > 0) {
        console.log(`  Обработано: ${i}/${sites.length} (импортировано: ${imported}, пропущено: ${skipped})`);
      }

      try {
        const normalized = normalizeSite(site);
        
        if (!normalized) {
          skipped++;
          continue;
        }

        // Проверяем, существует ли уже такой дайвсайт
        const existing = await client.query(
          `SELECT id FROM dive_sites 
           WHERE name = $1 
           AND ABS(latitude - $2) < 0.001 
           AND ABS(longitude - $3) < 0.001`,
          [normalized.name, normalized.latitude, normalized.longitude]
        );

        if (existing.rows.length > 0) {
          skipped++;
          continue;
        }

        // Определяем site_types
        let siteTypes = [];
        if (Array.isArray(normalized.site_types)) {
          siteTypes = normalized.site_types;
        } else if (typeof normalized.site_types === 'string') {
          siteTypes = [normalized.site_types];
        }

        // Определяем difficulty_level (1-5)
        let difficultyLevel = 1;
        if (normalized.difficulty) {
          difficultyLevel = Math.max(1, Math.min(5, parseInt(normalized.difficulty) || 1));
        }

        // Вставляем дайвсайт
        await client.query(
          `INSERT INTO dive_sites (
            name, description, location, country, region, site_types, difficulty_level,
            depth_min, depth_max, water_temp_min, water_temp_max, average_rating, review_count,
            marine_life, is_active, created_at, updated_at
          ) VALUES (
            $1, $2, ST_SetSRID(ST_MakePoint($3, $4), 4326)::geography,
            $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, NOW(), NOW()
          ) RETURNING id`,
          [
            normalized.name,
            normalized.description || '',
            normalized.longitude,
            normalized.latitude,
            normalized.country || '',
            normalized.region || '',
            siteTypes,
            difficultyLevel,
            normalized.depth_min,
            normalized.depth_max,
            normalized.water_temp_min,
            normalized.water_temp_max,
            normalized.average_rating || 0,
            normalized.review_count || 0,
            Array.isArray(normalized.marine_life) ? normalized.marine_life : [],
            true,
          ]
        );

        imported++;
      } catch (error) {
        errors++;
        if (errors <= 10) {
          console.error(`  ❌ Ошибка при импорте "${site.name || 'Unknown'}":`, error.message);
        }
      }
    }

    await client.query('COMMIT');
    console.log(`\n✅ Импорт завершен:`);
    console.log(`   Импортировано: ${imported}`);
    console.log(`   Пропущено (дубликаты): ${skipped}`);
    console.log(`   Ошибок: ${errors}`);
  } catch (error) {
    await client.query('ROLLBACK');
    console.error('❌ Критическая ошибка:', error);
    throw error;
  } finally {
    client.release();
  }
}

async function main() {
  try {
    // Сохраняем сырые данные в файл для отладки
    const { sites, errors } = await fetchAllDiveSites();
    
    if (sites.length === 0) {
      console.log('\n❌ Не получено ни одного дайвсайта. Проверьте API ключ и доступность API.');
      return;
    }

    // Сохраняем в файл для проверки
    fs.writeFileSync('dive_sites_from_api.json', JSON.stringify(sites, null, 2));
    console.log(`\n✅ Данные сохранены в dive_sites_from_api.json`);

    // Импортируем в базу данных
    await importToDatabase(sites);

    const totalCount = await pool.query('SELECT COUNT(*) as total FROM dive_sites');
    console.log(`\n📊 Всего дайвсайтов в базе: ${totalCount.rows[0].total}`);

    await pool.end();
  } catch (error) {
    console.error('❌ Фатальная ошибка:', error);
    await pool.end();
    process.exit(1);
  }
}

main();
