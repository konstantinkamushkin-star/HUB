/**
 * Promote all active DIVER_BASIC users to DIVER_PRO with active trial until X months ahead.
 *
 * Usage:
 *   node scripts/promote-diver-basic-to-pro-trial.cjs --dry-run
 *   node scripts/promote-diver-basic-to-pro-trial.cjs --apply
 *   node scripts/promote-diver-basic-to-pro-trial.cjs --apply --months=4
 *
 * Reads backend/.env (DB_HOST, DB_PORT, DB_USERNAME, DB_PASSWORD, DB_DATABASE),
 * but explicit environment variables take precedence.
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

function pick(fileEnv, key, fallback) {
  if (process.env[key] != null && String(process.env[key]).length > 0) {
    return process.env[key];
  }
  if (fileEnv[key] != null && String(fileEnv[key]).length > 0) {
    return fileEnv[key];
  }
  return fallback;
}

function parseArgs(argv) {
  const opts = {
    apply: false,
    dryRun: false,
    months: 4,
  };
  for (const arg of argv) {
    if (arg === '--apply') opts.apply = true;
    else if (arg === '--dry-run') opts.dryRun = true;
    else if (arg.startsWith('--months=')) {
      const n = parseInt(arg.split('=')[1], 10);
      if (Number.isFinite(n) && n > 0 && n <= 24) opts.months = n;
    }
  }
  if (!opts.apply) opts.dryRun = true;
  return opts;
}

async function main() {
  const opts = parseArgs(process.argv.slice(2));
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
      `DB: ${client.user}@${client.host}:${client.port}/${client.database}`,
    );
    console.log(`Mode: ${opts.dryRun ? 'DRY-RUN' : 'APPLY'}`);
    console.log(`Trial months: ${opts.months}`);

    const preview = await client.query(
      `
        SELECT id, email, role, "subscriptionTier", "subscriptionExpiresAt"
        FROM users
        WHERE role = 'DIVER_BASIC'
          AND COALESCE("accountStatus", 'ACTIVE') = 'ACTIVE'
          AND "deletedAt" IS NULL
        ORDER BY "createdAt" ASC
        LIMIT 20
      `,
    );

    const countRes = await client.query(
      `
        SELECT count(*)::int AS c
        FROM users
        WHERE role = 'DIVER_BASIC'
          AND COALESCE("accountStatus", 'ACTIVE') = 'ACTIVE'
          AND "deletedAt" IS NULL
      `,
    );
    const targetCount = countRes.rows[0]?.c ?? 0;
    console.log(`Target users: ${targetCount}`);
    if (preview.rows.length > 0) {
      console.log('Preview (first 20):');
      for (const r of preview.rows) {
        console.log(`- ${r.id} | ${r.email} | tier=${r.subscriptionTier ?? 'null'}`);
      }
    }

    if (opts.dryRun) {
      console.log('Dry-run only, no changes made.');
      return;
    }

    await client.query('BEGIN');
    const updateRes = await client.query(
      `
        UPDATE users
        SET role = 'DIVER_PRO',
            "subscriptionTier" = 'active',
            "subscriptionExpiresAt" = NOW() + make_interval(months => $1::int),
            "updatedAt" = NOW()
        WHERE role = 'DIVER_BASIC'
          AND COALESCE("accountStatus", 'ACTIVE') = 'ACTIVE'
          AND "deletedAt" IS NULL
      `,
      [opts.months],
    );
    await client.query('COMMIT');

    console.log(`Updated users: ${updateRes.rowCount ?? 0}`);
  } catch (e) {
    try {
      await client.query('ROLLBACK');
    } catch (_) {}
    throw e;
  } finally {
    await client.end();
  }
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
