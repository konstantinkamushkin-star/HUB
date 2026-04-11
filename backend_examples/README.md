# Backend Examples для DiveHub Geo API

Этот каталог содержит примеры реализации backend для высоконагруженного геосервиса.

## Структура

```
backend_examples/
├── migrations/
│   └── 001_create_dive_sites.sql    # SQL миграция с индексами
├── go_service/
│   ├── main.go                      # Точка входа
│   ├── dive_site_service.go         # Бизнес-логика
│   ├── api_handlers.go              # HTTP handlers
│   └── go.mod                       # Go зависимости
└── README.md                        # Этот файл
```

## Установка и запуск

### Требования

- Go 1.21+
- PostgreSQL 15+ с PostGIS 3.3+
- Redis 7+

### 1. Настройка базы данных

```bash
# Создать базу данных
createdb divehub

# Применить миграцию
psql -d divehub -f migrations/001_create_dive_sites.sql
```

### 2. Настройка переменных окружения

```bash
export DATABASE_URL="postgres://user:password@localhost/divehub?sslmode=disable"
export REDIS_URL="localhost:6379"
export PORT="8080"
```

### 3. Установка зависимостей

```bash
cd go_service
go mod download
```

### 4. Запуск сервера

```bash
go run main.go dive_site_service.go api_handlers.go
```

## Тестирование API

### Поиск дайвсайтов

```bash
curl "http://localhost:8080/api/v1/dive-sites/search?lat=20.0&lng=-80.0&radius=50000&limit=20"
```

### Поиск по bounding box (для карты)

```bash
curl "http://localhost:8080/api/v1/dive-sites/map?north=20.1&south=19.9&east=-79.9&west=-80.1"
```

### Популярные дайвсайты (fallback)

```bash
curl "http://localhost:8080/api/v1/dive-sites/popular?country=Belize&limit=20"
```

### Health check

```bash
curl "http://localhost:8080/health"
```

## Производительность

### Ожидаемые метрики

- Response time: < 200ms (с кэшем < 10ms)
- Database query: < 50ms
- Cache lookup: < 5ms
- Cache hit rate: > 70%

### Мониторинг

Используйте заголовок `X-Response-Time` для отслеживания времени ответа.

## Дальнейшие шаги

1. Добавить аутентификацию (JWT)
2. Добавить rate limiting
3. Настроить логирование (structured logging)
4. Добавить метрики (Prometheus)
5. Настроить CI/CD
6. Добавить тесты (unit + integration)
