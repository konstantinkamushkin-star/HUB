#!/usr/bin/env node

/**
 * Script to show all users with their login, password, and type
 * 
 * Usage:
 *   node show_all_users.js
 */

const { Client } = require('pg');

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

// Known passwords from creation scripts
const knownPasswords = {
  'shop1@deepblue.com': 'Shop1@2024',
  'shop2@coralreef.com': 'Shop2@2024',
  'shop3@tropical.com': 'Shop3@2024',
  'shop4@oceanpro.com': 'Shop4@2024',
  'shop5@divetech.com': 'Shop5@2024',
  'shop6@bluewater.com': 'Shop6@2024',
  'shop7@reefmasters.com': 'Shop7@2024',
  'shop8@aquaworld.com': 'Shop8@2024',
  'shop9@pacific.com': 'Shop9@2024',
  'shop10@mediterranean.com': 'Shop10@2024',
  'ww@ww.ww': '12345678',
};

async function showAllUsers() {
  const client = new Client(dbConfig);
  
  try {
    await client.connect();
    console.log('✅ Connected to database\n');
    
    // Query all users
    const result = await client.query(
      `SELECT 
        email as login,
        password,
        role as type,
        "firstName",
        "lastName",
        id,
        "createdAt"
       FROM users 
       ORDER BY "createdAt" DESC`
    );
    
    if (result.rows.length === 0) {
      console.log('❌ No users found in database');
      return;
    }
    
    console.log(`📊 Found ${result.rows.length} user(s):\n`);
    console.log('='.repeat(100));
    
    result.rows.forEach((user, index) => {
      const originalPassword = knownPasswords[user.login.toLowerCase()];
      
      console.log(`\n👤 User #${index + 1}:`);
      console.log(`   Login (Email): ${user.login}`);
      if (originalPassword) {
        console.log(`   Original Password: ${originalPassword}`);
        console.log(`   Hashed Password: ${user.password}`);
      } else {
        console.log(`   Password (Hashed): ${user.password}`);
        console.log(`   ⚠️  Original password not found in scripts`);
      }
      console.log(`   Type (Role): ${user.type}`);
      console.log(`   Name: ${user.firstName} ${user.lastName}`);
      console.log(`   ID: ${user.id}`);
      console.log(`   Created: ${user.createdAt}`);
      console.log('-'.repeat(100));
    });
    
    console.log(`\n✅ Total: ${result.rows.length} user(s)\n`);
    
  } catch (error) {
    console.error('❌ Error:', error.message);
    process.exit(1);
  } finally {
    await client.end();
  }
}

showAllUsers();
