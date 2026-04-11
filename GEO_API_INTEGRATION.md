# Интеграция Geo API в DiveHub

## ✅ Что было сделано

### 1. Обновлен NetworkService
- ✅ Добавлены новые методы для геопоиска:
  - `searchDiveSitesByLocation()` - поиск по радиусу
  - `searchDiveSitesInBounds()` - поиск по bounding box (для карты)
  - `getPopularDiveSites()` - популярные сайты (fallback)
- ✅ Добавлены модели ответов: `DiveSiteSearchResult`, `PaginationInfo`, `SearchMeta`
- ✅ Сохранена обратная совместимость со старым API

### 2. Обновлен DiveSiteFilters
- ✅ Добавлены поля: `country`, `accessTypes`
- ✅ Добавлены helper методы: `radiusMeters`, `shouldUseGeoSearch`

### 3. Обновлены ViewModels
- ✅ **ExploreViewModel**: Использует геопоиск если доступна локация
- ✅ **MapViewModel**: Использует bounding box поиск для карты
- ✅ Автоматический fallback на старый API или популярные сайты

### 4. Добавлен GeoCacheService
- ✅ Кэширование результатов геопоиска на клиенте
- ✅ TTL: 5 минут
- ✅ LRU кэш (максимум 50 записей)
- ✅ Автоматическая очистка истекших записей

## 🔧 Настройка Backend

### Требования
1. **PostgreSQL 15+** с **PostGIS 3.3+**
2. **Redis 7+** для кэширования
3. **Go 1.21+** (или Node.js, если используете другой backend)

### Шаги установки

#### 1. Установка PostGIS
```bash
# macOS
brew install postgis

# Ubuntu/Debian
sudo apt-get install postgresql-postgis

# В PostgreSQL
psql -d divehub -c "CREATE EXTENSION IF NOT EXISTS postgis;"
```

#### 2. Применение миграции
```bash
psql -d divehub -f backend_examples/migrations/001_create_dive_sites.sql
```

#### 3. Настройка Go сервиса (опционально)
```bash
cd backend_examples/go_service
export DATABASE_URL="postgres://user:pass@localhost/divehub"
export REDIS_URL="localhost:6379"
go run main.go dive_site_service.go api_handlers.go
```

#### 4. Обновление baseURL в NetworkService
Если используете Go сервис, обновите `baseURL` в `NetworkService.swift`:
```swift
#if DEBUG
let baseURL = "http://localhost:8080" // Go сервис на порту 8080
#else
let baseURL = "https://api.divehub.com"
#endif
```

## 📱 Использование в iOS приложении

### Автоматическое использование геопоиска

Геопоиск автоматически используется когда:
1. В `DiveSiteFilters` установлены `centerLatitude` и `centerLongitude`
2. Приложение имеет доступ к геолокации

### Пример использования

```swift
// В ExploreViewModel или MapViewModel
var filters = DiveSiteFilters()
filters.centerLatitude = userLocation.coordinate.latitude
filters.centerLongitude = userLocation.coordinate.longitude
filters.maxDistance = 50 // 50km
filters.siteType = .reef
filters.difficulty = .intermediate

// Автоматически использует геопоиск
await loadData()
```

### Fallback стратегия

Если геолокация недоступна:
1. Используется старый API (`/api/dive-sites`)
2. Если старый API недоступен, загружаются популярные сайты (`/api/v1/dive-sites/popular`)

## 🔄 Миграция существующего кода

### Если используете старый API

Старый код продолжит работать:
```swift
// Старый способ (все еще работает)
let sites = try await NetworkService.shared.getDiveSites(filters: filters)
```

### Рекомендуется обновить на новый API

```swift
// Новый способ (с геопоиском)
if filters.shouldUseGeoSearch,
   let lat = filters.centerLatitude,
   let lng = filters.centerLongitude {
    let result = try await NetworkService.shared.searchDiveSitesByLocation(
        latitude: lat,
        longitude: lng,
        radius: filters.radiusMeters,
        filters: filters,
        sortBy: "distance",
        limit: 50
    )
    sites = result.data
}
```

## 🎯 API Endpoints

### Новые endpoints (рекомендуется)

```
GET /api/v1/dive-sites/search
  ?lat=20.0&lng=-80.0&radius=50000
  &difficulty=2&site_types=reef
  &sort=distance&limit=20

GET /api/v1/dive-sites/map
  ?north=20.1&south=19.9&east=-79.9&west=-80.1
  &limit=500

GET /api/v1/dive-sites/popular
  ?country=Belize&limit=20
```

### Старые endpoints (для совместимости)

```
GET /api/dive-sites
  ?language=en&page=1&limit=20
  &diveTypes=reef&difficultyLevel=2
```

## 📊 Производительность

### Ожидаемые улучшения

- **Response time**: < 200ms (с кэшем < 10ms)
- **Database query**: < 50ms (с GIST индексом)
- **Cache hit rate**: > 70%
- **Payload size**: ~200-300 bytes на элемент (vs 2-3KB ранее)

### Мониторинг

Используйте заголовок `X-Response-Time` для отслеживания производительности:
```swift
// В NetworkService уже есть логирование
#if DEBUG
print("📥 Response [\(httpResponse.statusCode)]: \(responseString.prefix(500))")
#endif
```

## 🐛 Отладка

### Проверка использования геопоиска

Добавьте логирование в ViewModels:
```swift
DebugLogger.log(
    location: "ExploreViewModel",
    message: "Using geo search",
    data: ["shouldUseGeoSearch": filters.shouldUseGeoSearch]
)
```

### Проверка кэша

```swift
// Очистить кэш
GeoCacheService.shared.clearCache()

// Проверить размер кэша
// (добавьте свойство в GeoCacheService для отладки)
```

## 📝 Следующие шаги

1. ✅ **Backend**: Настроить Go сервис или адаптировать существующий
2. ✅ **База данных**: Применить миграцию с индексами
3. ✅ **Redis**: Настроить кэширование на сервере
4. ⏳ **Тестирование**: Протестировать на реальных данных
5. ⏳ **Мониторинг**: Настроить метрики производительности
6. ⏳ **Оптимизация**: Настроить TTL кэша под ваши нужды

## 🔗 Связанные документы

- [GEO_API_ARCHITECTURE.md](./GEO_API_ARCHITECTURE.md) - Полная архитектура
- [GEO_API_QUICK_REFERENCE.md](./GEO_API_QUICK_REFERENCE.md) - Быстрая справка
- [backend_examples/README.md](./backend_examples/README.md) - Примеры кода

## ❓ FAQ

### Q: Нужно ли обновлять существующий backend?
A: Нет, старый API продолжит работать. Новый API - это дополнение.

### Q: Что если backend еще не поддерживает новый API?
A: Приложение автоматически использует старый API как fallback.

### Q: Как отключить геопоиск?
A: Не устанавливайте `centerLatitude` и `centerLongitude` в фильтрах.

### Q: Как настроить TTL кэша?
A: Измените `cacheExpirationTime` в `GeoCacheService.swift` (по умолчанию 5 минут).
