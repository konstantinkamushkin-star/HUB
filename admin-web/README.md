This is a [Next.js](https://nextjs.org) project bootstrapped with [`create-next-app`](https://nextjs.org/docs/app/api-reference/cli/create-next-app).

## Getting Started

First, run the development server:

```bash
npm run dev
# or
yarn dev
# or
pnpm dev
# or
bun dev
```

Open **http://localhost:3001** with your browser (`npm run dev` uses port 3001).

### Admin panel login

Вход в админ-панель **не** вынесен в публичное меню. URL по умолчанию: `/staff/divehub-console` (см. `src/lib/adminLoginPath.ts`). При деплое можно переопределить `NEXT_PUBLIC_ADMIN_LOGIN_PATH`. Старый `/login` перенаправляет на главную.

### Деплой (команды)

#### Важно: где лежит `admin-web`

В репозитории **`admin-web`** и **`backend`** — **соседние папки** в корне проекта. На VPS это обычно так:

```text
/opt/HUB/
  backend/      ← здесь только Nest + docker-compose, НЕТ папки admin-web внутри
  admin-web/    ← сайт Next.js — заходить сюда отдельно
```

Если вы в **`/opt/HUB/backend`**, команда `cd admin-web` **не сработает** — нужно:

```bash
cd /opt/HUB/admin-web
# или из backend:
cd ../admin-web
```

Из **`backend`** второй раз **`cd backend`** тоже ошибочен — вы уже внутри `backend`.

#### Сайт (Next.js), прод

```bash
cd /opt/HUB/admin-web   # путь подставьте под свой сервер
npm ci
npm run build
NODE_ENV=production npm run start
```

`npm run start` запускает **`scripts/next-start.mjs`**: по умолчанию порт **3001**, иначе значение **`PORT`**. Пример: `PORT=3003 NODE_ENV=production npm run start`. За **nginx/Caddy** проксируйте на выбранный порт.

Если видите **`EADDRINUSE :::3001`** — порт уже занят (старый `next start`, pm2 и т.д.). Проверка: `sudo ss -tlnp | grep ':3001'` или `sudo lsof -iTCP:3001 -sTCP:LISTEN` → остановите процесс или используйте другой `PORT`.

#### Персистенция (systemd) и nginx на VPS

Шаблоны в репозитории: **`deploy/divehub-admin-web.service.example`**, **`deploy/nginx-dive-hub-ru-snippet.conf.example`**. На сервере после `git pull`, `npm ci`, `npm run build`:

```bash
sudo cp /opt/HUB/admin-web/deploy/divehub-admin-web.service.example /etc/systemd/system/divehub-admin-web.service
# Отредактируйте unit: WorkingDirectory, User, PORT, путь к npm при nvm
sudo systemctl daemon-reload
sudo systemctl enable --now divehub-admin-web
curl -fsSI http://127.0.0.1:3003/staff/divehub-console | head -n 5
```

В конфиге сайта для **dive-hub.ru** вставьте фрагмент из **`nginx-dive-hub-ru-snippet.conf.example`** (порты **3003** для Next и **3002** для API замените на свои), затем:

```bash
sudo nginx -t && sudo systemctl reload nginx
curl -fsSI https://dive-hub.ru/staff/divehub-console | head -n 5
```

Подробнее про `/privacy` на API и порты: **`backend/docs/SERVER_HANDOFF_TEMPLATE.md`**.

#### Бэкенд в проде — Docker, не `nest start` на хосте

Скрипт **`./deploy-dive-hub-ru.sh`** уже поднимает API в **контейнере** (у вас наружу, например, **3002→3000**). Если после этого запустить в той же машине **`npm run start`** из папки `backend`, Nest попытается занять **:3000** на хосте → **`EADDRINUSE`**. Для продакшена API достаточно Docker; ручной `nest start` — только для отладки и тогда остановите контейнер `api` или смените порт в `.env`.

#### Локально в dev (порт **3001** по `package.json`)

```bash
cd admin-web
npm install
npm run dev
```

Открыть: **http://localhost:3001** (не 3000).

**Vercel** (если проект привязан к Vercel):

```bash
cd admin-web
npx vercel --prod
```

#### Бэкенд (Docker + миграции на VPS)

```bash
cd /opt/HUB/backend
chmod +x deploy-dive-hub-ru.sh   # один раз
./deploy-dive-hub-ru.sh
```

Перед первым деплоем: `cp .env.production.example .env` и заполните секреты (см. `docker-compose.yml`).

You can start editing the page by modifying `app/page.tsx`. The page auto-updates as you edit the file.

This project uses [`next/font`](https://nextjs.org/docs/app/building-your-application/optimizing/fonts) to automatically optimize and load [Geist](https://vercel.com/font), a new font family for Vercel.

## Learn More

To learn more about Next.js, take a look at the following resources:

- [Next.js Documentation](https://nextjs.org/docs) - learn about Next.js features and API.
- [Learn Next.js](https://nextjs.org/learn) - an interactive Next.js tutorial.

You can check out [the Next.js GitHub repository](https://github.com/vercel/next.js) - your feedback and contributions are welcome!

## Deploy on Vercel

The easiest way to deploy your Next.js app is to use the [Vercel Platform](https://vercel.com/new?utm_medium=default-template&filter=next.js&utm_source=create-next-app&utm_campaign=create-next-app-readme) from the creators of Next.js.

Check out our [Next.js deployment documentation](https://nextjs.org/docs/app/building-your-application/deploying) for more details.
