#!/usr/bin/env node
/**
 * Перенос данных из старого бэкенда (Prisma /Users/admin/Desktop/divehub-backend)
 * в текущую PostgreSQL-схему DiveHub (Nest).
 *
 * Переносится: User → users, DiveSite → dive_sites, DiveLog → dive_logs, Review → reviews
 * В старом Prisma-проекте нет таблицы ленты — feed_posts этим скриптом не заполняются.
 *
 * Требуется:
 *   - Целевая БД: миграции DiveHub уже применены (npm run migrate:all / apply-all-migrations.cjs).
 *   - SOURCE_DATABASE_URL — строка подключения к старой БД (Prisma).
 *   - Целевая: backend/.env (DB_HOST, DB_PORT, DB_USERNAME, DB_PASSWORD, DB_DATABASE).
 *
 * Запуск (с Mac, когда старый Postgres доступен по сети или localhost):
 *   export SOURCE_DATABASE_URL="postgresql://USER:PASS@127.0.0.1:5432/СТАРАЯ_БД"
 *   cd /path/to/DivePROD/backend
 *   node scripts/migrate-from-prisma-divehub.cjs
 *
 * Сухой прогон:
 *   node scripts/migrate-from-prisma-divehub.cjs --dry-run
 *
 * Только шаг:
 *   node scripts/migrate-from-prisma-divehub.cjs --only=users
 *   --only=sites|logs|reviews|all   (по умолчанию all)
 */
const fs = require('fs');
const path = require('path');
const { Client } = require('pg');

function loadEnvFile(root) {
  try {
    const envPath = path.join(root, '.env');
    const envFile = fs.readFileSync(envPath, 'utf8');
    envFile.split('\n').forEach((line) => {
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith('#')) return;
      const eq = trimmed.indexOf('=');
      if (eq <= 0) return;
      const key = trimmed.slice(0, eq).trim();
      let value = trimmed.slice(eq + 1).trim();
      if (
        (value.startsWith('"') && value.endsWith('"')) ||
        (value.startsWith("'") && value.endsWith("'"))
      ) {
        value = value.slice(1, -1);
      }
      if (key && value && process.env[key] === undefined) {
        process.env[key] = value;
      }
    });
  } catch {
    /* no .env */
  }
}

function parseArgs(argv) {
  const out = { dryRun: false, only: 'all' };
  for (const a of argv) {
    if (a === '--dry-run') out.dryRun = true;
    if (a.startsWith('--only=')) out.only = a.slice('--only='.length);
  }
  return out;
}

async function columnExists(client, tableName, columnName) {
  const r = await client.query(
    `SELECT 1 FROM information_schema.columns
     WHERE table_schema = 'public' AND table_name = $1 AND column_name = $2`,
    [tableName, columnName],
  );
  return r.rows.length > 0;
}

async function migrateUsers(source, target, dryRun) {
  const r = await source.query(`SELECT count(*)::int AS c FROM "User"`);
  console.log(`\n[users] В источнике: ${r.rows[0].c} записей`);
  if (dryRun) return;

  const sql = `
    INSERT INTO users (
      id, email, password, "firstName", "lastName", "avatarUrl", phone, "dateOfBirth",
      role, "subscriptionTier", "subscriptionExpiresAt", "totalDives", "totalDiveTime", "maxDepth",
      language, "countryCode", timezone, "emailVerified", "phoneVerified", "lastLogin",
      "passwordResetCode", "passwordResetExpires", "shareLogbook", "createdAt", "updatedAt"
    )
    SELECT
      u.id::uuid,
      u.email,
      u.password,
      u."firstName",
      u."lastName",
      u."avatarUrl",
      u.phone,
      u."dateOfBirth",
      u.role,
      u."subscriptionTier",
      u."subscriptionExpiresAt",
      u."totalDives",
      u."totalDiveTime",
      u."maxDepth",
      u.language,
      u."countryCode",
      u.timezone,
      u."emailVerified",
      u."phoneVerified",
      u."lastLogin",
      u."passwordResetCode",
      u."passwordResetExpires",
      COALESCE(u."shareLogbook", false),
      u."createdAt",
      u."updatedAt"
    FROM "User" u
    ON CONFLICT (id) DO NOTHING
  `;
  const ins = await target.query(sql);
  console.log(`[users] Вставлено (новых строк по отчёту драйвера): ${ins.rowCount ?? '—'}`);
}

