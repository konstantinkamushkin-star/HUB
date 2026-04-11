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

async function applyMigration() {
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

    const migrationFile = path.join(__dirname, 'migrations', '002_create_dive_centers.sql');
    const sql = fs.readFileSync(migrationFile, 'utf8');
    
    console.log('📝 Applying migration: 002_create_dive_centers.sql');
    await client.query(sql);
    
    console.log('✅ Migration applied successfully!');
    
    // Verify table exists
    const result = await client.query(`
      SELECT EXISTS (
        SELECT FROM information_schema.tables 
        WHERE table_schema = 'public' 
        AND table_name = 'dive_centers'
      );
    `);
    
    if (result.rows[0].exists) {
      console.log('✅ Table dive_centers exists');
    } else {
      console.log('❌ Table dive_centers does not exist');
    }
    
  } catch (error) {
    console.error('❌ Error applying migration:', error.message);
    process.exit(1);
  } finally {
    await client.end();
  }
}

applyMigration();
