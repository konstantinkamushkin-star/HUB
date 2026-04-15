/**
 * Applies all SQL migrations in dependency-safe order (PostGIS + DiveHub schema).
 * Reads backend/.env for DB_HOST, DB_PORT, DB_USERNAME, DB_PASSWORD, DB_DATABASE.
 *
 * Usage (from backend/):
 *   node scripts/apply-all-migrations.cjs
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

/** Порядок важен: два файла `006_*`, два `008_*`, два `013_*`. */
const MIGRATION_FILES = [
  '001_create_dive_sites.sql',
  '002_create_dive_centers.sql',
  '003_create_users.sql',
  '008_fix_users_updated_at_trigger.sql',
  '004_create_courses.sql',
  '005_create_trips.sql',
  '006_trip_hotel_yacht_labels.sql',
  '006_create_shops.sql',
  '007_create_friendships.sql',
  '008_drop_friendships_updated_at_trigger.sql',
  '009_create_feed.sql',
  '010_create_chat.sql',
  '011_chat_attachments_push_devices.sql',
  '012_drop_chat_conversations_updated_at_trigger.sql',
  '013_create_dive_logs.sql',
  '013_users_add_bio.sql',
  '014_create_reviews.sql',
  '015_admin_foundation.sql',
  '016_reports_and_moderation.sql',
  '017_settings_flags_notifications.sql',
  '018_compliance_search_data_jobs.sql',
  '019_merge_and_verification_workflow.sql',
  '020_admin_tz_extensions.sql',
  '021_admin_totp_analytics_webhooks.sql',
  '022_shops_verification_status.sql',
  '023_user_must_change_password_dive_center_owner.sql',
  '024_user_inbox_notifications.sql',
  '025_users_oauth_subs.sql',
  '026_users_diver_profile.sql',
];

function pick(envFile, key, fallback) {
  if (process.env[key] != null && String(process.env[key]).length > 0) {
    return process.env[key];
  }
  if (envFile[key] != null && String(envFile[key]).length > 0) {
    return envFile[key];
  }
  return fallback;
}

async function main() {
  const root = path.join(__dirname, '..');
  const env = loadEnv(path.join(root, '.env'));
  const client = new Client({
    host: String(pick(env, 'DB_HOST', 'localhost') ?? 'localhost'),
    port: parseInt(String(pick(env, 'DB_PORT', '5432') ?? '5432'), 10),
    user: String(pick(env, 'DB_USERNAME', 'postgres') ?? 'postgres'),
    // pg требует строку для SCRAM; пустой пароль — допустимая строка "".
    password: String(pick(env, 'DB_PASSWORD', '') ?? ''),
    database: String(pick(env, 'DB_DATABASE', 'divehub') ?? 'divehub'),
  });

  await client.connect();
  try {
    for (const name of MIGRATION_FILES) {
      const sqlPath = path.join(root, 'migrations', name);
      if (!fs.existsSync(sqlPath)) {
        throw new Error(`Missing migration file: ${sqlPath}`);
      }
      const sql = fs.readFileSync(sqlPath, 'utf8');
      await client.query(sql);
      console.log(`OK: ${name}`);
    }
    console.log('\nAll migrations applied successfully.');
  } finally {
    await client.end();
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
