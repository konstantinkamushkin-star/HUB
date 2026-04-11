#!/bin/bash

echo "🔍 Диагностика сервера..."

# Остановить все процессы
echo "1. Останавливаю все процессы..."
pkill -9 -f "nest start"
pkill -9 -f "node dist/main"
lsof -ti:3000 | xargs kill -9 2>/dev/null || true
sleep 2

# Проверить .env
echo "2. Проверяю .env файл..."
if [ ! -f .env ]; then
    echo "⚠️  .env файл не найден, создаю..."
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
fi

# Проверить PostgreSQL
echo "3. Проверяю PostgreSQL..."
export PATH="/opt/homebrew/opt/postgresql@17/bin:$PATH"
if psql -d divehub -c "SELECT 1;" > /dev/null 2>&1; then
    echo "✅ PostgreSQL подключен"
    psql -d divehub -c "SELECT COUNT(*) FROM dive_sites;"
else
    echo "❌ Ошибка подключения к PostgreSQL"
    exit 1
fi

# Проверить Redis
echo "4. Проверяю Redis..."
if redis-cli ping > /dev/null 2>&1; then
    echo "✅ Redis работает"
else
    echo "⚠️  Redis не доступен (будет использован in-memory cache)"
fi

# Собрать проект
echo "5. Собираю проект..."
npm run build

# Запустить сервер
echo "6. Запускаю сервер..."
echo "   Логи будут показаны ниже. Нажмите Ctrl+C для остановки."
echo ""
npm run start:dev
