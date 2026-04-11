# Geo API - Быстрая справка

## Ключевые решения

### 1. Технологии
- **Backend**: Go (Golang) - производительность, конкурентность
- **База**: PostgreSQL + PostGIS - достаточно для 100k+ записей
- **Кэш**: Redis - обязательно для производительности
- **ElasticSearch**: НЕ нужен (только если требуется full-text search)

### 2. Индексы (критически важно!)

```sql
-- ГЕОИНДЕКС (самый важный!)
CREATE INDEX idx_dive_sites_location_gist 
ON dive_sites USING GIST (location);

-- Композитный для активных + гео
CREATE INDEX idx_dive_sites_active_location 
ON dive_sites USING GIST (location) 
WHERE is_active = true;

-- GIN для массивов
CREATE INDEX idx_dive_sites_site_types 
ON dive_sites USING GIN (site_types);
```

### 3. Порядок фильтров в SQL

```sql
-- ✅ ПРАВИЛЬНО: Сначала гео, потом остальное
WHERE is_active = true
  AND ST_DWithin(location, point, radius)  -- Использует GIST индекс
  AND difficulty_level = 2
  AND site_types @> ARRAY['reef'];

-- ❌ НЕПРАВИЛЬНО: Сначала фильтры, потом гео
WHERE difficulty_level = 2
  AND site_types @> ARRAY['reef']
  AND ST_DWithin(location, point, radius);  -- Full scan!
```

### 4. Пагинация

**Cursor-based (рекомендуется):**
- Быстро всегда (O(log n))
- Стабильно при изменениях данных
- Использует индекс

**Offset:**
- Только для offset < 100
- Медленно на больших offset (O(n))

### 5. Кэширование

**TTL стратегия:**
- < 10km: 5 минут
- < 50km: 15 минут
- > 50km: 1 час

**Ключ кэша:**
```
divesites:geo:{lat}:{lng}:r{radius}:f{hash}:sort{sort}:limit{limit}:cursor{cursor}
```

### 6. DTO стратегия

**List DTO** (~200-300 bytes):
- id, name, lat, lng, distance, types, difficulty, rating, thumbnail

**Detail DTO** (~2-3KB):
- Все поля + description, photos, videos, etc.

### 7. API Endpoints

```
GET /api/v1/dive-sites/search
  ?lat=20.0&lng=-80.0&radius=50000
  &difficulty=2&site_types=reef,wreck
  &sort=distance&limit=20&cursor=...

GET /api/v1/dive-sites/map
  ?north=20.1&south=19.9&east=-79.9&west=-80.1

GET /api/v1/dive-sites/popular
  ?country=Belize&limit=20
```

### 8. Производительность

**Цели:**
- Response time: < 200ms
- DB query: < 50ms
- Cache lookup: < 5ms
- Cache hit rate: > 70%

**Метрики:**
- Используйте `EXPLAIN ANALYZE` для проверки индексов
- Мониторьте `X-Response-Time` заголовок
- Логируйте медленные запросы (> 200ms)

### 9. Fallback стратегии

1. **GeoIP** - определение локации по IP
2. **Популярные по стране** - если известна страна
3. **Глобальные популярные** - если ничего не известно

### 10. Clustering

**Алгоритм:**
- Zoom < 10: только кластеры
- Zoom 10-15: смешанный режим
- Zoom > 15: все точки

**Расстояние кластеризации:**
- ~0.01 градуса (~1km) для среднего зума

## Частые ошибки

1. ❌ Не использовать GIST индекс (full scan)
2. ❌ Неправильный порядок фильтров
3. ❌ Offset пагинация на больших offset
4. ❌ Отсутствие кэширования
5. ❌ Полная модель в списке (большой payload)
6. ❌ Нет индексов на часто используемые фильтры

## Checklist для production

- [ ] PostGIS расширение установлено
- [ ] GIST индексы созданы
- [ ] Redis настроен и работает
- [ ] Кэширование реализовано
- [ ] Cursor пагинация используется
- [ ] DTO для списка (не полная модель)
- [ ] Health check endpoint
- [ ] Мониторинг производительности
- [ ] Rate limiting
- [ ] Graceful shutdown
- [ ] Логирование медленных запросов
