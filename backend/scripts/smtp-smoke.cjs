/**
 * Проверка SMTP: соединение (verify) и опционально тестовое письмо.
 * Читает backend/.env (простой парсер, как в apply-all-migrations.cjs).
 *
 * Использование (из каталога backend/):
 *   node scripts/smtp-smoke.cjs              # только verify()
 *   node scripts/smtp-smoke.cjs you@mail.ru  # verify + sendMail на адрес
 *
 * Если .env не в backend/.env (например только в Docker-каталоге):
 *   SMTP_SMOKE_ENV=/opt/divehub-backend/.env node scripts/smtp-smoke.cjs you@mail.ru
 */
const fs = require('fs');
const path = require('path');
const nodemailer = require('nodemailer');

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
  const envPath = process.env.SMTP_SMOKE_ENV
    ? path.resolve(process.env.SMTP_SMOKE_ENV)
    : path.join(__dirname, '..', '.env');
  const env = loadEnv(envPath);
  const host = env.SMTP_HOST;
  const port = Number(env.SMTP_PORT || 587);
  const user = env.SMTP_USER;
  const pass = env.SMTP_PASSWORD;
  const from = env.SMTP_FROM || user;
  const testTo = process.argv[2];

  if (!host || !user || !pass) {
    console.error('Нет SMTP_HOST / SMTP_USER / SMTP_PASSWORD в', envPath);
    process.exit(1);
  }

  const transporter = nodemailer.createTransport({
    host,
    port,
    secure: port === 465,
    auth: { user, pass },
  });

  console.log('SMTP verify:', host, 'port', port);
  await transporter.verify();
  console.log('OK: verify() прошёл, соединение с сервером есть.');

  if (testTo) {
    if (!from) {
      console.error('Нужен SMTP_FROM или SMTP_USER как отправитель.');
      process.exit(1);
    }
    const subj = 'DiveHub SMTP smoke test';
    await transporter.sendMail({
      from,
      to: testTo,
      subject: subj,
      text: 'Если вы видите это письмо, SMTP с сервера/локали работает.\n',
    });
    console.log('OK: тестовое письмо отправлено на', testTo);
  } else {
    console.log('(Без аргумента адреса письмо не отправлялось. Укажите: node scripts/smtp-smoke.cjs your@email.com)');
  }
}

main().catch((err) => {
  console.error('Ошибка:', err.message || err);
  process.exit(1);
});
