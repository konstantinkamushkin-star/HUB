#!/usr/bin/env node

/**
 * Create or update SUPER_ADMIN user.
 *
 * Usage (пароль приложения с `!` — в одинарных кавычках, иначе bash съест `!`):
 *   node scripts/create-super-admin.cjs --email=admin@example.com --password='StrongPass123!' --first-name=Super --last-name=Admin
 */

const fs = require('fs');
const path = require('path');
const bcrypt = require('bcryptjs');
const { Client } = require('pg');
const { parse: parseConnectionString } = require('pg-connection-string');

function parseArgs(argv) {
  const args = {};
  for (const item of argv) {
    if (!item.startsWith('--')) continue;
    const [key, ...rest] = item.slice(2).split('=');
    args[key] = rest.join('=');
  }
  return args;
}

function readEnvFileUtf8(filePath) {
  let text = fs.readFileSync(filePath, 'utf8');
  if (text.charCodeAt(0) === 0xfeff) {
    text = text.slice(1);
  }
  return text;
}

function loadEnv(filePath) {
  if (!fs.existsSync(filePath)) return;

  const lines = readEnvFileUtf8(filePath).split('\n');
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

/** Перезаписать DB_* из `backend/.env`, чтобы пустой/битый `DB_PASSWORD` из окружения shell не ломал `pg`. */
function loadEnvForceDbKeys(filePath) {
  if (!fs.existsSync(filePath)) return;
  const keys = new Set([
    'DB_HOST',
    'DB_PORT',
    'DB_USERNAME',
    'DB_PASSWORD',
    'DB_DATABASE',
    'POSTGRES_PASSWORD',
    'DATABASE_URL',
  ]);
  const lines = readEnvFileUtf8(filePath).split('\n');
  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const idx = trimmed.indexOf('=');
    if (idx === -1) continue;
    const key = trimmed.slice(0, idx).trim();
    if (!keys.has(key)) continue;
    let value = trimmed.slice(idx + 1).trim();
    if (
      (value.startsWith('"') && value.endsWith('"')) ||
      (value.startsWith("'") && value.endsWith("'"))
    ) {
      value = value.slice(1, -1);
    }
    process.env[key] = value;
  }
}

function required(name, value) {
  if (!value || !String(value).trim()) {
    throw new Error(`Missing required parameter: ${name}`);
  }
  return String(value).trim();
}

/** `pg` + SCRAM требуют строковый пароль; из .env / Docker иногда приходит нестроковое значение. */
function strEnv(name, fallback = '') {
  const v = process.env[name];
  if (v === undefined || v === null) return fallback;
  return String(v);
}

/** Пароль из DATABASE_URL в .env, если отдельный DB_PASSWORD не задан. */
function passwordFromDatabaseUrl() {
  const raw = strEnv('DATABASE_URL', '').trim();
  if (!raw) return '';
  try {
    const c = parseConnectionString(raw);
    if (c.password != null && String(c.password).length > 0) {
      return String(c.password);
    }
  } catch (_) {
    /* ignore */
  }
  return '';
}

/**
 * Подключение только через URL: обходит баг `pg`, когда `password: ''` в объекте
 * игнорируется (`if (config.password)` → null).
 */
function buildPostgresConnectionString(host, port, user, password, database) {
  const enc = encodeURIComponent;
  let h = String(host || 'localhost');
  if (h.includes(':') && !h.startsWith('/') && !h.startsWith('[')) {
    h = `[${h}]`;
  }
  const p = parseInt(String(port || 5432), 10) || 5432;
  return `postgres://${enc(String(user || 'postgres'))}:${enc(String(password))}@${h}:${p}/${enc(String(database || 'divehub'))}`;
}

async function main() {
  const backendRoot = path.join(__dirname, '..');
  const envPath = path.join(backendRoot, '.env');
  loadEnv(envPath);
  loadEnvForceDbKeys(envPath);

  const args = parseArgs(process.argv.slice(2));
  const email = required('email', args.email).toLowerCase();
  const password = required('password', args.password);
  const firstName = (args['first-name'] || 'Super').trim();
  const lastName = (args['last-name'] || 'Admin').trim();

  if (password.length < 8) {
    throw new Error('Password must be at least 8 characters long');
  }

  /**
   * Пустой `password: ''` в объекте конфига `pg` игнорируется → default `null` → SCRAM:
   * "client password must be a string". Подключаемся через `connectionString` (URL).
   */
  const rawDatabaseUrl = strEnv('DATABASE_URL', '').trim();
  let client;
  if (rawDatabaseUrl) {
    try {
      const parsedUrl = parseConnectionString(rawDatabaseUrl);
      if (parsedUrl.password != null && String(parsedUrl.password).trim() !== '') {
        client = new Client({ connectionString: rawDatabaseUrl });
      }
    } catch (_) {
      /* ниже — сборка из DB_* */
    }
  }

  if (!client) {
    let dbPassword = strEnv('DB_PASSWORD', '').trim();
    if (!dbPassword) {
      dbPassword = strEnv('POSTGRES_PASSWORD', '').trim();
    }
    if (!dbPassword) {
      dbPassword = passwordFromDatabaseUrl().trim();
    }
    if (!dbPassword) {
      throw new Error(
        'Postgres password is missing: set DATABASE_URL (with password), or non-empty DB_PASSWORD / POSTGRES_PASSWORD in backend/.env. Empty DB_PASSWORD causes a misleading SASL error from node-pg.',
      );
    }

    const connStr = buildPostgresConnectionString(
      strEnv('DB_HOST', 'localhost'),
      strEnv('DB_PORT', '5432'),
      strEnv('DB_USERNAME', 'postgres'),
      dbPassword,
      strEnv('DB_DATABASE', 'divehub'),
    );
    client = new Client({ connectionString: connStr });
  }

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
