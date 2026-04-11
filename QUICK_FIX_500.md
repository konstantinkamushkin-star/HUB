# 🔧 Быстрое исправление ошибки 500

## Проблема
Сервер возвращает ошибку 500 или не запускается.

## Решение (пошагово):

### 1. Остановите все процессы:

```bash
pkill -9 -f "nest start"
pkill -9 -f "node dist/main"
lsof -ti:3000 | xargs kill -9
```

### 2. Создайте/проверьте .env файл:

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

### 3. Проверьте подключение к БД:

```bash
export PATH="/opt/homebrew/opt/postgresql@17/bin:$PATH"
psql -d divehub -c "SELECT COUNT(*) FROM dive_sites;"
```

Должно вернуть: `count = 2`

### 4. Запустите сервер с диагностикой:

```bash
cd backend
./DEBUG_SERVER.sh
```

Или вручную:

```bash
cd backend
npm run build
npm run start:dev
```

### 5. В другом терминале протестируйте:

```bash
# Подождите 10-15 секунд после запуска сервера
curl http://localhost:3000/api/v1/dive-sites/popular
```

## Если все еще ошибка 500:

1. **Проверьте логи** в консоли, где запущен сервер
2. **Проверьте подключение к БД** - возможно, неправильный пароль
3. **Проверьте, что PostgreSQL 17 запущен**: `brew services list | grep postgresql`

## Типичные ошибки:

- **"Cannot connect to database"** → Проверьте настройки в .env
- **"PostGIS extension not found"** → Убедитесь, что PostgreSQL 17 запущен
- **"Port 3000 already in use"** → Остановите другие серверы

## Альтернативный способ запуска:

Если `npm run start:dev` не работает, попробуйте:

```bash
cd backend
npm run build
node dist/main.js
```

Это запустит сервер без watch mode и покажет все ошибки в консоли.
