# Настройка Backend для DiveHub

## Пошаговая инструкция

### 1. Установка зависимостей

```bash
cd backend
npm install
```

### 2. Настройка PostgreSQL с PostGIS

#### macOS
```bash
brew install postgresql@15 postgis
brew services start postgresql@15
```

#### Ubuntu/Debian
```bash
sudo apt-get update
sudo apt-get install postgresql-15 postgresql-15-postgis-3
sudo systemctl start postgresql
```

#### Создание базы данных
```bash
# Войти в PostgreSQL
psql postgres

# Создать базу данных
CREATE DATABASE divehub;

# Выйти
\q

# Войти в новую базу
psql divehub

# Включить PostGIS
CREATE EXTENSION IF NOT EXISTS postgis;

# Проверить версию
SELECT PostGIS_version();

# Выйти
\q
```

### 3. Применение миграции

```bash
# Применить SQL миграцию
psql -d divehub -f ../backend_examples/migrations/001_create_dive_sites.sql
```

### 4. Настройка Redis

#### macOS
```bash
brew install redis
brew services start redis
```

#### Ubuntu/Debian
```bash
sudo apt-get install redis-server
sudo systemctl start redis
```

#### Проверка
```bash
redis-cli ping
# Должно вернуть: PONG
```

### 5. Настройка .env

```bash
cp .env.example .env
```

Отредактируйте `.env`:
```env
DB_HOST=localhost
DB_PORT=5432
DB_USERNAME=postgres
DB_PASSWORD=ваш_пароль
DB_DATABASE=divehub

REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

PORT=3000
NODE_ENV=development
```

### 6. Запуск сервера

```bash
# Development режим (с hot reload)
npm run start:dev

# Или production режим
npm run build
npm run start:prod
```

Сервер должен запуститься на `http://localhost:3000`

### 7. Проверка работы

```bash
# Health check (если добавите endpoint)
curl http://localhost:3000/api/v1/dive-sites/popular

# Geo search (нужны данные в БД)
curl "http://localhost:3000/api/v1/dive-sites/search?lat=20.0&lng=-80.0&radius=50000"
```

## Добавление тестовых данных

После настройки базы данных, добавьте тестовые данные:

```sql
INSERT INTO dive_sites (
    name, description, latitude, longitude,
    country, region, site_types, difficulty_level,
    depth_min, depth_max, average_rating, review_count,
    is_active
) VALUES
(
    'Blue Hole',
    'Famous circular sinkhole',
    17.3158, -87.5346,
    'Belize', 'Lighthouse Reef',
    ARRAY['wall', 'cave'], 3,
    0, 124, 4.8, 1234,
    true
),
(
    'Shark Ray Alley',
    'Shallow reef with sharks and rays',
    17.9167, -87.9500,
    'Belize', 'Ambergris Caye',
    ARRAY['reef'], 1,
    3, 12, 4.7, 892,
    true
);
```

## Устранение проблем

### Ошибка: "relation dive_sites does not exist"
- Примените миграцию: `psql -d divehub -f ../backend_examples/migrations/001_create_dive_sites.sql`

### Ошибка: "PostGIS extension not found"
- Установите PostGIS: `CREATE EXTENSION postgis;`

### Ошибка: "Redis connection failed"
- Проверьте, что Redis запущен: `redis-cli ping`
- Проверьте настройки в `.env`

### Ошибка: "Cannot find module"
- Установите зависимости: `npm install`

### Медленные запросы
- Проверьте индексы: `\d+ dive_sites` в psql
- Убедитесь, что GIST индекс создан: `idx_dive_sites_location_gist`

## Production Deployment

Для production:

1. Установите `NODE_ENV=production` в `.env`
2. Используйте переменные окружения для секретов
3. Настройте SSL/TLS
4. Настройте мониторинг (Prometheus, Grafana)
5. Настройте логирование
6. Используйте process manager (PM2)

```bash
npm install -g pm2
npm run build
pm2 start dist/main.js --name divehub-api
```
