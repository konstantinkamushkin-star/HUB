#!/usr/bin/env node

/**
 * Script to create 10 shops with users (logins and passwords)
 * 
 * Usage:
 *   node create_10_shops.js
 * 
 * Environment variables:
 *   DB_HOST=localhost
 *   DB_PORT=5432
 *   DB_USERNAME=postgres
 *   DB_PASSWORD=postgres
 *   DB_DATABASE=divehub
 */

const { Client } = require('pg');
const bcrypt = require('bcryptjs');

// Load .env file if it exists
try {
  const fs = require('fs');
  const path = require('path');
  const envFile = fs.readFileSync(path.join(__dirname, '.env'), 'utf8');
  envFile.split('\n').forEach(line => {
    const [key, ...valueParts] = line.split('=');
    if (key && valueParts.length > 0) {
      const value = valueParts.join('=').trim();
      if (value && !process.env[key]) {
        process.env[key] = value;
      }
    }
  });
} catch (e) {
  // .env file doesn't exist, use defaults
}

// Database configuration
const dbConfig = {
  host: process.env.DB_HOST || 'localhost',
  port: parseInt(process.env.DB_PORT || '5432'),
  user: process.env.DB_USERNAME || 'admin',
  password: process.env.DB_PASSWORD || '',
  database: process.env.DB_DATABASE || 'divehub',
};

