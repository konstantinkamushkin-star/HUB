/**
 * Applies settings/flags/notifications SQL migration (017) via pg.
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

async function main() {
  const root = path.join(__dirname, '..');
  const env = loadEnv(path.join(root, '.env'));
  const client = new Client({
    host: env.DB_HOST || 'localhost',
    port: parseInt(env.DB_PORT || '5432', 10),
    user: env.DB_USERNAME || 'postgres',
    password: env.DB_PASSWORD || '',
    database: env.DB_DATABASE || 'divehub',
  });

  await client.connect();
  try {
    const sqlPath = path.join(
      root,
      'migrations',
      '017_settings_flags_notifications.sql',
    );
    const sql = fs.readFileSync(sqlPath, 'utf8');
    await client.query(sql);
    console.log('Applied 017_settings_flags_notifications.sql successfully.');
  } finally {
    await client.end();
  }
}

main().catch((err) => {
  console.error(err.message || err);
  process.exit(1);
});