async function migrateDiveSites(source, target, dryRun) {
  const hasNameTr = await columnExists(source, 'DiveSite', 'nameTranslations');
  const hasDescTr = await columnExists(source, 'DiveSite', 'descriptionTranslations');

  const r = await source.query(`SELECT count(*)::int AS c FROM "DiveSite"`);
  console.log(`\n[sites] В источнике: ${r.rows[0].c} записей`);
  if (dryRun) return;

  const nameCol = hasNameTr ? 's."nameTranslations"' : 'NULL';
  const descCol = hasDescTr ? 's."descriptionTranslations"' : 'NULL';

  const sql = `
    INSERT INTO dive_sites (
      id, name, description, localized_name, localized_description,
      location, country, region,
      site_types, difficulty_level, depth_min, depth_max,
      average_rating, review_count, marine_life,
      is_active, status, created_at, updated_at
    )
    SELECT
      s.id::uuid,
      LEFT(s.name, 255),
      s.description,
      ${nameCol},
      ${descCol},
      ST_SetSRID(ST_MakePoint(s.longitude, s.latitude), 4326)::geography,
      LEFT(s.country, 100),
      LEFT(s.region, 100),
      COALESCE(s."diveTypes", ARRAY[]::text[]),
      COALESCE(s."difficultyLevel", 1),
      s."depthMin",
      s."depthMax",
      LEAST(5::numeric, GREATEST(0::numeric, COALESCE(s."averageRating", 0)::numeric))::decimal(3,2),
      GREATEST(0, COALESCE(s."reviewCount", 0)),
      COALESCE(s."marineLife", ARRAY[]::text[]),
      true,
      'published',
      s."createdAt",
      s."updatedAt"
    FROM "DiveSite" s
    ON CONFLICT (id) DO NOTHING
  `;
  const ins = await target.query(sql);
  console.log(`[sites] Обработано вставкой: ${ins.rowCount ?? '—'}`);
}

async function migrateDiveLogs(source, target, dryRun) {
  const r = await source.query(`SELECT count(*)::int AS c FROM "DiveLog"`);
  console.log(`\n[logs] В источнике: ${r.rows[0].c} записей`);
  if (dryRun) return;

  const sql = `
    INSERT INTO dive_logs (
      id, "userId", "diveSiteId", date, "startTime", "endTime",
      duration, "maxDepth", "averageDepth", "waterTemperature", visibility,
      current, "diveType", notes,
      "photoUrls", "videoUrls", "fishSpecies",
      "isPublished", "moderationStatus", "createdAt", "updatedAt"
    )
    SELECT
      dl.id::uuid,
      dl."userId"::uuid,
      CASE
        WHEN dl."diveSiteId" IS NULL OR dl."diveSiteId" = '' THEN NULL
        WHEN EXISTS (SELECT 1 FROM dive_sites d WHERE d.id = dl."diveSiteId"::uuid)
          THEN dl."diveSiteId"::uuid
        ELSE NULL
      END,
      (dl.date AT TIME ZONE 'UTC')::date,
      dl."startTime",
      dl."endTime",
      dl.duration,
      dl."maxDepth",
      dl."averageDepth",
      dl."waterTemperature",
      dl.visibility::double precision,
      dl.current,
      LEFT(dl."diveType", 64),
      dl.notes,
      COALESCE(to_jsonb(dl."photoUrls"), '[]'::jsonb),
      COALESCE(to_jsonb(dl."videoUrls"), '[]'::jsonb),
      '[]'::jsonb,
      NULL,
      'published',
      dl."createdAt",
      dl."updatedAt"
    FROM "DiveLog" dl
    ON CONFLICT (id) DO NOTHING
  `;
  const ins = await target.query(sql);
  console.log(`[logs] Обработано вставкой: ${ins.rowCount ?? '—'}`);
}

function mapReviewTargetType(t) {
  if (!t) return 'dive_site';
  const u = String(t).toUpperCase();
  if (u === 'DIVE_SITE') return 'dive_site';
  if (u === 'DIVE_CENTER') return 'dive_center';
  if (u === 'INSTRUCTOR') return 'instructor';
  return String(t).toLowerCase();
}

