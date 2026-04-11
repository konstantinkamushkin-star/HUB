# Исправление ошибки 500

## Проблема
Сервер возвращает ошибку 500 при обращении к API endpoints.

## Решение

### 1. Убедитесь, что .env файл создан:

```bash
cd backend
cat > .env << 'EOF'
DB_HOST=localhost
DB_PORT=5432
DB_USERNAME=postgres
DB_PASSWORD=postgres
DB_DATABASE=divehub
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=
PORT=3000
NODE_ENV=development
EOF
```

### 2. Остановите все процессы:

```bash
pkill -9 -f "nest start"
lsof -ti:3000 | xargs kill -9
```

### 3. Проверьте подключение к БД:

```bash
export PATH="/opt/homebrew/opt/postgresql@17/bin:$PATH"
psql -d divehub -c "SELECT COUNT(*) FROM dive_sites;"
```

### 4. Запустите сервер:

```bash
cd backend
npm run start:dev
```

### 5. Проверьте логи:

Если видите ошибки подключения к БД, проверьте:
- PostgreSQL запущен: `brew services list | grep postgresql`
- Пароль в .env правильный
- База данных существует: `psql -l | grep divehub`

### 6. Тестируйте API:

```bash
# Популярные сайты
curl http://localhost:3000/api/v1/dive-sites/popular

# Geo search
curl "http://localhost:3000/api/v1/dive-sites/search?lat=17.5&lng=-87.7&radius=100000"
```

## Типичные ошибки:

1. **Ошибка подключения к БД**: Проверьте настройки в .env
2. **PostGIS не найден**: Убедитесь, что PostgreSQL 17 запущен
3. **Redis недоступен**: Не критично, используется in-memory cache
4. **Порт занят**: Остановите другие серверы на порту 3000
