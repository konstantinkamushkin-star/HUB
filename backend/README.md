# DiveHub Backend API

NestJS backend с оптимизированным геопоиском для DiveHub.

## 🚀 Быстрый старт

### Требования

- Node.js 18+
- PostgreSQL 15+ с PostGIS 3.3+
- Redis 7+

### Установка

```bash
# Установка зависимостей
npm install

# Копирование .env файла
cp .env.example .env

# Редактирование .env с вашими настройками
nano .env
```

### Настройка базы данных

```bash
# Создание базы данных
createdb divehub

# Применение миграции (PostGIS + таблицы)
psql -d divehub -f ../backend_examples/migrations/001_create_dive_sites.sql
```

### Запуск

```bash
# Development режим
npm run start:dev

# Production режим
npm run build
npm run start:prod
```

Сервер запустится на `http://localhost:3000`

## 📡 API Endpoints

### Geo Search API

#### 1. Поиск по радиусу
```
GET /api/v1/dive-sites/search
```

**Query параметры:**
- `lat` (required) - Широта
- `lng` (required) - Долгота
- `radius` (optional) - Радиус в метрах (default: 50000)
- `difficulty` (optional) - Уровень сложности (1-4)
- `site_types` (optional) - Типы сайтов (массив)
- `min_depth` (optional) - Минимальная глубина
- `max_depth` (optional) - Максимальная глубина
- `min_rating` (optional) - Минимальный рейтинг (0-5)
- `access_type` (optional) - Тип доступа (массив: shore, boat)
- `country` (optional) - Страна
- `sort` (optional) - Сортировка: distance, rating, popularity, newest (default: distance)
- `limit` (optional) - Количество результатов (default: 20, max: 100)
- `cursor` (optional) - Cursor для пагинации

**Пример:**
```bash
curl "http://localhost:3000/api/v1/dive-sites/search?lat=20.0&lng=-80.0&radius=50000&difficulty=2&site_types=reef&sort=distance&limit=20"
```

#### 2. Поиск по bounding box (для карты)
```
GET /api/v1/dive-sites/map
```

**Query параметры:**
- `north` (required) - Северная граница
- `south` (required) - Южная граница
- `east` (required) - Восточная граница
- `west` (required) - Западная граница
- `difficulty` (optional)
- `site_types` (optional)
- `min_rating` (optional)
- `limit` (optional, max: 500)

**Пример:**
```bash
curl "http://localhost:3000/api/v1/dive-sites/map?north=20.1&south=19.9&east=-79.9&west=-80.1"
```

#### 3. Популярные дайвсайты (fallback)
```
GET /api/v1/dive-sites/popular
```

**Query параметры:**
- `country` (optional) - Фильтр по стране
- `limit` (optional, default: 20, max: 100)

**Пример:**
```bash
curl "http://localhost:3000/api/v1/dive-sites/popular?country=Belize&limit=20"
```

## 🗄️ База данных

### Структура таблицы

Таблица `dive_sites` создается миграцией из `backend_examples/migrations/001_create_dive_sites.sql`.

### Важные индексы

- `idx_dive_sites_location_gist` - GIST индекс для геопоиска (критически важно!)
- `idx_dive_sites_active_location` - Композитный индекс для активных сайтов
- `idx_dive_sites_site_types` - GIN индекс для массивов типов
- И другие индексы для фильтрации

## 🔄 Кэширование

Используется Redis для кэширования результатов:

- **TTL зависит от радиуса:**
  - < 10km: 5 минут
  - < 50km: 15 минут
  - >= 50km: 1 час

- **Ключи кэша:**
  - Geo search: `divesites:geo:{lat}:{lng}:r{radius}:f{hash}:...`
  - Bounds search: `divesites:bounds:{north}_{south}_{east}_{west}:f{hash}`

## 📊 Производительность

### Ожидаемые метрики

- Response time: < 200ms (с кэшем < 10ms)
- Database query: < 50ms (с GIST индексом)
- Cache hit rate: > 70%

### Мониторинг

Логи показывают:
- Cache HIT/MISS
- Query execution time
- Количество результатов

## 🔧 Конфигурация

Настройки в `.env`:

```env
# Database
DB_HOST=localhost
DB_PORT=5432
DB_USERNAME=postgres
DB_PASSWORD=postgres
DB_DATABASE=divehub

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# Server
PORT=3000
NODE_ENV=development
```

## 🐛 Отладка

### Проверка PostGIS

```sql
SELECT PostGIS_version();
```

### Проверка индексов

```sql
EXPLAIN ANALYZE
SELECT * FROM dive_sites
WHERE is_active = true
  AND ST_DWithin(
      location,
      ST_SetSRID(ST_MakePoint(-80.0, 20.0), 4326)::geography,
      50000
  );
```

Должно показать использование `idx_dive_sites_location_gist`.

### Проверка Redis

```bash
redis-cli
> KEYS divesites:*
> GET divesites:geo:...
```

## 📝 Следующие шаги

1. ✅ Настроить базу данных с PostGIS
2. ✅ Применить миграцию
3. ✅ Настроить Redis
4. ✅ Запустить сервер
5. ⏳ Добавить тестовые данные
6. ⏳ Настроить мониторинг
7. ⏳ Настроить production deployment

## 🔗 Связанные документы

- [GEO_API_ARCHITECTURE.md](../GEO_API_ARCHITECTURE.md) - Полная архитектура
- [GEO_API_INTEGRATION.md](../GEO_API_INTEGRATION.md) - Интеграция в iOS
- [backend_examples/](../backend_examples/) - Примеры кода
