#!/usr/bin/env node

/**
 * Script to create test dive center with user, courses, and trips
 * 
 * Usage:
 *   node create_test_dive_center.js
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

// Test data configuration
const TEST_USER = {
  email: 'ww@ww.ww',
  password: '12345678',
  firstName: 'Test',
  lastName: 'Dive Center',
  role: 'DIVE_CENTER_ADMIN',
};

// Sample photo URLs (using placeholder images)
const SAMPLE_PHOTOS = [
  'https://images.unsplash.com/photo-1583212292454-1fe6229603b7?w=800',
  'https://images.unsplash.com/photo-1559827260-dc66d52bef19?w=800',
  'https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=800',
  'https://images.unsplash.com/photo-1559827260-dc66d52bef19?w=800',
  'https://images.unsplash.com/photo-1583212292454-1fe6229603b7?w=800',
];

// Course data
const COURSES = [
  {
    name: 'Open Water Diver',
    level: 'basic',
    description: 'Learn the fundamentals of scuba diving. Perfect for beginners.',
    trainingSystems: ['PADI', 'SSI'],
    duration: 4,
    prerequisites: [],
    modules: [
      { id: '1', title: 'Theory', description: 'Basic diving theory', duration: 4, moduleType: 'theory', order: 1 },
      { id: '2', title: 'Confined Water', description: 'Pool training', duration: 5, moduleType: 'confined_water', order: 2 },
      { id: '3', title: 'Open Water Dives', description: '4 open water dives', duration: 8, moduleType: 'open_water', order: 3 },
    ],
  },
  {
    name: 'Advanced Open Water',
    level: 'advanced',
    description: 'Expand your diving skills with 5 adventure dives.',
    trainingSystems: ['PADI'],
    duration: 2,
    prerequisites: ['Open Water Diver'],
    modules: [
      { id: '1', title: 'Deep Dive', description: 'Deep diving techniques', duration: 3, moduleType: 'open_water', order: 1 },
      { id: '2', title: 'Navigation', description: 'Underwater navigation', duration: 2, moduleType: 'open_water', order: 2 },
    ],
  },
  {
    name: 'Rescue Diver',
    level: 'advanced',
    description: 'Learn to prevent and manage problems in the water.',
    trainingSystems: ['PADI'],
    duration: 3,
    prerequisites: ['Advanced Open Water'],
    modules: [
      { id: '1', title: 'Rescue Theory', description: 'Rescue techniques theory', duration: 4, moduleType: 'theory', order: 1 },
      { id: '2', title: 'Rescue Exercises', description: 'Practical rescue exercises', duration: 6, moduleType: 'open_water', order: 2 },
    ],
  },
  {
    name: 'Divemaster',
    level: 'professional',
    description: 'First professional level. Lead certified divers.',
    trainingSystems: ['PADI'],
    duration: 14,
    prerequisites: ['Rescue Diver'],
    modules: [
      { id: '1', title: 'Divemaster Theory', description: 'Professional diving theory', duration: 20, moduleType: 'theory', order: 1 },
      { id: '2', title: 'Practical Training', description: 'Practical leadership training', duration: 40, moduleType: 'open_water', order: 2 },
    ],
  },
  {
    name: 'Enriched Air (Nitrox)',
    level: 'specialization',
    description: 'Learn to dive with enriched air nitrox.',
    trainingSystems: ['PADI', 'SSI'],
    duration: 1,
    prerequisites: ['Open Water Diver'],
    modules: [
      { id: '1', title: 'Nitrox Theory', description: 'Nitrox diving theory', duration: 2, moduleType: 'theory', order: 1 },
      { id: '2', title: 'Nitrox Dive', description: 'Practical nitrox dive', duration: 1, moduleType: 'open_water', order: 2 },
    ],
  },
  {
    name: 'Deep Diver',
    level: 'specialization',
    description: 'Learn to dive safely to 40 meters.',
    trainingSystems: ['PADI'],
    duration: 2,
    prerequisites: ['Advanced Open Water'],
    modules: [
      { id: '1', title: 'Deep Diving Theory', description: 'Deep diving safety', duration: 2, moduleType: 'theory', order: 1 },
      { id: '2', title: 'Deep Dives', description: '4 deep dives', duration: 4, moduleType: 'open_water', order: 2 },
    ],
  },
  {
    name: 'Wreck Diver',
    level: 'specialization',
    description: 'Explore sunken ships and aircraft safely.',
    trainingSystems: ['PADI'],
    duration: 2,
    prerequisites: ['Advanced Open Water'],
    modules: [
      { id: '1', title: 'Wreck Diving Theory', description: 'Wreck diving safety', duration: 2, moduleType: 'theory', order: 1 },
      { id: '2', title: 'Wreck Dives', description: '4 wreck dives', duration: 4, moduleType: 'open_water', order: 2 },
    ],
  },
  {
    name: 'Night Diver',
    level: 'specialization',
    description: 'Experience the underwater world after dark.',
    trainingSystems: ['PADI'],
    duration: 1,
    prerequisites: ['Open Water Diver'],
    modules: [
      { id: '1', title: 'Night Diving Theory', description: 'Night diving techniques', duration: 1, moduleType: 'theory', order: 1 },
      { id: '2', title: 'Night Dives', description: '3 night dives', duration: 3, moduleType: 'open_water', order: 2 },
    ],
  },
  {
    name: 'Underwater Photography',
    level: 'specialization',
    description: 'Capture amazing underwater moments.',
    trainingSystems: ['PADI'],
    duration: 2,
    prerequisites: ['Open Water Diver'],
    modules: [
      { id: '1', title: 'Photography Theory', description: 'Underwater photography basics', duration: 2, moduleType: 'theory', order: 1 },
      { id: '2', title: 'Photo Dives', description: '3 photography dives', duration: 3, moduleType: 'open_water', order: 2 },
    ],
  },
  {
    name: 'Peak Performance Buoyancy',
    level: 'specialization',
    description: 'Master your buoyancy control.',
    trainingSystems: ['PADI'],
    duration: 1,
    prerequisites: ['Open Water Diver'],
    modules: [
      { id: '1', title: 'Buoyancy Theory', description: 'Buoyancy control techniques', duration: 1, moduleType: 'theory', order: 1 },
      { id: '2', title: 'Buoyancy Practice', description: '2 practice dives', duration: 2, moduleType: 'open_water', order: 2 },
    ],
  },
];

// Trip data
function generateTrips(diveCenterId, courseIds) {
  const now = new Date();
  const trips = [];
  
  for (let i = 0; i < 10; i++) {
    const startDate = new Date(now);
    startDate.setDate(startDate.getDate() + (i * 7) + 14); // Start 2 weeks from now, then weekly
    
    const endDate = new Date(startDate);
    endDate.setDate(endDate.getDate() + (i % 2 === 0 ? 7 : 3)); // 7 days for safari, 3 for daily
    
    const isSafari = i % 2 === 0;
    
    trips.push({
      organizerId: diveCenterId,
      organizerType: 'dive_center',
      tripType: isSafari ? 'safari' : 'daily',
      country: ['Egypt', 'Maldives', 'Indonesia', 'Philippines', 'Thailand'][i % 5],
      region: ['Red Sea', 'Ari Atoll', 'Bali', 'Palawan', 'Similan Islands'][i % 5],
      startDate: startDate.toISOString().split('T')[0],
      endDate: endDate.toISOString().split('T')[0],
      minimumCertificationLevel: i < 3 ? 'Open Water Diver' : 'Advanced Open Water',
      minimumDives: i < 3 ? 10 : 30,
      description: `${isSafari ? 'Liveaboard safari' : 'Daily diving trip'} to amazing dive sites. Experience world-class diving with professional guides.`,
      totalSpots: 12 + (i * 2),
      bookedSpots: Math.floor(Math.random() * 5),
      availableCourses: courseIds.slice(0, 3),
      nitroxAvailable: i % 3 === 0,
      equipmentRentalAvailable: true,
      priceDetails: isSafari ? {
        yachtPrices: [
          { id: '1', cabinType: 'Standard', cabinCount: 4, divingPrice: 1500 + (i * 100), nonDivingPrice: 1000 + (i * 50) },
          { id: '2', cabinType: 'Deluxe', cabinCount: 2, divingPrice: 2000 + (i * 100), nonDivingPrice: 1500 + (i * 50) },
        ],
        currency: 'USD',
      } : {
        roomPrices: [
          { id: '1', roomType: 'Single', roomCount: 2, divingPrice: 800 + (i * 50), nonDivingPrice: 500 + (i * 30) },
          { id: '2', roomType: 'Double', roomCount: 4, divingPrice: 600 + (i * 50), nonDivingPrice: 400 + (i * 30) },
        ],
        currency: 'USD',
      },
      programDays: generateProgramDays(startDate, endDate, isSafari),
      additionalExpenses: [
        { id: '1', expenseType: 'flight', description: 'International flight', cost: 500 + (i * 50), currency: 'USD' },
        { id: '2', expenseType: 'transfer', description: 'Airport transfer', cost: 50, currency: 'USD' },
      ],
    });
  }
  
  return trips;
}

function generateProgramDays(startDate, endDate, isSafari) {
  const days = [];
  const current = new Date(startDate);
  let dayNum = 1;
  
  while (current <= endDate) {
    const activities = [];
    
    if (isSafari) {
      activities.push(
        { id: `${dayNum}-1`, time: '07:00', activity: 'Breakfast', notes: null },
        { id: `${dayNum}-2`, time: '08:00', activity: 'First dive', notes: 'Reef dive' },
        { id: `${dayNum}-3`, time: '11:00', activity: 'Second dive', notes: 'Wall dive' },
        { id: `${dayNum}-4`, time: '13:00', activity: 'Lunch', notes: null },
        { id: `${dayNum}-5`, time: '15:00', activity: 'Third dive', notes: 'Optional' },
        { id: `${dayNum}-6`, time: '19:00', activity: 'Dinner', notes: null },
      );
    } else {
      activities.push(
        { id: `${dayNum}-1`, time: '08:00', activity: 'Meet at dive center', notes: null },
        { id: `${dayNum}-2`, time: '09:00', activity: 'Boat departure', notes: null },
        { id: `${dayNum}-3`, time: '10:00', activity: 'First dive', notes: 'Reef dive' },
        { id: `${dayNum}-4`, time: '12:00', activity: 'Surface interval', notes: 'Lunch on board' },
        { id: `${dayNum}-5`, time: '13:00', activity: 'Second dive', notes: 'Wall dive' },
        { id: `${dayNum}-6`, time: '15:00', activity: 'Return to shore', notes: null },
      );
    }
    
    days.push({
      id: `day-${dayNum}`,
      date: current.toISOString().split('T')[0],
      activities,
      description: `Day ${dayNum} of diving`,
    });
    
    current.setDate(current.getDate() + 1);
    dayNum++;
  }
  
  return days;
}

async function main() {
  const client = new Client(dbConfig);
  
  try {
    console.log('🔌 Connecting to database...');
    await client.connect();
    console.log('✅ Connected to database');
    
    // Check if user already exists
    const existingUser = await client.query(
      'SELECT id FROM users WHERE email = $1',
      [TEST_USER.email]
    );
    
    let userId;
    let diveCenterId;
    
    if (existingUser.rows.length > 0) {
      console.log('⚠️  User already exists, using existing user...');
      userId = existingUser.rows[0].id;
      
      // Get dive center for this user
      const diveCenterResult = await client.query(
        'SELECT id FROM dive_centers WHERE email = $1',
        [TEST_USER.email]
      );
      
      if (diveCenterResult.rows.length > 0) {
        diveCenterId = diveCenterResult.rows[0].id;
        console.log('⚠️  Dive center already exists, using existing dive center...');
      }
    } else {
      // Create user
      console.log('👤 Creating user...');
      const hashedPassword = await bcrypt.hash(TEST_USER.password, 10);
      
      const userResult = await client.query(
        `INSERT INTO users (email, password, "firstName", "lastName", role)
         VALUES ($1, $2, $3, $4, $5)
         RETURNING id`,
        [TEST_USER.email, hashedPassword, TEST_USER.firstName, TEST_USER.lastName, TEST_USER.role]
      );
      
      userId = userResult.rows[0].id;
      console.log(`✅ User created with ID: ${userId}`);
    }
    
    // Create or update dive center
    if (!diveCenterId) {
      console.log('🏢 Creating dive center...');
      
      const latitude = 27.9158; // Sharm El Sheikh coordinates
      const longitude = 34.3300;
      
      const diveCenterResult = await client.query(
        `INSERT INTO dive_centers (
          name, description, email, phone, country, city, address,
          location,
          services, certification_agency, languages, nitrox_available,
          photo_urls, average_rating, review_count, is_active
        )
        VALUES (
          $1, $2, $3, $4, $5, $6, $7,
          ST_SetSRID(ST_MakePoint($8, $9), 4326)::geography,
          $10, $11, $12, $13,
          $14, $15, $16, $17
        )
        RETURNING id`,
        [
          'Test Dive Center',
          'Professional dive center offering courses and trips worldwide.',
          TEST_USER.email,
          '+1234567890',
          'Egypt',
          'Sharm El Sheikh',
          '123 Diving Street',
          longitude, // Note: ST_MakePoint takes (longitude, latitude)
          latitude,
          ['courses', 'equipment rental', 'guided dives', 'night dives', 'wreck dives'],
          'PADI',
          ['English', 'Russian'],
          true,
          SAMPLE_PHOTOS,
          4.8,
          150,
          true,
        ]
      );
      
      diveCenterId = diveCenterResult.rows[0].id;
      console.log(`✅ Dive center created with ID: ${diveCenterId}`);
    }
    
    // Create courses
    console.log('📚 Creating courses...');
    const courseIds = [];
    
    for (const course of COURSES) {
      const courseResult = await client.query(
        `INSERT INTO courses (
          name, level, description, training_systems, modules, duration,
          prerequisites, dive_center_id, photo_urls
        )
        VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
        RETURNING id`,
        [
          course.name,
          course.level,
          course.description,
          course.trainingSystems,
          JSON.stringify(course.modules),
          course.duration,
          course.prerequisites,
          diveCenterId,
          SAMPLE_PHOTOS,
        ]
      );
      
      courseIds.push(courseResult.rows[0].id);
      console.log(`  ✅ Created course: ${course.name}`);
    }
    
    console.log(`✅ Created ${courseIds.length} courses`);
    
    // Create trips
    console.log('✈️  Creating trips...');
    const trips = generateTrips(diveCenterId, courseIds);
    
    for (const trip of trips) {
      await client.query(
        `INSERT INTO trips (
          organizer_id, organizer_type, trip_type, country, region,
          start_date, end_date, minimum_certification_level, minimum_dives,
          description, photo_urls, total_spots, booked_spots,
          available_courses, nitrox_available, equipment_rental_available,
          program_days, additional_expenses, price_details
        )
        VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16, $17, $18, $19)`,
        [
          trip.organizerId,
          trip.organizerType,
          trip.tripType,
          trip.country,
          trip.region,
          trip.startDate,
          trip.endDate,
          trip.minimumCertificationLevel,
          trip.minimumDives,
          trip.description,
          SAMPLE_PHOTOS,
          trip.totalSpots,
          trip.bookedSpots,
          trip.availableCourses,
          trip.nitroxAvailable,
          trip.equipmentRentalAvailable,
          JSON.stringify(trip.programDays),
          JSON.stringify(trip.additionalExpenses),
          JSON.stringify(trip.priceDetails)
        ]
      );
      
      console.log(`  ✅ Created trip: ${trip.country} - ${trip.tripType}`);
    }
    
    console.log(`✅ Created ${trips.length} trips`);
    
    console.log('\n🎉 Test data created successfully!');
    console.log(`\n📧 Email: ${TEST_USER.email}`);
    console.log(`🔑 Password: ${TEST_USER.password}`);
    console.log(`🏢 Dive Center ID: ${diveCenterId}`);
    console.log(`📚 Courses created: ${courseIds.length}`);
    console.log(`✈️  Trips created: ${trips.length}`);
    
  } catch (error) {
    console.error('❌ Error:', error);
    process.exit(1);
  } finally {
    await client.end();
  }
}

main();
