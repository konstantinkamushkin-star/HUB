# Архитектура высоконагруженного геосервиса для DiveHub

## Оглавление
1. [Выбор технологий](#выбор-технологий)
2. [Архитектурная схема](#архитектурная-схема)
3. [Структура базы данных](#структура-базы-данных)
4. [Индексы и оптимизация](#индексы-и-оптимизация)
5. [Геопоиск и фильтрация](#геопоиск-и-фильтрация)
6. [Стратегия пагинации](#стратегия-пагинации)
7. [Кэширование](#кэширование)
8. [API дизайн](#api-дизайн)
9. [Предзагрузка и clustering](#предзагрузка-и-clustering)
10. [Оптимизация payload](#оптимизация-payload)
11. [Fallback стратегии](#fallback-стратегии)
12. [Примеры кода](#примеры-кода)

---

## Выбор технологий

### Backend: **Go (Golang)** ✅

**Почему Go, а не Node.js:**
- **Производительность**: Go компилируется в нативный код, в 2-5x быстрее Node.js для CPU-intensive операций
- **Конкурентность**: Goroutines идеальны для параллельной обработки геозапросов
- **Память**: Меньше overhead, лучше для высоконагрузки
- **PostGIS интеграция**: Отличные драйверы (pq, pgx)
- **Типизация**: Статическая типизация предотвращает runtime ошибки
- **Latency**: Go показывает стабильно низкую latency (< 10ms) для геозапросов

**Рекомендуемый стек:**
- **Framework**: Gin или Fiber (легковесные, быстрые)
- **ORM**: pgx (нативный драйвер) или GORM (если нужен ORM)
- **PostGIS**: lib/pq + PostGIS расширение
- **Redis**: go-redis/v9
- **Monitoring**: Prometheus + Grafana

### База данных: **PostgreSQL 15+ с PostGIS 3.3+** ✅

**Почему PostGIS достаточно (без ElasticSearch):**
- PostGIS имеет **GIST индексы** для геоданных (R-tree) - очень быстрые
- **ST_DWithin** и **ST_Distance** оптимизированы через индексы
- Для 100k+ записей PostGIS справляется отлично (< 50ms на запрос)
- ElasticSearch нужен только если требуется full-text search по описаниям
- **Рекомендация**: Начните с PostGIS, добавьте ElasticSearch позже если нужен полнотекстовый поиск

### Кэш: **Redis 7+** ✅

**Обязательно нужен для:**
- Кэширование результатов геозапросов
- Кэширование фильтрованных результатов
- Rate limiting
- Session management
- Real-time статистика

---

## Архитектурная схема

```
┌─────────────────┐
│  iOS Client     │
│  (Mapbox/MapKit)│
└────────┬────────┘
         │ HTTPS/REST
         │
┌────────▼─────────────────────────────────────────┐
│           API Gateway (Nginx/Cloudflare)         │
│           - Rate Limiting                        │
│           - SSL Termination                      │
└────────┬─────────────────────────────────────────┘
         │
┌────────▼─────────────────────────────────────────┐
│         Load Balancer (Round Robin)              │
└────────┬─────────────────────────────────────────┘
         │
    ┌────┴────┬──────────┬──────────┐
    │         │          │          │
┌───▼───┐ ┌──▼───┐  ┌───▼───┐  ┌───▼───┐
│ Go API│ │ Go API│  │ Go API│  │ Go API│
│Server │ │Server │  │Server │  │Server │
└───┬───┘ └───┬──┘  └───┬───┘  └───┬───┘
    │         │          │          │
    └────┬────┴──────────┴──────────┘
         │
    ┌────┴────┬──────────────┐
    │         │              │
┌───▼───┐ ┌──▼────┐    ┌────▼────┐
│PostGIS│ │ Redis │    │  S3/CDN │
│  DB   │ │ Cache │    │ (Photos)│
└───────┘ └───────┘    └─────────┘
```

### Поток запроса

```
1. Client → API Gateway
   └─> Rate limit check (Redis)

2. API Gateway → Load Balancer → Go Server
   └─> Parse request (lat, lng, radius, filters)

3. Go Server → Redis (Cache Check)
   └─> Cache key: "divesites:{lat}:{lng}:{radius}:{filters_hash}"
   └─> If HIT: Return cached result (< 5ms)
   └─> If MISS: Continue

4. Go Server → PostgreSQL/PostGIS
   └─> Build optimized SQL query
   └─> Use GIST index for geo filtering
   └─> Apply filters (WHERE clauses)
   └─> Limit + Cursor pagination
   └─> Execute query (< 50ms)

5. Go Server → Transform results
   └─> Calculate distances
   └─> Apply DTO (minimal payload)
   └─> Sort if needed

6. Go Server → Redis (Cache Write)
   └─> Cache result (TTL: 5-15 min)
   └─> Cache invalidation on updates

7. Go Server → Client
   └─> JSON response (< 200ms total)
```

---

## Структура базы данных

### Таблица `dive_sites`

```sql
CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE dive_sites (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- Основная информация
    name VARCHAR(255) NOT NULL,
    description TEXT,
    localized_name JSONB, -- {"en": "Name", "ru": "Название"}
    localized_description JSONB,
    
    -- Геолокация (ОБЯЗАТЕЛЬНО PostGIS тип)
    location GEOGRAPHY(POINT, 4326) NOT NULL, -- Используем GEOGRAPHY для точных расстояний
    latitude DOUBLE PRECISION GENERATED ALWAYS AS (ST_Y(location::geometry)) STORED,
    longitude DOUBLE PRECISION GENERATED ALWAYS AS (ST_X(location::geometry)) STORED,
    
    -- Адрес
    country VARCHAR(100),
    region VARCHAR(100),
    address TEXT,
    
    -- Характеристики
    site_types TEXT[] NOT NULL DEFAULT '{}', -- ['reef', 'wreck'] - массив типов
    difficulty_level INTEGER NOT NULL DEFAULT 1, -- 1=beginner, 2=intermediate, 3=advanced, 4=expert
    depth_min DOUBLE PRECISION, -- метры
    depth_max DOUBLE PRECISION, -- метры
    water_temp_min DOUBLE PRECISION, -- Celsius
    water_temp_max DOUBLE PRECISION, -- Celsius
    seasonality JSONB, -- {"jan": true, "feb": true, ...} или null если круглый год
    access_type TEXT[] DEFAULT '{}', -- ['shore', 'boat']
    price_from DECIMAL(10, 2), -- минимальная цена
    
    -- Рейтинг и популярность
    average_rating DECIMAL(3, 2) DEFAULT 0.0,
    review_count INTEGER DEFAULT 0,
    
    -- Медиа
    photo_urls TEXT[] DEFAULT '{}',
    video_urls TEXT[] DEFAULT '{}',
    marine_life TEXT[] DEFAULT '{}',
    
    -- Метаданные
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    -- Дополнительно
    ai_summary TEXT,
    affiliated_centers UUID[] DEFAULT '{}'
);

-- Генерация location из lat/lng при вставке
CREATE OR REPLACE FUNCTION set_dive_site_location()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.latitude IS NOT NULL AND NEW.longitude IS NOT NULL THEN
        NEW.location := ST_SetSRID(ST_MakePoint(NEW.longitude, NEW.latitude), 4326)::geography;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER dive_site_location_trigger
    BEFORE INSERT OR UPDATE ON dive_sites
    FOR EACH ROW
    EXECUTE FUNCTION set_dive_site_location();
```

### Таблица для индексов расстояний (опционально, для ускорения)

```sql
-- Материализованное представление для предвычисленных расстояний
-- Обновляется раз в час или при изменении данных
CREATE MATERIALIZED VIEW dive_sites_with_distance AS
SELECT 
    id,
    name,
    location,
    latitude,
    longitude,
    average_rating,
    review_count,
    is_active
FROM dive_sites
WHERE is_active = true;

CREATE INDEX ON dive_sites_with_distance USING GIST (location);
```

---

## Индексы и оптимизация

### Критически важные индексы

```sql
-- 1. ГЕОИНДЕКС (самый важный!) - GIST для PostGIS
CREATE INDEX idx_dive_sites_location_gist 
ON dive_sites USING GIST (location);

-- 2. Композитный индекс для активных сайтов + гео
CREATE INDEX idx_dive_sites_active_location 
ON dive_sites USING GIST (location) 
WHERE is_active = true;

-- 3. Индекс для фильтрации по типу
CREATE INDEX idx_dive_sites_site_types 
ON dive_sites USING GIN (site_types);

-- 4. Индекс для сложности
CREATE INDEX idx_dive_sites_difficulty 
ON dive_sites (difficulty_level) 
WHERE is_active = true;

-- 5. Индекс для рейтинга (для сортировки)
CREATE INDEX idx_dive_sites_rating 
ON dive_sites (average_rating DESC, review_count DESC) 
WHERE is_active = true;

-- 6. Индекс для глубины (для фильтрации)
CREATE INDEX idx_dive_sites_depth 
ON dive_sites (depth_min, depth_max) 
WHERE is_active = true;

-- 7. Индекс для страны/региона (для fallback)
CREATE INDEX idx_dive_sites_country_region 
ON dive_sites (country, region) 
WHERE is_active = true;

-- 8. Индекс для времени создания (для сортировки newest)
CREATE INDEX idx_dive_sites_created_at 
ON dive_sites (created_at DESC) 
WHERE is_active = true;

-- 9. Композитный индекс для частых фильтров
CREATE INDEX idx_dive_sites_composite 
ON dive_sites (difficulty_level, country, is_active) 
INCLUDE (latitude, longitude, average_rating, review_count);
```

### Почему GIST индекс?

- **R-tree структура**: Оптимизирована для пространственных запросов
- **ST_DWithin использует индекс**: Автоматически использует GIST для радиус-поиска
- **Производительность**: Для 100k записей поиск в радиусе 50km занимает < 20ms

### Анализ производительности индексов

```sql
-- Проверка использования индексов
EXPLAIN ANALYZE
SELECT id, name, 
       ST_Distance(location, ST_MakePoint(-80.0, 20.0)::geography) as distance
FROM dive_sites
WHERE is_active = true
  AND ST_DWithin(location, ST_MakePoint(-80.0, 20.0)::geography, 50000)
ORDER BY distance
LIMIT 20;

-- Должно показать: "Index Scan using idx_dive_sites_location_gist"
```

---

## Геопоиск и фильтрация

### Как избежать full-scan

**Правило**: Всегда сначала применяйте геофильтр, потом остальные фильтры.

```sql
-- ❌ ПЛОХО: Сначала фильтры, потом гео (full scan)
SELECT * FROM dive_sites
WHERE difficulty_level = 2
  AND site_types @> ARRAY['reef']
  AND ST_DWithin(location, point, radius); -- Индекс не используется!

-- ✅ ХОРОШО: Сначала гео, потом фильтры (использует GIST индекс)
SELECT * FROM dive_sites
WHERE is_active = true
  AND ST_DWithin(location, point, radius) -- Использует GIST индекс
  AND difficulty_level = 2
  AND site_types @> ARRAY['reef'];
```

### Оптимизированный SQL запрос

```sql
-- Базовый геопоиск с фильтрами
WITH geo_filtered AS (
    -- Шаг 1: Геофильтр (использует GIST индекс)
    SELECT 
        id,
        name,
        location,
        latitude,
        longitude,
        site_types,
        difficulty_level,
        depth_min,
        depth_max,
        water_temp_min,
        water_temp_max,
        average_rating,
        review_count,
        country,
        region,
        ST_Distance(
            location, 
            ST_SetSRID(ST_MakePoint($1, $2), 4326)::geography
        ) as distance_meters
    FROM dive_sites
    WHERE is_active = true
      AND ST_DWithin(
          location,
          ST_SetSRID(ST_MakePoint($1, $2), 4326)::geography,
          $3 -- radius in meters
      )
),
filtered AS (
    -- Шаг 2: Применяем остальные фильтры
    SELECT *
    FROM geo_filtered
    WHERE 
        ($4::INTEGER IS NULL OR difficulty_level = $4)
        AND ($5::TEXT[] IS NULL OR site_types && $5) -- Пересечение массивов
        AND ($6::DOUBLE PRECISION IS NULL OR depth_max >= $6) -- min_depth filter
        AND ($7::DOUBLE PRECISION IS NULL OR depth_min <= $7) -- max_depth filter
        AND ($8::DOUBLE PRECISION IS NULL OR average_rating >= $8)
        AND ($9::TEXT[] IS NULL OR access_type && $9)
        AND ($10::TEXT IS NULL OR country = $10)
)
SELECT 
    id,
    name,
    latitude,
    longitude,
    site_types,
    difficulty_level,
    depth_min,
    depth_max,
    average_rating,
    review_count,
    country,
    region,
    ROUND(distance_meters::numeric, 0) as distance_meters
FROM filtered
ORDER BY 
    CASE 
        WHEN $11 = 'distance' THEN distance_meters
        WHEN $11 = 'rating' THEN -average_rating -- DESC через отрицание
        WHEN $11 = 'popularity' THEN -review_count
        ELSE -EXTRACT(EPOCH FROM created_at) -- newest
    END
LIMIT $12 OFFSET $13; -- Для offset пагинации
```

### Параметры запроса

```go
// Go пример параметров
params := []interface{}{
    longitude,        // $1
    latitude,         // $2
    radiusMeters,     // $3 (например, 50000 = 50km)
    difficultyLevel,  // $4 (nullable)
    siteTypes,        // $5 (nullable []string)
    minDepth,         // $6 (nullable)
    maxDepth,         // $7 (nullable)
    minRating,        // $8 (nullable)
    accessTypes,      // $9 (nullable []string)
    country,          // $10 (nullable)
    sortBy,           // $11: "distance" | "rating" | "popularity" | "newest"
    limit,            // $12
    offset,           // $13
}
```

---

## Стратегия пагинации

### Cursor-based пагинация ✅ (РЕКОМЕНДУЕТСЯ)

**Почему cursor, а не offset:**

| Критерий | Offset | Cursor |
|----------|--------|--------|
| Производительность | O(n) - сканирует все записи до offset | O(log n) - использует индекс |
| Стабильность | Проблемы при добавлении данных | Стабильно при изменениях |
| Масштабируемость | Медленно на больших offset | Быстро всегда |
| Память | Высокое потребление | Низкое |

**Пример cursor пагинации:**

```sql
-- Cursor-based для сортировки по distance
SELECT 
    id,
    name,
    distance_meters,
    -- Используем составной cursor: (distance, id)
    (distance_meters, id) as cursor
FROM (
    SELECT 
        id,
        name,
        ST_Distance(location, point) as distance_meters
    FROM dive_sites
    WHERE is_active = true
      AND ST_DWithin(location, point, radius)
      AND (distance_meters, id) > ($cursor_distance, $cursor_id) -- Cursor условие
    ORDER BY distance_meters, id
    LIMIT 20
) sub;
```

**API формат:**

```json
{
  "data": [...],
  "pagination": {
    "has_more": true,
    "next_cursor": "50.5|uuid-here", // "distance|id"
    "limit": 20
  }
}
```

### Offset пагинация (для совместимости)

Используйте только если:
- Клиент требует offset
- Маленькие offset (< 100)
- Кэшируйте результаты

```sql
-- Offset с оптимизацией через индекс
SELECT * FROM dive_sites
WHERE is_active = true
  AND ST_DWithin(location, point, radius)
ORDER BY distance
LIMIT 20 OFFSET $offset; -- Только для offset < 100!
```

---

## Кэширование

### Стратегия кэширования в Redis

#### 1. Кэш ключи

```go
// Формат ключа
cacheKey := fmt.Sprintf(
    "divesites:geo:%d:%d:r%d:f%s:sort%s:limit%d:cursor%s",
    int(lat*1000),      // Округляем до ~100м
    int(lng*1000),
    radiusMeters,
    filtersHash,        // MD5 хеш фильтров
    sortBy,
    limit,
    cursor,
)
```

#### 2. TTL стратегия

```go
// Разные TTL для разных типов запросов
const (
    GeoCacheTTLShort  = 5 * time.Minute   // Частые запросы (карта)
    GeoCacheTTLMedium = 15 * time.Minute  // Обычные запросы
    GeoCacheTTLLong   = 1 * time.Hour     // Редкие фильтры
)

// Выбор TTL в зависимости от радиуса
func getCacheTTL(radiusMeters int) time.Duration {
    if radiusMeters < 10000 { // < 10km - часто меняется
        return GeoCacheTTLShort
    } else if radiusMeters < 50000 { // < 50km
        return GeoCacheTTLMedium
    }
    return GeoCacheTTLLong // > 50km - редко меняется
}
```

#### 3. Инвалидация кэша

```go
// При обновлении дайвсайта
func InvalidateDiveSiteCache(siteID string) error {
    // Найти все ключи с этим сайтом
    pattern := "divesites:*"
    keys, err := redisClient.Keys(ctx, pattern).Result()
    
    // Удалить ключи в радиусе сайта
    for _, key := range keys {
        // Проверить, содержит ли кэш этот сайт
        // Если да - удалить
        redisClient.Del(ctx, key)
    }
    
    // Или использовать более умную стратегию:
    // Хранить mapping: site_id -> cache_keys
    cacheKeys := redisClient.SMembers(ctx, "site_cache_keys:"+siteID).Result()
    for _, key := range cacheKeys {
        redisClient.Del(ctx, key)
    }
}
```

#### 4. Кэширование в Go

```go
type GeoCache struct {
    redis *redis.Client
}

func (c *GeoCache) GetDiveSites(
    lat, lng float64,
    radius int,
    filters DiveSiteFilters,
    sortBy string,
    limit int,
    cursor string,
) ([]DiveSite, string, error) {
    // 1. Генерация ключа
    cacheKey := c.generateCacheKey(lat, lng, radius, filters, sortBy, limit, cursor)
    
    // 2. Попытка получить из кэша
    cached, err := c.redis.Get(ctx, cacheKey).Result()
    if err == nil {
        var result CachedResult
        json.Unmarshal([]byte(cached), &result)
        return result.Sites, result.NextCursor, nil
    }
    
    // 3. Если нет в кэше - запрос к БД
    sites, nextCursor, err := c.fetchFromDB(lat, lng, radius, filters, sortBy, limit, cursor)
    if err != nil {
        return nil, "", err
    }
    
    // 4. Сохранение в кэш
    ttl := getCacheTTL(radius)
    result := CachedResult{Sites: sites, NextCursor: nextCursor}
    data, _ := json.Marshal(result)
    c.redis.Set(ctx, cacheKey, data, ttl)
    
    return sites, nextCursor, nil
}
```

### Кэширование на клиенте (iOS)

```swift
// LRU кэш на клиенте для часто запрашиваемых регионов
class DiveSiteCache {
    private let cache = NSCache<NSString, CachedDiveSites>()
    private let maxAge: TimeInterval = 300 // 5 минут
    
    func get(region: MapRegion) -> [DiveSite]? {
        let key = cacheKey(for: region)
        guard let cached = cache.object(forKey: key as NSString) else {
            return nil
        }
        
        if Date().timeIntervalSince(cached.timestamp) > maxAge {
            cache.removeObject(forKey: key as NSString)
            return nil
        }
        
        return cached.sites
    }
    
    func set(_ sites: [DiveSite], for region: MapRegion) {
        let key = cacheKey(for: region)
        let cached = CachedDiveSites(sites: sites, timestamp: Date())
        cache.setObject(cached, forKey: key as NSString)
    }
}
```

---

## API дизайн

### REST API (рекомендуется для геосервисов)

**Почему REST, а не GraphQL:**
- Проще кэшировать (URL = cache key)
- Меньше overhead для простых запросов
- Лучше для мобильных (меньше payload)
- GraphQL хорош для сложных связанных данных, но здесь запросы простые

### Endpoints

#### 1. Поиск дайвсайтов (основной)

```
GET /api/v1/dive-sites/search
```

**Query параметры:**

| Параметр | Тип | Обязательный | Описание |
|----------|-----|--------------|----------|
| `lat` | float | Да | Широта |
| `lng` | float | Да | Долгота |
| `radius` | int | Нет | Радиус в метрах (default: 50000 = 50km) |
| `difficulty` | int | Нет | 1-4 (beginner-expert) |
| `site_types` | string[] | Нет | reef,wreck,wall (comma-separated) |
| `min_depth` | float | Нет | Минимальная глубина |
| `max_depth` | float | Нет | Максимальная глубина |
| `min_rating` | float | Нет | Минимальный рейтинг (0-5) |
| `access_type` | string[] | Нет | shore,boat |
| `country` | string | Нет | Фильтр по стране |
| `sort` | string | Нет | distance,rating,popularity,newest (default: distance) |
| `limit` | int | Нет | Количество результатов (default: 20, max: 100) |
| `cursor` | string | Нет | Cursor для пагинации |

**Пример запроса:**

```http
GET /api/v1/dive-sites/search?lat=20.0&lng=-80.0&radius=50000&difficulty=2&site_types=reef,wreck&sort=distance&limit=20
```

**Пример ответа:**

```json
{
  "success": true,
  "data": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "name": "Blue Hole",
      "latitude": 20.001,
      "longitude": -80.001,
      "distance_meters": 1250,
      "site_types": ["reef", "wall"],
      "difficulty_level": 2,
      "depth_min": 5,
      "depth_max": 40,
      "average_rating": 4.8,
      "review_count": 234,
      "country": "Belize",
      "region": "Ambergris Caye",
      "photo_urls": ["https://cdn.../photo1.jpg"],
      "access_type": ["boat"]
    }
  ],
  "pagination": {
    "has_more": true,
    "next_cursor": "1250|550e8400-e29b-41d4-a716-446655440000",
    "limit": 20
  },
  "meta": {
    "total_in_radius": 156,
    "query_time_ms": 45
  }
}
```

#### 2. Поиск по bounding box (для карты)

```
GET /api/v1/dive-sites/map
```

**Query параметры:**

| Параметр | Тип | Обязательный | Описание |
|----------|-----|--------------|----------|
| `north` | float | Да | Северная граница |
| `south` | float | Да | Южная граница |
| `east` | float | Да | Восточная граница |
| `west` | float | Да | Западная граница |
| `zoom` | int | Нет | Уровень зума (для clustering) |
| `filters` | JSON | Нет | Те же фильтры, что и в /search |

**SQL для bounding box:**

```sql
SELECT * FROM dive_sites
WHERE is_active = true
  AND location && ST_MakeEnvelope($west, $south, $east, $north, 4326)::geography
  -- && оператор использует GIST индекс
LIMIT 500; -- Ограничение для карты
```

#### 3. Clustering (группировка точек)

```
GET /api/v1/dive-sites/clusters
```

**Query параметры:**
- Те же, что и `/map`, плюс:
- `cluster_distance` (int): Расстояние для кластеризации в пикселях (default: 50)

**Ответ:**

```json
{
  "clusters": [
    {
      "id": "cluster_1",
      "latitude": 20.0,
      "longitude": -80.0,
      "count": 15,
      "bounds": {
        "north": 20.01,
        "south": 19.99,
        "east": -79.99,
        "west": -80.01
      }
    }
  ],
  "points": [
    // Точки вне кластеров
  ]
}
```

#### 4. Fallback (без геолокации)

```
GET /api/v1/dive-sites/popular
```

Возвращает популярные дайвсайты по стране или глобально.

---

## Предзагрузка и clustering

### Предзагрузка при движении карты

**Стратегия на клиенте (iOS):**

```swift
class MapPreloadManager {
    private let apiService: APIService
    private var loadedRegions: Set<String> = []
    private let preloadDistance: Double = 0.1 // ~11km
    
    func onMapRegionChanged(_ region: MapRegion) {
        // 1. Проверяем, загружены ли соседние регионы
        let regionsToLoad = calculateRegionsToPreload(region)
        
        for regionToLoad in regionsToLoad {
            let key = regionKey(for: regionToLoad)
            if !loadedRegions.contains(key) {
                // 2. Асинхронная предзагрузка
                Task {
                    await preloadRegion(regionToLoad)
                    loadedRegions.insert(key)
                }
            }
        }
    }
    
    private func calculateRegionsToPreload(_ center: MapRegion) -> [MapRegion] {
        // Предзагружаем 8 соседних регионов
        var regions: [MapRegion] = []
        let delta = preloadDistance
        
        for latDelta in [-delta, 0, delta] {
            for lngDelta in [-delta, 0, delta] {
                if latDelta == 0 && lngDelta == 0 { continue }
                let newCenter = CLLocationCoordinate2D(
                    latitude: center.center.latitude + latDelta,
                    longitude: center.center.longitude + lngDelta
                )
                regions.append(MapRegion(center: newCenter, span: region.span))
            }
        }
        return regions
    }
}
```

**Стратегия на сервере:**

```go
// Endpoint для предзагрузки нескольких регионов
POST /api/v1/dive-sites/batch-search

Body:
{
  "regions": [
    {"lat": 20.0, "lng": -80.0, "radius": 50000},
    {"lat": 20.1, "lng": -80.0, "radius": 50000},
    // ...
  ],
  "filters": {...}
}

// Параллельная обработка на сервере
func BatchSearch(regions []Region, filters Filters) (map[string][]DiveSite, error) {
    results := make(map[string][]DiveSite)
    var wg sync.WaitGroup
    var mu sync.Mutex
    
    for _, region := range regions {
        wg.Add(1)
        go func(r Region) {
            defer wg.Done()
            sites, _ := SearchDiveSites(r.Lat, r.Lng, r.Radius, filters)
            mu.Lock()
            results[regionKey(r)] = sites
            mu.Unlock()
        }(region)
    }
    
    wg.Wait()
    return results, nil
}
```

### Clustering на сервере

**Алгоритм: Supercluster (адаптированный для сервера)**

```go
type Cluster struct {
    ID       string    `json:"id"`
    Lat      float64   `json:"latitude"`
    Lng      float64   `json:"longitude"`
    Count    int       `json:"count"`
    Bounds   Bounds    `json:"bounds,omitempty"`
}

func ClusterDiveSites(
    sites []DiveSite,
    zoom int,
    clusterDistance float64, // в градусах
) ([]Cluster, []DiveSite) {
    if zoom < 10 {
        // Низкий зум - возвращаем только кластеры
        return createClusters(sites, clusterDistance), nil
    } else if zoom > 15 {
        // Высокий зум - возвращаем все точки
        return nil, sites
    } else {
        // Средний зум - смешанный режим
        clusters, points := createMixedClusters(sites, clusterDistance, zoom)
        return clusters, points
    }
}

func createClusters(sites []DiveSite, distance float64) []Cluster {
    clusters := []Cluster{}
    used := make(map[int]bool)
    
    for i, site := range sites {
        if used[i] {
            continue
        }
        
        cluster := Cluster{
            ID:    fmt.Sprintf("cluster_%d", i),
            Lat:   site.Latitude,
            Lng:   site.Longitude,
            Count: 1,
        }
        
        // Находим соседние точки
        for j := i + 1; j < len(sites); j++ {
            if used[j] {
                continue
            }
            
            dist := haversineDistance(
                site.Latitude, site.Longitude,
                sites[j].Latitude, sites[j].Longitude,
            )
            
            if dist <= distance {
                cluster.Count++
                used[j] = true
                // Обновляем центр кластера (среднее)
                cluster.Lat = (cluster.Lat*float64(cluster.Count-1) + sites[j].Latitude) / float64(cluster.Count)
                cluster.Lng = (cluster.Lng*float64(cluster.Count-1) + sites[j].Longitude) / float64(cluster.Count)
            }
        }
        
        clusters = append(clusters, cluster)
        used[i] = true
    }
    
    return clusters
}
```

**Оптимизированный SQL для clustering:**

```sql
-- Используем PostGIS для предварительной группировки
WITH grid AS (
    SELECT 
        ST_SnapToGrid(location::geometry, 0.01) as grid_point, -- ~1km grid
        COUNT(*) as count,
        array_agg(id) as site_ids
    FROM dive_sites
    WHERE is_active = true
      AND location && ST_MakeEnvelope($west, $south, $east, $north, 4326)::geography
    GROUP BY grid_point
)
SELECT 
    ST_Y(grid_point) as latitude,
    ST_X(grid_point) as longitude,
    count,
    site_ids
FROM grid
WHERE count > 1; -- Только кластеры (count > 1)
```

---

## Оптимизация payload

### DTO стратегия (Data Transfer Object)

**Проблема**: Полная модель `DiveSite` содержит много данных, которые не нужны для списка.

**Решение**: Разные DTO для разных сценариев.

#### 1. List DTO (для списка/карты)

```go
type DiveSiteListItem struct {
    ID            string    `json:"id"`
    Name          string    `json:"name"`
    Latitude      float64   `json:"latitude"`
    Longitude     float64   `json:"longitude"`
    DistanceMeters int      `json:"distance_meters,omitempty"`
    SiteTypes     []string  `json:"site_types"`
    Difficulty    int       `json:"difficulty_level"`
    DepthMin      *float64  `json:"depth_min,omitempty"`
    DepthMax      *float64  `json:"depth_max,omitempty"`
    Rating        float64   `json:"average_rating"`
    ReviewCount   int       `json:"review_count"`
    Country       string    `json:"country,omitempty"`
    Region        string    `json:"region,omitempty"`
    ThumbnailURL  *string   `json:"thumbnail_url,omitempty"` // Только первое фото
}

// Размер: ~200-300 bytes (vs 2-3KB для полной модели)
```

#### 2. Detail DTO (для детальной страницы)

```go
type DiveSiteDetail struct {
    DiveSiteListItem
    Description      string            `json:"description"`
    LocalizedName    map[string]string `json:"localized_name,omitempty"`
    WaterTempMin     *float64          `json:"water_temp_min,omitempty"`
    WaterTempMax     *float64          `json:"water_temp_max,omitempty"`
    AccessType       []string          `json:"access_type"`
    MarineLife       []string          `json:"marine_life"`
    PhotoURLs        []string          `json:"photo_urls"`
    VideoURLs        []string          `json:"video_urls,omitempty"`
    AISummary        *string           `json:"ai_summary,omitempty"`
    AffiliatedCenters []string         `json:"affiliated_centers,omitempty"`
}
```

#### 3. Map DTO (минимальный для карты)

```go
type DiveSiteMapPoint struct {
    ID       string   `json:"id"`
    Lat      float64  `json:"lat"`
    Lng      float64  `json:"lng"`
    Type     string   `json:"type"` // "site" | "cluster"
    Count    *int     `json:"count,omitempty"` // Для кластеров
}
// Размер: ~50 bytes
```

### Сжатие ответов

```go
// Используйте gzip compression
func DiveSiteHandler(c *gin.Context) {
    c.Header("Content-Encoding", "gzip")
    c.Header("Content-Type", "application/json")
    
    gz := gzip.NewWriter(c.Writer)
    defer gz.Close()
    
    json.NewEncoder(gz).Encode(response)
}

// На клиенте (iOS) - автоматическая распаковка
// URLSession автоматически распаковывает gzip
```

### Поля по запросу (field selection)

```http
GET /api/v1/dive-sites/search?lat=20&lng=-80&fields=id,name,lat,lng,rating
```

```go
func parseFields(fieldsParam string) map[string]bool {
    if fieldsParam == "" {
        return nil // Все поля
    }
    
    selected := make(map[string]bool)
    for _, field := range strings.Split(fieldsParam, ",") {
        selected[strings.TrimSpace(field)] = true
    }
    return selected
}

func toListItem(site DiveSite, fields map[string]bool) map[string]interface{} {
    result := make(map[string]interface{})
    
    if fields == nil || fields["id"] {
        result["id"] = site.ID
    }
    if fields == nil || fields["name"] {
        result["name"] = site.Name
    }
    // ...
    
    return result
}
```

---

## Fallback стратегии

### Если геолокация недоступна

#### 1. По IP адресу (GeoIP)

```go
import "github.com/oschwald/geoip2-golang"

func GetLocationFromIP(ip string) (float64, float64, error) {
    db, err := geoip2.Open("GeoLite2-City.mmdb")
    if err != nil {
        return 0, 0, err
    }
    defer db.Close()
    
    record, err := db.City(net.ParseIP(ip))
    if err != nil {
        return 0, 0, err
    }
    
    return record.Location.Latitude, record.Location.Longitude, nil
}
```

#### 2. По популярным дайвсайтам в стране

```sql
-- Получить популярные дайвсайты по стране
SELECT * FROM dive_sites
WHERE is_active = true
  AND country = $1
ORDER BY 
    (average_rating * LOG(review_count + 1)) DESC, -- Взвешенный рейтинг
    review_count DESC
LIMIT 50;
```

#### 3. Глобальные популярные

```sql
-- Топ дайвсайтов глобально
SELECT * FROM dive_sites
WHERE is_active = true
  AND review_count >= 10
ORDER BY 
    (average_rating * LOG(review_count + 1)) DESC
LIMIT 100;
```

#### 4. API endpoint для fallback

```
GET /api/v1/dive-sites/popular?country=Belize&limit=20
```

```go
func GetPopularDiveSites(country string, limit int) ([]DiveSite, error) {
    query := `
        SELECT * FROM dive_sites
        WHERE is_active = true
    `
    args := []interface{}{limit}
    
    if country != "" {
        query += " AND country = $2"
        args = append(args, country)
    }
    
    query += `
        ORDER BY 
            (average_rating * LN(review_count + 1)) DESC,
            review_count DESC
        LIMIT $1
    `
    
    // Выполнить запрос...
}
```

---

## Примеры кода

### Go: Полный пример сервиса

```go
package main

import (
    "context"
    "database/sql"
    "encoding/json"
    "fmt"
    "log"
    "time"
    
    "github.com/gin-gonic/gin"
    "github.com/go-redis/redis/v9"
    "github.com/jackc/pgx/v5"
    "github.com/jackc/pgx/v5/pgxpool"
)

type DiveSiteService struct {
    db    *pgxpool.Pool
    redis *redis.Client
}

type DiveSiteFilters struct {
    DifficultyLevel *int      `json:"difficulty_level"`
    SiteTypes       []string  `json:"site_types"`
    MinDepth        *float64  `json:"min_depth"`
    MaxDepth        *float64  `json:"max_depth"`
    MinRating       *float64  `json:"min_rating"`
    AccessTypes     []string  `json:"access_types"`
    Country         *string   `json:"country"`
}

type SearchRequest struct {
    Lat      float64         `json:"lat" binding:"required"`
    Lng      float64         `json:"lng" binding:"required"`
    Radius   int             `json:"radius"` // meters
    Filters  DiveSiteFilters `json:"filters"`
    SortBy   string          `json:"sort"` // distance, rating, popularity, newest
    Limit    int             `json:"limit"`
    Cursor   string          `json:"cursor"`
}

func (s *DiveSiteService) SearchDiveSites(ctx context.Context, req SearchRequest) ([]DiveSiteListItem, string, error) {
    // 1. Проверка кэша
    cacheKey := s.generateCacheKey(req)
    cached, err := s.redis.Get(ctx, cacheKey).Result()
    if err == nil {
        var result CachedResult
        json.Unmarshal([]byte(cached), &result)
        return result.Sites, result.NextCursor, nil
    }
    
    // 2. Запрос к БД
    sites, nextCursor, err := s.searchInDB(ctx, req)
    if err != nil {
        return nil, "", err
    }
    
    // 3. Кэширование
    ttl := s.getCacheTTL(req.Radius)
    result := CachedResult{Sites: sites, NextCursor: nextCursor}
    data, _ := json.Marshal(result)
    s.redis.Set(ctx, cacheKey, data, ttl)
    
    return sites, nextCursor, nil
}

func (s *DiveSiteService) searchInDB(ctx context.Context, req SearchRequest) ([]DiveSiteListItem, string, error) {
    // Установка значений по умолчанию
    if req.Radius == 0 {
        req.Radius = 50000 // 50km
    }
    if req.Limit == 0 {
        req.Limit = 20
    }
    if req.SortBy == "" {
        req.SortBy = "distance"
    }
    
    // Парсинг cursor
    var cursorDistance float64
    var cursorID string
    if req.Cursor != "" {
        fmt.Sscanf(req.Cursor, "%f|%s", &cursorDistance, &cursorID)
    }
    
    query := `
        WITH geo_filtered AS (
            SELECT 
                id,
                name,
                latitude,
                longitude,
                site_types,
                difficulty_level,
                depth_min,
                depth_max,
                average_rating,
                review_count,
                country,
                region,
                ST_Distance(
                    location,
                    ST_SetSRID(ST_MakePoint($1, $2), 4326)::geography
                ) as distance_meters
            FROM dive_sites
            WHERE is_active = true
              AND ST_DWithin(
                  location,
                  ST_SetSRID(ST_MakePoint($1, $2), 4326)::geography,
                  $3
              )
              AND ($13 = '' OR (distance_meters, id::text) > ($14, $13))
        ),
        filtered AS (
            SELECT *
            FROM geo_filtered
            WHERE 
                ($4::INTEGER IS NULL OR difficulty_level = $4)
                AND ($5::TEXT[] IS NULL OR site_types && $5)
                AND ($6::DOUBLE PRECISION IS NULL OR depth_max >= $6)
                AND ($7::DOUBLE PRECISION IS NULL OR depth_min <= $7)
                AND ($8::DOUBLE PRECISION IS NULL OR average_rating >= $8)
                AND ($9::TEXT[] IS NULL OR access_type && $9)
                AND ($10::TEXT IS NULL OR country = $10)
        )
        SELECT 
            id,
            name,
            latitude,
            longitude,
            site_types,
            difficulty_level,
            depth_min,
            depth_max,
            average_rating,
            review_count,
            country,
            region,
            ROUND(distance_meters::numeric, 0)::INTEGER as distance_meters
        FROM filtered
        ORDER BY 
            CASE 
                WHEN $11 = 'distance' THEN distance_meters
                WHEN $11 = 'rating' THEN -average_rating
                WHEN $11 = 'popularity' THEN -review_count
                ELSE -EXTRACT(EPOCH FROM created_at)
            END,
            id
        LIMIT $12 + 1
    `
    
    args := []interface{}{
        req.Lng, req.Lat, req.Radius,
        req.Filters.DifficultyLevel,
        req.Filters.SiteTypes,
        req.Filters.MinDepth,
        req.Filters.MaxDepth,
        req.Filters.MinRating,
        req.Filters.AccessTypes,
        req.Filters.Country,
        req.SortBy,
        req.Limit,
        cursorID,
        cursorDistance,
    }
    
    rows, err := s.db.Query(ctx, query, args...)
    if err != nil {
        return nil, "", err
    }
    defer rows.Close()
    
    var sites []DiveSiteListItem
    var nextCursor string
    
    count := 0
    for rows.Next() {
        if count >= req.Limit {
            // Есть еще данные - формируем cursor
            var lastSite DiveSiteListItem
            rows.Scan(
                &lastSite.ID, &lastSite.Name,
                &lastSite.Latitude, &lastSite.Longitude,
                &lastSite.SiteTypes, &lastSite.Difficulty,
                &lastSite.DepthMin, &lastSite.DepthMax,
                &lastSite.Rating, &lastSite.ReviewCount,
                &lastSite.Country, &lastSite.Region,
                &lastSite.DistanceMeters,
            )
            nextCursor = fmt.Sprintf("%.2f|%s", lastSite.DistanceMeters, lastSite.ID)
            break
        }
        
        var site DiveSiteListItem
        err := rows.Scan(
            &site.ID, &site.Name,
            &site.Latitude, &site.Longitude,
            &site.SiteTypes, &site.Difficulty,
            &site.DepthMin, &site.DepthMax,
            &site.Rating, &site.ReviewCount,
            &site.Country, &site.Region,
            &site.DistanceMeters,
        )
        if err != nil {
            continue
        }
        
        sites = append(sites, site)
        count++
    }
    
    return sites, nextCursor, nil
}

func (s *DiveSiteService) generateCacheKey(req SearchRequest) string {
    filtersHash := hashFilters(req.Filters)
    return fmt.Sprintf(
        "divesites:geo:%d:%d:r%d:f%s:sort%s:limit%d:cursor%s",
        int(req.Lat*1000),
        int(req.Lng*1000),
        req.Radius,
        filtersHash,
        req.SortBy,
        req.Limit,
        req.Cursor,
    )
}

func (s *DiveSiteService) getCacheTTL(radius int) time.Duration {
    if radius < 10000 {
        return 5 * time.Minute
    } else if radius < 50000 {
        return 15 * time.Minute
    }
    return 1 * time.Hour
}

// HTTP Handler
func setupRoutes(service *DiveSiteService) *gin.Engine {
    r := gin.Default()
    
    r.GET("/api/v1/dive-sites/search", func(c *gin.Context) {
        var req SearchRequest
        if err := c.ShouldBindQuery(&req); err != nil {
            c.JSON(400, gin.H{"error": err.Error()})
            return
        }
        
        sites, nextCursor, err := service.SearchDiveSites(c.Request.Context(), req)
        if err != nil {
            c.JSON(500, gin.H{"error": err.Error()})
            return
        }
        
        c.JSON(200, gin.H{
            "success": true,
            "data": sites,
            "pagination": gin.H{
                "has_more": nextCursor != "",
                "next_cursor": nextCursor,
                "limit": req.Limit,
            },
        })
    })
    
    return r
}

func main() {
    // Инициализация БД
    db, err := pgxpool.New(context.Background(), "postgres://user:pass@localhost/divehub?sslmode=disable")
    if err != nil {
        log.Fatal(err)
    }
    
    // Инициализация Redis
    rdb := redis.NewClient(&redis.Options{
        Addr: "localhost:6379",
    })
    
    service := &DiveSiteService{db: db, redis: rdb}
    router := setupRoutes(service)
    
    router.Run(":8080")
}
```

### SQL: Полный пример запроса

```sql
-- Оптимизированный запрос с объяснением
EXPLAIN (ANALYZE, BUFFERS, VERBOSE)
WITH geo_filtered AS (
    SELECT 
        id,
        name,
        latitude,
        longitude,
        site_types,
        difficulty_level,
        depth_min,
        depth_max,
        average_rating,
        review_count,
        country,
        region,
        ST_Distance(
            location,
            ST_SetSRID(ST_MakePoint(-80.0, 20.0), 4326)::geography
        ) as distance_meters
    FROM dive_sites
    WHERE is_active = true
      AND ST_DWithin(
          location,
          ST_SetSRID(ST_MakePoint(-80.0, 20.0), 4326)::geography,
          50000 -- 50km
      )
),
filtered AS (
    SELECT *
    FROM geo_filtered
    WHERE 
        (2 IS NULL OR difficulty_level = 2)
        AND (ARRAY['reef', 'wreck']::TEXT[] IS NULL OR site_types && ARRAY['reef', 'wreck'])
        AND (NULL::DOUBLE PRECISION IS NULL OR depth_max >= NULL)
        AND (NULL::DOUBLE PRECISION IS NULL OR depth_min <= NULL)
        AND (4.0 IS NULL OR average_rating >= 4.0)
)
SELECT 
    id,
    name,
    latitude,
    longitude,
    site_types,
    difficulty_level,
    depth_min,
    depth_max,
    average_rating,
    review_count,
    country,
    region,
    ROUND(distance_meters::numeric, 0)::INTEGER as distance_meters
FROM filtered
ORDER BY distance_meters, id
LIMIT 20;

-- Ожидаемый результат EXPLAIN:
-- Index Scan using idx_dive_sites_location_gist on dive_sites
--   Index Cond: (location && '...'::geography)
--   Filter: (is_active = true) AND (st_dwithin(...))
-- Planning Time: 0.5 ms
-- Execution Time: 15-50 ms (зависит от количества результатов)
```

---

## Лучшие практики для high performance geo API

### 1. Мониторинг производительности

```go
// Middleware для логирования времени запроса
func PerformanceMiddleware() gin.HandlerFunc {
    return func(c *gin.Context) {
        start := time.Now()
        c.Next()
        duration := time.Since(start)
        
        if duration > 200*time.Millisecond {
            log.Warnf("Slow request: %s took %v", c.Request.URL.Path, duration)
        }
        
        c.Header("X-Response-Time", fmt.Sprintf("%dms", duration.Milliseconds()))
    }
}
```

### 2. Connection pooling

```go
// Настройка пула соединений
config, _ := pgxpool.ParseConfig("postgres://...")
config.MaxConns = 25
config.MinConns = 5
config.MaxConnLifetime = time.Hour
config.MaxConnIdleTime = 30 * time.Minute

pool, _ := pgxpool.NewWithConfig(ctx, config)
```

### 3. Rate limiting

```go
import "golang.org/x/time/rate"

func RateLimitMiddleware() gin.HandlerFunc {
    limiter := rate.NewLimiter(rate.Every(time.Second), 10) // 10 req/sec
    
    return func(c *gin.Context) {
        if !limiter.Allow() {
            c.JSON(429, gin.H{"error": "Too many requests"})
            c.Abort()
            return
        }
        c.Next()
    }
}
```

### 4. Graceful shutdown

```go
func main() {
    server := &http.Server{
        Addr:    ":8080",
        Handler: router,
    }
    
    go func() {
        if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
            log.Fatalf("Server failed: %v", err)
        }
    }()
    
    // Graceful shutdown
    quit := make(chan os.Signal, 1)
    signal.Notify(quit, os.Interrupt, syscall.SIGTERM)
    <-quit
    
    ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
    defer cancel()
    
    if err := server.Shutdown(ctx); err != nil {
        log.Fatal("Server forced to shutdown:", err)
    }
}
```

### 5. Health checks

```go
r.GET("/health", func(c *gin.Context) {
    // Проверка БД
    ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
    defer cancel()
    
    if err := db.Ping(ctx); err != nil {
        c.JSON(503, gin.H{"status": "unhealthy", "db": "down"})
        return
    }
    
    // Проверка Redis
    if err := redis.Ping(ctx).Err(); err != nil {
        c.JSON(503, gin.H{"status": "unhealthy", "redis": "down"})
        return
    }
    
    c.JSON(200, gin.H{"status": "healthy"})
})
```

---

## Итоговая архитектура

### Компоненты:

1. **API Layer (Go)**
   - Gin/Fiber framework
   - Middleware: rate limiting, compression, logging
   - DTO transformation
   - Caching layer

2. **Database Layer (PostgreSQL + PostGIS)**
   - GIST индексы для геоданных
   - Композитные индексы для фильтров
   - Materialized views для сложных запросов

3. **Cache Layer (Redis)**
   - Кэширование результатов запросов
   - TTL стратегия
   - Инвалидация при обновлениях

4. **Client Layer (iOS)**
   - Локальное кэширование
   - Предзагрузка регионов
   - Clustering на клиенте (опционально)

### Метрики производительности:

- **Цель**: < 200ms response time
- **Кэш hit rate**: > 70%
- **Database query time**: < 50ms
- **Cache lookup time**: < 5ms
- **Payload size**: < 50KB для списка из 20 элементов

### Масштабирование:

- **Горизонтальное**: Несколько Go серверов за load balancer
- **Вертикальное**: Увеличение ресурсов БД (больше RAM для индексов)
- **Read replicas**: Для чтения использовать реплики PostgreSQL
- **CDN**: Для статических данных (фото, видео)

---

## Заключение

Эта архитектура обеспечивает:
- ✅ Быстрый геопоиск (< 50ms в БД)
- ✅ Эффективное кэширование
- ✅ Масштабируемость до 100k+ записей
- ✅ Оптимизированный payload
- ✅ Надежные fallback стратегии

**Следующие шаги:**
1. Реализовать базовый API на Go
2. Настроить PostGIS индексы
3. Интегрировать Redis кэширование
4. Добавить мониторинг (Prometheus)
5. Нагрузочное тестирование
6. Оптимизация на основе метрик