// Shop data with full information
const SHOPS_DATA = [
  {
    name: 'Deep Blue Diving Equipment',
    description: 'Premier diving equipment store offering top brands like Scubapro, Aqualung, and Mares. We provide expert fitting services and maintenance for all your diving gear. Our knowledgeable staff will help you find the perfect equipment for your diving adventures.',
    localizedName: {
      en: 'Deep Blue Diving Equipment',
      ru: 'Глубокий Синий Дайвинг Оборудование'
    },
    localizedDescription: {
      en: 'Premier diving equipment store offering top brands like Scubapro, Aqualung, and Mares.',
      ru: 'Первоклассный магазин дайвинг-оборудования, предлагающий ведущие бренды, такие как Scubapro, Aqualung и Mares.'
    },
    type: 'offline',
    brands: ['Scubapro', 'Aqualung', 'Mares', 'Cressi', 'Oceanic'],
    serviceAvailable: true,
    latitude: 27.9158,
    longitude: 34.3300,
    country: 'Egypt',
    city: 'Sharm El Sheikh',
    address: 'Naama Bay, Sharm El Sheikh, Egypt',
    email: 'shop1@deepblue.com',
    phone: '+20 69 360 1234',
    website: 'https://www.deepblue-diving.com',
    photoUrls: [
      'https://images.unsplash.com/photo-1559827260-dc66d52bef19?w=800',
      'https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=800',
      'https://images.unsplash.com/photo-1583212292454-1fe6229603b7?w=800'
    ],
    user: {
      email: 'shop1@deepblue.com',
      password: 'Shop1@2024',
      firstName: 'Ahmed',
      lastName: 'Hassan',
      role: 'DIVE_CENTER_ADMIN'
    }
  },
  {
    name: 'Coral Reef Gear Online',
    description: 'Your one-stop online shop for all diving needs. We ship worldwide and offer competitive prices on premium diving equipment. Specializing in wetsuits, BCDs, regulators, and dive computers. Fast shipping and excellent customer service guaranteed.',
    localizedName: {
      en: 'Coral Reef Gear Online',
      ru: 'Коралловый Риф Снаряжение Онлайн'
    },
    localizedDescription: {
      en: 'Your one-stop online shop for all diving needs. We ship worldwide.',
      ru: 'Ваш универсальный интернет-магазин для всех дайвинг-потребностей. Доставляем по всему миру.'
    },
    type: 'online',
    brands: ['Scubapro', 'Aqualung', 'Suunto', 'Garmin', 'Shearwater'],
    serviceAvailable: true,
    latitude: 25.2048,
    longitude: 55.2708,
    country: 'United Arab Emirates',
    city: 'Dubai',
    address: 'Dubai Marina, Dubai, UAE',
    email: 'shop2@coralreef.com',
    phone: '+971 4 123 4567',
    website: 'https://www.coralreefgear.com',
    photoUrls: [
      'https://images.unsplash.com/photo-1559827260-dc66d52bef19?w=800',
      'https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=800'
    ],
    user: {
      email: 'shop2@coralreef.com',
      password: 'Shop2@2024',
      firstName: 'Mohammed',
      lastName: 'Al-Rashid',
      role: 'DIVE_CENTER_ADMIN'
    }
  },
  {
    name: 'Tropical Diving Supplies',
    description: 'Family-owned diving equipment store serving the diving community for over 20 years. We offer a wide selection of gear from entry-level to professional equipment. Our experienced team provides personalized service and expert advice.',
    localizedName: {
      en: 'Tropical Diving Supplies',
      ru: 'Тропические Дайвинг Поставки'
    },
    localizedDescription: {
      en: 'Family-owned diving equipment store serving the diving community for over 20 years.',
      ru: 'Семейный магазин дайвинг-оборудования, обслуживающий дайвинг-сообщество более 20 лет.'
    },
    type: 'offline',
    brands: ['Mares', 'Cressi', 'Beuchat', 'Tusa', 'Atomic Aquatics'],
    serviceAvailable: true,
    latitude: 7.2906,
    longitude: 80.6337,
    country: 'Sri Lanka',
    city: 'Colombo',
    address: 'Galle Road, Colombo 03, Sri Lanka',
    email: 'shop3@tropical.com',
    phone: '+94 11 234 5678',
    website: 'https://www.tropicaldiving.com',
    photoUrls: [
      'https://images.unsplash.com/photo-1583212292454-1fe6229603b7?w=800',
      'https://images.unsplash.com/photo-1559827260-dc66d52bef19?w=800'
    ],
    user: {
      email: 'shop3@tropical.com',
      password: 'Shop3@2024',
      firstName: 'Priya',
      lastName: 'Fernando',
      role: 'DIVE_CENTER_ADMIN'
    }
  },
  {
    name: 'Ocean Pro Equipment',
    description: 'Professional diving equipment supplier for dive centers and individual divers. We stock the latest models from leading manufacturers and offer bulk pricing for dive centers. Equipment testing and certification services available.',
    localizedName: {
      en: 'Ocean Pro Equipment',
      ru: 'Океан Про Оборудование'
    },
    localizedDescription: {
      en: 'Professional diving equipment supplier for dive centers and individual divers.',
      ru: 'Профессиональный поставщик дайвинг-оборудования для дайв-центров и индивидуальных дайверов.'
    },
    type: 'offline',
    brands: ['Scubapro', 'Aqualung', 'Poseidon', 'Hollis', 'Fourth Element'],
    serviceAvailable: true,
    latitude: -8.3405,
    longitude: 115.0920,
    country: 'Indonesia',
    city: 'Bali',
    address: 'Seminyak, Bali, Indonesia',
    email: 'shop4@oceanpro.com',
    phone: '+62 361 123 456',
    website: 'https://www.oceanproequipment.com',
    photoUrls: [
      'https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=800',
      'https://images.unsplash.com/photo-1583212292454-1fe6229603b7?w=800'
    ],
    user: {
      email: 'shop4@oceanpro.com',
      password: 'Shop4@2024',
      firstName: 'Made',
      lastName: 'Wijaya',
      role: 'DIVE_CENTER_ADMIN'
    }
  },
  {
    name: 'Dive Tech Global',
    description: 'Online marketplace for diving equipment with worldwide shipping. We offer competitive prices, secure payment options, and a 30-day return policy. Specializing in dive computers, regulators, and technical diving equipment.',
    localizedName: {
      en: 'Dive Tech Global',
      ru: 'Дайв Тех Глобал'
    },
    localizedDescription: {
      en: 'Online marketplace for diving equipment with worldwide shipping.',
      ru: 'Онлайн-маркетплейс для дайвинг-оборудования с доставкой по всему миру.'
    },
    type: 'online',
    brands: ['Shearwater', 'Suunto', 'Garmin', 'Scubapro', 'Atomic Aquatics'],
    serviceAvailable: true,
    latitude: 1.3521,
    longitude: 103.8198,
    country: 'Singapore',
    city: 'Singapore',
    address: 'Marina Bay, Singapore',
    email: 'shop5@divetech.com',
    phone: '+65 6123 4567',
    website: 'https://www.divetechglobal.com',
    photoUrls: [
      'https://images.unsplash.com/photo-1559827260-dc66d52bef19?w=800',
      'https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=800'
    ],
    user: {
      email: 'shop5@divetech.com',
      password: 'Shop5@2024',
      firstName: 'Wei',
      lastName: 'Tan',
      role: 'DIVE_CENTER_ADMIN'
    }
  },
  {
    name: 'Blue Water Diving Store',
    description: 'Full-service diving equipment store with rental and repair services. We carry all major brands and offer expert fitting for wetsuits and BCDs. Our certified technicians provide equipment servicing and annual inspections.',
    localizedName: {
      en: 'Blue Water Diving Store',
      ru: 'Синяя Вода Дайвинг Магазин'
    },
    localizedDescription: {
      en: 'Full-service diving equipment store with rental and repair services.',
      ru: 'Полносервисный магазин дайвинг-оборудования с услугами аренды и ремонта.'
    },
    type: 'offline',
    brands: ['Aqualung', 'Mares', 'Cressi', 'Beuchat', 'Tusa'],
    serviceAvailable: true,
    latitude: 10.3157,
    longitude: 123.8854,
    country: 'Philippines',
    city: 'Cebu',
    address: 'Mactan Island, Cebu, Philippines',
    email: 'shop6@bluewater.com',
    phone: '+63 32 123 4567',
    website: 'https://www.bluewaterdiving.com',
    photoUrls: [
      'https://images.unsplash.com/photo-1583212292454-1fe6229603b7?w=800',
      'https://images.unsplash.com/photo-1559827260-dc66d52bef19?w=800'
    ],
    user: {
      email: 'shop6@bluewater.com',
      password: 'Shop6@2024',
      firstName: 'Maria',
      lastName: 'Santos',
      role: 'DIVE_CENTER_ADMIN'
    }
  },
  {
    name: 'Reef Masters Equipment',
    description: 'Specializing in technical diving equipment and rebreathers. We serve both recreational and technical divers with expert knowledge and personalized service. Authorized dealer for major technical diving brands.',
    localizedName: {
      en: 'Reef Masters Equipment',
      ru: 'Риф Мастера Оборудование'
    },
    localizedDescription: {
      en: 'Specializing in technical diving equipment and rebreathers.',
      ru: 'Специализация на техническом дайвинг-оборудовании и ребризерах.'
    },
    type: 'offline',
    brands: ['Hollis', 'Poseidon', 'Shearwater', 'Fourth Element', 'Santi'],
    serviceAvailable: true,
    latitude: -12.4634,
    longitude: 130.8456,
    country: 'Australia',
    city: 'Darwin',
    address: 'Darwin Waterfront, Northern Territory, Australia',
    email: 'shop7@reefmasters.com',
    phone: '+61 8 1234 5678',
    website: 'https://www.reefmasters.com',
    photoUrls: [
      'https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=800',
      'https://images.unsplash.com/photo-1583212292454-1fe6229603b7?w=800'
    ],
    user: {
      email: 'shop7@reefmasters.com',
      password: 'Shop7@2024',
      firstName: 'James',
      lastName: 'Mitchell',
      role: 'DIVE_CENTER_ADMIN'
    }
  },
  {
    name: 'Aqua World Online',
    description: 'Your trusted online source for diving equipment. We offer daily deals, free shipping on orders over $200, and a comprehensive selection of gear. Customer reviews and detailed product descriptions help you make informed decisions.',
    localizedName: {
      en: 'Aqua World Online',
      ru: 'Аква Мир Онлайн'
    },
    localizedDescription: {
      en: 'Your trusted online source for diving equipment with daily deals.',
      ru: 'Ваш надежный онлайн-источник дайвинг-оборудования с ежедневными предложениями.'
    },
    type: 'online',
    brands: ['Scubapro', 'Aqualung', 'Mares', 'Cressi', 'Oceanic', 'Tusa'],
    serviceAvailable: true,
    latitude: 19.4326,
    longitude: -99.1332,
    country: 'Mexico',
    city: 'Mexico City',
    address: 'Mexico City, Mexico',
    email: 'shop8@aquaworld.com',
    phone: '+52 55 1234 5678',
    website: 'https://www.aquaworldonline.com',
    photoUrls: [
      'https://images.unsplash.com/photo-1559827260-dc66d52bef19?w=800',
      'https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=800'
    ],
    user: {
      email: 'shop8@aquaworld.com',
      password: 'Shop8@2024',
      firstName: 'Carlos',
      lastName: 'Rodriguez',
      role: 'DIVE_CENTER_ADMIN'
    }
  },
  {
    name: 'Pacific Diving Gear',
    description: 'Local diving equipment store with a passion for the ocean. We offer personalized service, equipment rentals, and maintenance. Our team of experienced divers will help you find the perfect gear for your next adventure.',
    localizedName: {
      en: 'Pacific Diving Gear',
      ru: 'Тихоокеанское Дайвинг Снаряжение'
    },
    localizedDescription: {
      en: 'Local diving equipment store with a passion for the ocean.',
      ru: 'Местный магазин дайвинг-оборудования с страстью к океану.'
    },
    type: 'offline',
    brands: ['Mares', 'Cressi', 'Beuchat', 'Tusa', 'Atomic Aquatics'],
    serviceAvailable: true,
    latitude: 21.3099,
    longitude: -157.8581,
    country: 'United States',
    city: 'Honolulu',
    address: 'Waikiki, Honolulu, Hawaii, USA',
    email: 'shop9@pacific.com',
    phone: '+1 808 123 4567',
    website: 'https://www.pacificdivinggear.com',
    photoUrls: [
      'https://images.unsplash.com/photo-1583212292454-1fe6229603b7?w=800',
      'https://images.unsplash.com/photo-1559827260-dc66d52bef19?w=800'
    ],
    user: {
      email: 'shop9@pacific.com',
      password: 'Shop9@2024',
      firstName: 'Kai',
      lastName: 'Nakamura',
      role: 'DIVE_CENTER_ADMIN'
    }
  },
  {
    name: 'Mediterranean Dive Shop',
    description: 'Premier diving equipment retailer in the Mediterranean region. We offer a wide range of equipment for all diving disciplines, from recreational to technical diving. Expert staff and competitive prices make us the preferred choice for divers.',
    localizedName: {
      en: 'Mediterranean Dive Shop',
      ru: 'Средиземноморский Дайв Магазин'
    },
    localizedDescription: {
      en: 'Premier diving equipment retailer in the Mediterranean region.',
      ru: 'Первоклассный розничный продавец дайвинг-оборудования в Средиземноморском регионе.'
    },
    type: 'offline',
    brands: ['Scubapro', 'Aqualung', 'Mares', 'Cressi', 'Beuchat'],
    serviceAvailable: true,
    latitude: 36.3932,
    longitude: 25.4615,
    country: 'Greece',
    city: 'Santorini',
    address: 'Fira, Santorini, Greece',
    email: 'shop10@mediterranean.com',
    phone: '+30 2286 123 456',
    website: 'https://www.mediterraneandiveshop.com',
    photoUrls: [
      'https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=800',
      'https://images.unsplash.com/photo-1583212292454-1fe6229603b7?w=800'
    ],
    user: {
      email: 'shop10@mediterranean.com',
      password: 'Shop10@2024',
      firstName: 'Dimitris',
      lastName: 'Papadopoulos',
      role: 'DIVE_CENTER_ADMIN'
    }
  }
];

