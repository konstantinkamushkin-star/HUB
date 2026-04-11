# Руководство по миграции: Новые endpoints для загрузки данных

## Обзор

Новый бэкенд (NestJS) предоставляет оптимизированные endpoints для загрузки дайвсайтов и дайвцентров с использованием PostGIS для геопоиска. Данные импортируются из старого бэкенда, но загрузка происходит через новые оптимизированные endpoints.

## Архитектура

### Новые endpoints (для загрузки данных)
- **Дайвсайты**: `/api/v1/dive-sites/search`, `/api/v1/dive-sites/map`, `/api/v1/dive-sites/popular`
- **Дайвцентры**: `/api/v1/dive-centers/search`, `/api/v1/dive-centers/map`, `/api/v1/dive-centers/popular`

### Старые endpoints (для остальных операций)
- **CRUD операции**: `/api/dive-sites/*`, `/api/dive-centers/*`
- **Аутентификация**: `/api/auth/*`
- **Бронирования**: `/api/bookings/*`
- **Отзывы**: `/api/reviews/*`
- И другие операции из старого бэкенда

## Установка и настройка

### 1. Применить миграции базы данных

```bash
cd backend
psql -d divehub -f migrations/002_create_dive_centers.sql
```

### 2. Импорт данных из старого бэкенда

```bash
cd backend
node import_from_old_backend.js
```

Скрипт импортирует:
- Дайвсайты из `/Users/admin/Desktop/divehub-backend/prisma/dive-sites-data.js`
- Дайвцентры (тестовые данные, можно расширить)

### 3. Запуск нового бэкенда

```bash
cd backend
npm run start:dev
```

Бэкенд будет доступен на `http://localhost:3000`

## Использование в iOS приложении

### Дайвсайты

Приложение автоматически использует новые endpoints, если доступна геолокация:

```swift
// Geo search (если есть локация)
let result = try await NetworkService.shared.searchDiveSitesByLocation(
    latitude: lat,
    longitude: lng,
    radius: 50000,
    filters: filters,
    sortBy: "distance",
    limit: 50
)

// Bounding box search (для карты)
let sites = try await NetworkService.shared.searchDiveSitesInBounds(
    north: north,
    south: south,
    east: east,
    west: west,
    filters: filters,
    limit: 500
)

// Popular sites (fallback)
let sites = try await NetworkService.shared.getPopularDiveSites(
    country: country,
    limit: 20
)
```

### Дайвцентры

Аналогично для дайвцентров:

```swift
// Geo search
let result = try await NetworkService.shared.searchDiveCentersByLocation(
    latitude: lat,
    longitude: lng,
    radius: 50000,
    filters: nil,
    sortBy: "distance",
    limit: 50
)

// Bounding box search
let centers = try await NetworkService.shared.searchDiveCentersInBounds(
    north: north,
    south: south,
    east: east,
    west: west,
    filters: nil,
    limit: 500
)

// Popular centers
let centers = try await NetworkService.shared.getPopularDiveCenters(
    country: country,
    limit: 20
)
```

## Преимущества новых endpoints

1. **Оптимизированный геопоиск**: Использует PostGIS для точного расчета расстояний
2. **Кэширование**: Redis кэш для быстрого доступа к часто запрашиваемым данным
3. **Сортировка по расстоянию**: Правильная сортировка по удаленности от пользователя
4. **Пагинация с курсором**: Эффективная пагинация для больших наборов данных
5. **Производительность**: Оптимизированные SQL запросы с индексами

## Структура базы данных

### Таблица `dive_sites`
- PostGIS geography column для геопоиска
- Индексы для быстрого поиска
- Поддержка фильтрации по типу, сложности, глубине, рейтингу

### Таблица `dive_centers`
- PostGIS geography column для геопоиска
- Индексы для быстрого поиска
- Поддержка фильтрации по услугам, рейтингу, стране

## Отладка

### Проверка подключения к базе данных

```bash
psql -d divehub -c "SELECT COUNT(*) FROM dive_sites;"
psql -d divehub -c "SELECT COUNT(*) FROM dive_centers;"
```

### Проверка PostGIS

```bash
psql -d divehub -c "SELECT PostGIS_version();"
```

### Тестирование endpoints

```bash
# Поиск дайвсайтов
curl "http://localhost:3000/api/v1/dive-sites/search?lat=20.0&lng=-80.0&radius=50000&limit=20"

# Поиск дайвцентров
curl "http://localhost:3000/api/v1/dive-centers/search?lat=20.0&lng=-80.0&radius=50000&limit=20"

# Health check
curl "http://localhost:3000/api/health"
```

## Следующие шаги

1. ✅ Создан модуль dive-centers в новом бэкенде
2. ✅ Добавлены оптимизированные endpoints для дайвцентров
3. ✅ Обновлен скрипт импорта данных
4. ✅ iOS приложение использует новые endpoints для загрузки
5. ⏳ Расширить импорт дайвцентров из старого бэкенда
6. ⏳ Добавить unit тесты для новых endpoints
