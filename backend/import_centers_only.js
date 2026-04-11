// Quick script to import only dive centers
const { Pool } = require('pg');

const pool = new Pool({
  host: 'localhost',
  port: 5432,
  database: 'divehub',
  user: 'admin',
  password: process.env.DB_PASSWORD || '',
});

const diveCenters = [
  {
    name: 'Blue Ocean Dive Center',
    description: 'Professional dive center with certified instructors and modern equipment',
    email: 'info@blueoceandive.com',
    phone: '+52-998-123-4567',
    website: 'https://blueoceandive.com',
    address: '123 Beach Road, Cancun, Mexico',
    latitude: 20.4250,
    longitude: -86.9215,
    country: 'Mexico',
    city: 'Cancun',
    services: ['courses', 'equipment rental', 'guided dives', 'night dives', 'wreck dives'],
    averageRating: 4.7,
    reviewCount: 45,
  },
  {
    name: 'Coral Reef Dive Center',
    description: 'Eco-friendly dive center specializing in reef conservation',
    email: 'info@coralreef.com',
    phone: '+501-223-4567',
    website: 'https://coralreef.com',
    address: '456 Ocean Drive, Ambergris Caye, Belize',
    latitude: 17.9167,
    longitude: -87.9500,
    country: 'Belize',
    city: 'Ambergris Caye',
    services: ['courses', 'equipment rental', 'guided dives', 'reef tours'],
    averageRating: 4.8,
    reviewCount: 67,
  },
];

async function main() {
  try {
    await pool.connect();
    console.log('🔌 Подключено к базе данных divehub\n');
    
    let imported = 0;
    let skipped = 0;
    let errors = 0;
    
    console.log(`Обработка ${diveCenters.length} дайвцентров...\n`);
    
    for (const center of diveCenters) {
      try {
        // Check if center already exists
        const existing = await pool.query(
          `SELECT id FROM dive_centers WHERE email = $1 OR (name = $2 AND latitude = $3 AND longitude = $4)`,
          [center.email, center.name, center.latitude, center.longitude]
        );
        
        if (existing.rows.length > 0) {
          skipped++;
          console.log(`  ⏭️  Пропущен: ${center.name} (уже существует)`);
          continue;
        }
        
        // Insert new center
        await pool.query(
          `INSERT INTO dive_centers (
            name, description, location, country, city, address,
            email, phone, website, services,
            average_rating, review_count, is_active
          ) VALUES (
            $1, $2, ST_SetSRID(ST_MakePoint($3, $4), 4326)::geography,
            $5, $6, $7, $8, $9, $10, $11, $12, $13, true
          ) RETURNING id`,
          [
            center.name,
            center.description || '',
            center.longitude,
            center.latitude,
            center.country || null,
            center.city || null,
            center.address || null,
            center.email || null,
            center.phone || null,
            center.website || null,
            center.services || [],
            center.averageRating || 0,
            center.reviewCount || 0,
          ]
        );
        
        imported++;
        console.log(`  ✅ Импортирован: ${center.name}`);
      } catch (error) {
        errors++;
        console.error(`  ❌ Ошибка при импорте "${center.name}":`, error.message);
      }
    }
    
    console.log(`\n✅ Импорт завершен: ${imported} новых, ${skipped} пропущено, ${errors} ошибок\n`);
    
  } catch (error) {
    console.error('❌ Ошибка:', error);
    process.exit(1);
  } finally {
    await pool.end();
  }
}

main();