async function migrateReviews(source, target, dryRun) {
  const r = await source.query(`SELECT count(*)::int AS c FROM "Review"`);
  console.log(`\n[reviews] В источнике: ${r.rows[0].c} записей`);
  if (dryRun) return;

  const rows = await source.query(
    `SELECT id, "authorId", "targetType", "targetId", rating, comment, "createdAt", "updatedAt"
     FROM "Review"`,
  );

  let n = 0;
  for (const row of rows.rows) {
    const rt = mapReviewTargetType(row.targetType);
    const text = row.comment != null && String(row.comment).trim() !== ''
      ? String(row.comment)
      : '—';
    try {
      await target.query(
        `INSERT INTO reviews (
          id, "userId", "reviewableType", "reviewableId", rating, text, language, "createdAt", "updatedAt"
        ) VALUES ($1::uuid, $2::uuid, $3, $4::uuid, $5, $6, 'en', $7, $8)
        ON CONFLICT (id) DO NOTHING`,
        [
          row.id,
          row.authorId,
          rt,
          row.targetId,
          row.rating,
          text,
          row.createdAt,
          row.updatedAt,
        ],
      );
      n++;
    } catch (e) {
      if (e.code === '23505' || e.code === '23503') {
        continue;
      }
      console.warn(`[reviews] skip ${row.id}: ${e.message}`);
    }
  }
  console.log(`[reviews] Попыток вставки: ${n}`);
}

async function main() {
  const root = path.join(__dirname, '..');
  loadEnvFile(root);

  const args = parseArgs(process.argv.slice(2));
  const sourceUrl = process.env.SOURCE_DATABASE_URL;
  if (!sourceUrl || !String(sourceUrl).startsWith('postgresql')) {
    console.error(
      'Задайте SOURCE_DATABASE_URL — скопируйте DATABASE_URL из старого divehub-backend/.env',
    );
    console.error(
      'Пример: postgresql://postgres:мойпароль@127.0.0.1:5432/divehub',
    );
    console.error('Не оставляйте буквальные слова USER или PASS — подставьте реальные логин и пароль.');
    process.exit(1);
  }

  try {
    const u = new URL(sourceUrl.replace(/^postgresql:/, 'http:'));
    if (u.username === 'USER' || u.password === 'PASS') {
      console.error(
        'В SOURCE_DATABASE_URL всё ещё шаблон USER/PASS — возьмите точную строку из старого .env (DATABASE_URL).',
      );
      process.exit(1);
    }
  } catch {
    /* URL parse optional */
  }

  const targetConfig = {
    host: process.env.DB_HOST || 'localhost',
    port: parseInt(process.env.DB_PORT || '5432', 10),
    user: process.env.DB_USERNAME || process.env.DB_USER || 'postgres',
    password: process.env.DB_PASSWORD || '',
    database: process.env.DB_DATABASE || 'divehub',
  };

  const source = new Client({ connectionString: sourceUrl });
  const target = new Client(targetConfig);

  await source.connect();
  await target.connect();

  console.log('Источник (Prisma): подключено');
  console.log(
    `Цель: ${targetConfig.user}@${targetConfig.host}:${targetConfig.port}/${targetConfig.database}`,
  );
  if (args.dryRun) {
    console.log('Режим --dry-run: записи в цель не пишутся\n');
  }

  const only = args.only;
  const run = (name, fn) =>
    only === 'all' || only === name ? fn() : Promise.resolve();

  try {
    await run('users', () => migrateUsers(source, target, args.dryRun));
    await run('sites', () => migrateDiveSites(source, target, args.dryRun));
    await run('logs', () => migrateDiveLogs(source, target, args.dryRun));
    await run('reviews', () => migrateReviews(source, target, args.dryRun));

    if (!['all', 'users', 'sites', 'logs', 'reviews'].includes(only)) {
      console.error('Неизвестный --only=… (users|sites|logs|reviews|all)');
      process.exit(1);
    }

    console.log('\nГотово.');
    console.log(
      '\nЛента (feed_posts) в старом Prisma-схеме не найдена — перенос постов отдельно, если данные были в другом месте.',
    );
  } finally {
    await source.end();
    await target.end();
  }
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
