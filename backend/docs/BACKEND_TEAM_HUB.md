# Указания для бэкенд-разработчиков (репозиторий HUB)

Целевой репозиторий: **https://github.com/konstantinkamushkin-star/HUB**

## 1. С чем работаем

- **Стек:** NestJS, PostgreSQL + PostGIS, Redis.
- **Код API:** каталог `backend/` в монорепозитории (рядом могут быть `DiveHub/`, `admin-web/`, `backend_examples/` — не ломать соседние проекты без согласования).
- **Документация по продакшену:** `backend/docs/PRODUCTION_PREP.md` (если есть в ветке).

## 2. Первый клон и ветки

```bash
git clone https://github.com/konstantinkamushkin-star/HUB.git
cd HUB
git checkout -b feature/краткое-описание-задачи
```

- Основная ветка по договорённости с тимлидом (`main` / `develop`).
- Коммиты — осмысленные сообщения на английском или русском (единый стиль в команде).
- Секреты (JWT, пароли БД, ключи) **никогда** в git — только `.env` локально, значения из менеджера секретов / CI.

## 3. Локальный запуск backend

```bash
cd backend
cp .env.example .env   # при наличии; заполнить переменные
docker compose up -d --build
```

Проверка API (порт по умолчанию **3000**):

```bash
curl -s http://127.0.0.1:3000/api/health
```

(точный health-путь уточнить в `AppModule` / контроллерах.)

## 4. Переменные окружения (минимум)

| Переменная      | Назначение                          |
|-----------------|-------------------------------------|
| `DB_HOST`       | `localhost` / `postgres` в Docker   |
| `DB_PORT`       | `5432`                              |
| `DB_DATABASE`   | имя БД (часто `divehub`)            |
| `DB_USERNAME`   | пользователь Postgres               |
| `DB_PASSWORD`   | пароль                              |
| `REDIS_HOST`    | `localhost` / `redis` в Docker      |
| `JWT_SECRET`    | длинная случайная строка в проде   |
| `CORS_ORIGINS`  | URL фронта и админки через запятую |

С хоста VPS к Postgres в Docker часто: `DB_HOST=127.0.0.1`, если порт **5432** проброшен на хост.

## 5. Миграции и схема `dive_sites`

- Базовая таблица для гео/Explore: `backend/migrations/001_create_dive_sites.sql`.
- Применение на пустой БД — по инструкции в репозитории (скрипт `npm run migrate:001-dive-sites` или `scripts/apply-migration-001-dive-sites.cjs` — смотреть `backend/package.json`).

После изменений схемы: новые SQL-миграции или TypeORM-миграции — по принятому в проекте процессу; не править прод БД вручную без записи в миграции.

## 6. Импорт дайв-сайтов (GeoJSON)

- Скрипт: `backend/scripts/import_opendivemap_geojson.js`.
- Формат: GeoJSON **FeatureCollection**, точки как в `opendivemap_all_sites.geojson`.
- Запуск (из каталога `backend`, с корректным `DB_*`):

```bash
node scripts/import_opendivemap_geojson.js /path/to/file.geojson
npm run import:opendivemap -- /path/to/file.geojson
```

Проверка без записи в БД:

```bash
node scripts/import_opendivemap_geojson.js /path/to/file.geojson --dry-run
```

**Важно:** в production Docker-образе API обычно **нет** исходников `scripts/` — импорт выполняют с **хоста** или из checkout репозитория, с доступом к Postgres.

**Пустые дайв-сайты на проде после нового Postgres в compose:** таблица `dive_sites` создана миграциями, но **пустая**, пока не выполнен импорт. Файл в корне репозитория: `opendivemap_all_sites.geojson`. С **хоста VPS** (не из контейнера `api`) подставьте **`POSTGRES_PUBLISH_PORT`** из `backend/.env` (часто `5433`) и пароль **`POSTGRES_PASSWORD`**:

```bash
cd /opt/divehub-src/DivePROD/backend
# сухой прогон
DB_HOST=127.0.0.1 DB_PORT=5433 DB_USERNAME=postgres DB_PASSWORD='ВАШ_POSTGRES_PASSWORD' DB_DATABASE=divehub \
  node scripts/import_opendivemap_geojson.js ../opendivemap_all_sites.geojson --dry-run
# запись в БД
DB_HOST=127.0.0.1 DB_PORT=5433 DB_USERNAME=postgres DB_PASSWORD='ВАШ_POSTGRES_PASSWORD' DB_DATABASE=divehub \
  node scripts/import_opendivemap_geojson.js ../opendivemap_all_sites.geojson
```

Проверка количества: `DB_HOST=127.0.0.1 DB_PORT=5433 … node scripts/db-show-counts.cjs`. Старые данные при необходимости переносят с **другого** инстанса Postgres (например прежний compose на `:5432`), а не из GeoJSON.

## 7. Сборка и тесты

```bash
cd backend
npm ci
npm run build
npm run lint    # если настроен
npm test        # если настроен
```

Перед merge: сборка без ошибок, линтер, ручная проверка затронутых эндпоинтов.

## 8. API и клиенты

- Мобильные приложения и админка могут опираться на **legacy** маршруты (например `GET /api/dive-sites`) — перед изменением контракта: согласование с iOS/Android/фронтом.
- Новые эндпоинты: DTO, валидация, при необходимости кэш Redis — по существующим паттернам в `backend/src/`.

## 9. Деплой (кратко)

- На сервере: `docker compose` (или orchestrator команды), актуальный `.env`, проброс портов, HTTPS (например Caddy/nginx) — детали у DevOps / в `PRODUCTION_PREP.md`.
- После деплоя: smoke-тест `curl` к health и критичным API.

## 10. Что сделать при приёме задачи

1. Обновить `main` (или базовую ветку), создать feature-ветку.
2. Реализовать изменения, не расширяя scope без согласования.
3. Проверить локально + документировать неочевидное в PR.
4. PR с описанием: что сделано, как проверить, риски для БД/миграций.

Вопросы по инфраструктуре сервера (IP, SSH, секреты) — не в коде репозитория; отдельный канал с владельцем инфраструктуры.
