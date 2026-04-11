/**
 * Показать количество строк в основных таблицах (проверка «пусто или нет»).
 * Читает backend/.env — DB_HOST, DB_PORT, DB_USERNAME, DB_PASSWORD, DB_DATABASE.
 *
 *   node scripts/db-show-counts.cjs
 */
const fs = require('fs');
const path = require('path');
const { Client } = require('pg');

function loadEnv(filePath) {
  const env = {};
  if (!fs.existsSync(filePath)) return env;
  for (const line of fs.readFileSync(filePath, 'utf8').split('\n')) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const eq = trimmed.indexOf('=');
    if (eq === -1) continue;
    const key = trimmed.slice(0, eq).trim();
    let val = trimmed.slice(eq + 1).trim();
    if (
      (val.startsWith('"') && val.endsWith('"')) ||
      (val.startsWith("'") && val.endsWith("'"))
    ) {
      val = val.slice(1, -1);
    }
    env[key] = val;
  }
  return env;
}

/** Как в apply-all-migrations.cjs: явные переменные окружения важнее .env (там часто DB_USERNAME=admin для внешней БД). */
function pick(fileEnv, key, fallback) {
  if (process.env[key] != null && String(process.env[key]).length > 0) {
    return process.env[key];
  }
  if (fileEnv[key] != null && String(fileEnv[key]).length > 0) {
    return fileEnv[key];
  }
  return fallback;
}

const TABLES = [
  'dive_sites',
  'dive_centers',
  'shops',
  'users',
  'courses',
  'trips',
  'dive_logs',
  'feed_posts',
  'reviews',
];

async function main() {
  const root = path.join(__dirname, '..');
  const fileEnv = loadEnv(path.join(root, '.env'));
  const client = new Client({
    host: String(pick(fileEnv, 'DB_HOST', 'localhost') ?? 'localhost'),
    port: parseInt(String(pick(fileEnv, 'DB_PORT', '5432') ?? '5432'), 10),
    user: String(pick(fileEnv, 'DB_USERNAME', 'postgres') ?? 'postgres'),
    password: String(pick(fileEnv, 'DB_PASSWORD', '') ?? ''),
    database: String(pick(fileEnv, 'DB_DATABASE', 'divehub') ?? 'divehub'),
  });

  await client.connect();
  try {
    console.log(
      `DB: ${client.user}@${client.host}:${client.port}/${client.database}\n`,
    );
    for (const t of TABLES) {
      try {
        const r = await client.query(
          `SELECT count(*)::int AS c FROM ${t.replace(/[^a-z0-9_]/gi, '')}`,
        );
        console.log(`${t.padEnd(16)} ${r.rows[0].c}`);
      } catch (e) {
        console.log(`${t.padEnd(16)} (ошибка: ${e.message})`);
      }
    }
  } finally {
    await client.end();
  }
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