async function main() {
  const client = new Client(dbConfig);
  
  try {
    console.log('🔌 Connecting to database...');
    await client.connect();
    console.log('✅ Connected to database');
    
    const createdShops = [];
    
    for (let i = 0; i < SHOPS_DATA.length; i++) {
      const shopData = SHOPS_DATA[i];
      console.log(`\n📦 Creating shop ${i + 1}/10: ${shopData.name}`);
      
      // Check if user already exists
      const existingUser = await client.query(
        'SELECT id FROM users WHERE email = $1',
        [shopData.user.email]
      );
      
      let userId;
      
      if (existingUser.rows.length > 0) {
        console.log(`  ⚠️  User already exists, using existing user...`);
        userId = existingUser.rows[0].id;
      } else {
        // Create user
        console.log(`  👤 Creating user: ${shopData.user.email}`);
        const hashedPassword = await bcrypt.hash(shopData.user.password, 10);

        const userResult = await client.query(
          `INSERT INTO users (email, password, "firstName", "lastName", role)
           VALUES ($1, $2, $3, $4, $5)
           RETURNING id`,
          [
            shopData.user.email,
            hashedPassword,
            shopData.user.firstName,
            shopData.user.lastName,
            shopData.user.role
          ]
        );
        
        userId = userResult.rows[0].id;
        console.log(`  ✅ User created with ID: ${userId}`);
      }
      
      // Check if shop already exists
      const existingShop = await client.query(
        'SELECT id FROM shops WHERE email = $1',
        [shopData.email]
      );
      
      let shopId;
      
      if (existingShop.rows.length > 0) {
        console.log(`  ⚠️  Shop already exists, updating...`);
        shopId = existingShop.rows[0].id;
        
        // Update shop
        await client.query(
          `UPDATE shops SET
            name = $1,
            description = $2,
            localized_name = $3,
            localized_description = $4,
            type = $5,
            brands = $6,
            service_available = $7,
            location = ST_SetSRID(ST_MakePoint($8, $9), 4326)::geography,
            country = $10,
            city = $11,
            address = $12,
            email = $13,
            phone = $14,
            website = $15,
            photo_urls = $16,
            owner_id = $17,
            is_active = true,
            updated_at = NOW()
           WHERE id = $18`,
          [
            shopData.name,
            shopData.description,
            JSON.stringify(shopData.localizedName),
            JSON.stringify(shopData.localizedDescription),
            shopData.type,
            shopData.brands,
            shopData.serviceAvailable,
            shopData.longitude,
            shopData.latitude,
            shopData.country,
            shopData.city,
            shopData.address,
            shopData.email,
            shopData.phone,
            shopData.website,
            shopData.photoUrls,
            userId,
            shopId
          ]
        );
        console.log(`  ✅ Shop updated`);
      } else {
        // Create shop
        console.log(`  🏪 Creating shop...`);
        const shopResult = await client.query(
          `INSERT INTO shops (
            name, description, localized_name, localized_description,
            type, brands, service_available,
            location,
            country, city, address,
            email, phone, website,
            photo_urls, owner_id, is_active
          )
          VALUES (
            $1, $2, $3, $4, $5, $6, $7,
            ST_SetSRID(ST_MakePoint($8, $9), 4326)::geography,
            $10, $11, $12, $13, $14, $15, $16, $17, $18
          )
          RETURNING id`,
          [
            shopData.name,
            shopData.description,
            JSON.stringify(shopData.localizedName),
            JSON.stringify(shopData.localizedDescription),
            shopData.type,
            shopData.brands,
            shopData.serviceAvailable,
            shopData.longitude,
            shopData.latitude,
            shopData.country,
            shopData.city,
            shopData.address,
            shopData.email,
            shopData.phone,
            shopData.website,
            shopData.photoUrls,
            userId,
            true
          ]
        );
        
        shopId = shopResult.rows[0].id;
        console.log(`  ✅ Shop created with ID: ${shopId}`);
      }
      
      createdShops.push({
        shopId,
        name: shopData.name,
        email: shopData.user.email,
        password: shopData.user.password
      });
    }
    
    console.log('\n🎉 All shops created successfully!');
    console.log('\n📋 Summary:');
    console.log('═══════════════════════════════════════════════════════════');
    createdShops.forEach((shop, index) => {
      console.log(`\n${index + 1}. ${shop.name}`);
      console.log(`   📧 Email: ${shop.email}`);
      console.log(`   🔑 Password: ${shop.password}`);
    });
    console.log('\n═══════════════════════════════════════════════════════════');
    
  } catch (error) {
    console.error('❌ Error:', error);
    process.exit(1);
  } finally {
    await client.end();
  }
}

main();
