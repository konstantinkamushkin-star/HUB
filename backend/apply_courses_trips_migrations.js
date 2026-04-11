const { Client } = require('pg');
const fs = require('fs');
const path = require('path');

// Load .env file if it exists
try {
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

async function applyMigrations() {
  const client = new Client({
    host: process.env.DB_HOST || 'localhost',
    port: parseInt(process.env.DB_PORT || '5432'),
    user: process.env.DB_USERNAME || 'admin',
    password: process.env.DB_PASSWORD || '',
    database: process.env.DB_DATABASE || 'divehub',
  });

  try {
    await client.connect();
    console.log('✅ Connected to database');

    // Apply courses migration
    const coursesMigrationFile = path.join(__dirname, 'migrations', '004_create_courses.sql');
    if (fs.existsSync(coursesMigrationFile)) {
      const coursesSql = fs.readFileSync(coursesMigrationFile, 'utf8');
      console.log('📝 Applying migration: 004_create_courses.sql');
      await client.query(coursesSql);
      console.log('✅ Courses migration applied successfully!');
    } else {
      console.log('⚠️  Courses migration file not found, skipping...');
    }

    // Apply trips migration
    const tripsMigrationFile = path.join(__dirname, 'migrations', '005_create_trips.sql');
    if (fs.existsSync(tripsMigrationFile)) {
      const tripsSql = fs.readFileSync(tripsMigrationFile, 'utf8');
      console.log('📝 Applying migration: 005_create_trips.sql');
      await client.query(tripsSql);
      console.log('✅ Trips migration applied successfully!');
    } else {
      console.log('⚠️  Trips migration file not found, skipping...');
    }

    // Verify tables exist
    const coursesResult = await client.query(`
      SELECT EXISTS (
        SELECT FROM information_schema.tables 
        WHERE table_schema = 'public' 
        AND table_name = 'courses'
      );
    `);
    
    const tripsResult = await client.query(`
      SELECT EXISTS (
        SELECT FROM information_schema.tables 
        WHERE table_schema = 'public' 
        AND table_name = 'trips'
      );
    `);
    
    if (coursesResult.rows[0].exists) {
      console.log('✅ Table courses exists');
    } else {
      console.log('❌ Table courses does not exist');
    }
    
    if (tripsResult.rows[0].exists) {
      console.log('✅ Table trips exists');
    } else {
      console.log('❌ Table trips does not exist');
    }
    
  } catch (error) {
    console.error('❌ Error applying migrations:', error.message);
    if (error.message.includes('already exists')) {
      console.log('ℹ️  Tables may already exist, continuing...');
    } else {
      process.exit(1);
    }
  } finally {
    await client.end();
  }
}

applyMigrations();
