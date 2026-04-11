# Быстрый старт Backend

## 🚀 За 5 минут

### 1. Установка зависимостей

```bash
cd backend
npm install
```

### 2. Настройка базы данных

```bash
# Создать БД
createdb divehub

# Применить миграцию
psql -d divehub -f ../backend_examples/migrations/001_create_dive_sites.sql
```

### 3. Настройка .env

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

PORT=3000
```

### 4. Запуск Redis

```bash
# macOS
brew services start redis

# Linux
sudo systemctl start redis
```

### 5. Запуск сервера

```bash
npm run start:dev
```

Готово! Сервер на `http://localhost:3000`

## ✅ Проверка

```bash
# Популярные сайты
curl http://localhost:3000/api/v1/dive-sites/popular

# Geo search (нужны данные в БД)
curl "http://localhost:3000/api/v1/dive-sites/search?lat=20.0&lng=-80.0&radius=50000"
```

## 📝 Добавление тестовых данных

```sql
psql -d divehub

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
);
```

## 🐛 Проблемы?

См. [SETUP.md](./SETUP.md) для детальной инструкции.
