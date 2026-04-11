#!/usr/bin/env node

/**
 * Create or update SUPER_ADMIN user.
 *
 * Usage:
 *   node scripts/create-super-admin.cjs --email=admin@example.com --password=StrongPass123! --first-name=Super --last-name=Admin
 */

const fs = require('fs');
const path = require('path');
const bcrypt = require('bcryptjs');
const { Client } = require('pg');

function parseArgs(argv) {
  const args = {};
  for (const item of argv) {
    if (!item.startsWith('--')) continue;
    const [key, ...rest] = item.slice(2).split('=');
    args[key] = rest.join('=');
  }
  return args;
}

function loadEnv(filePath) {
  if (!fs.existsSync(filePath)) return;

  const lines = fs.readFileSync(filePath, 'utf8').split('\n');
  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const idx = trimmed.indexOf('=');
    if (idx === -1) continue;

    const key = trimmed.slice(0, idx).trim();
    let value = trimmed.slice(idx + 1).trim();
    if (
      (value.startsWith('"') && value.endsWith('"')) ||
      (value.startsWith("'") && value.endsWith("'"))
    ) {
      value = value.slice(1, -1);
    }

    if (!(key in process.env)) {
      process.env[key] = value;
    }
  }
}

function required(name, value) {
  if (!value || !String(value).trim()) {
    throw new Error(`Missing required parameter: ${name}`);
  }
  return String(value).trim();
}

async function main() {
  const backendRoot = path.join(__dirname, '..');
  loadEnv(path.join(backendRoot, '.env'));

  const args = parseArgs(process.argv.slice(2));
  const email = required('email', args.email).toLowerCase();
  const password = required('password', args.password);
  const firstName = (args['first-name'] || 'Super').trim();
  const lastName = (args['last-name'] || 'Admin').trim();

  if (password.length < 8) {
    throw new Error('Password must be at least 8 characters long');
  }

  const client = new Client({
    host: process.env.DB_HOST || 'localhost',
    port: parseInt(process.env.DB_PORT || '5432', 10),
    user: process.env.DB_USERNAME || 'postgres',
    password: process.env.DB_PASSWORD || '',
    database: process.env.DB_DATABASE || 'divehub',
  });

  await client.connect();
  try {
    const passwordHash = await bcrypt.hash(password, 10);

    const existing = await client.query(
      'SELECT id FROM users WHERE LOWER(email) = LOWER($1) LIMIT 1',
      [email],
    );

    if (existing.rows.length > 0) {
      const userId = existing.rows[0].id;
      await client.query(
        `UPDATE users
         SET email = $1,
             password = $2,
             "firstName" = $3,
             "lastName" = $4,
             role = 'SUPER_ADMIN',
             "updatedAt" = NOW()
         WHERE id = $5`,
        [email, passwordHash, firstName, lastName, userId],
      );
      console.log(`Updated existing user ${email} as SUPER_ADMIN`);
    } else {
      await client.query(
        `INSERT INTO users (
          email,
          password,
          "firstName",
          "lastName",
          role,
          "emailVerified"
        ) VALUES ($1, $2, $3, $4, 'SUPER_ADMIN', true)`,
        [email, passwordHash, firstName, lastName],
      );
      console.log(`Created new SUPER_ADMIN user ${email}`);
    }
  } finally {
    await client.end();
  }
}

main().catch((error) => {
  console.error(error.message || error);
  process.exit(1);
});
