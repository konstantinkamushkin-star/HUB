#!/bin/bash

cd "$(dirname "$0")"

echo "🔍 Проверка окружения..."

# Проверка .env
if [ ! -f .env ]; then
    echo "❌ .env файл не найден!"
    exit 1
fi

# Проверка PostgreSQL
export PATH="/opt/homebrew/opt/postgresql@17/bin:$PATH"
if ! psql -d divehub -c "SELECT 1;" > /dev/null 2>&1; then
    echo "❌ Не могу подключиться к PostgreSQL!"
    echo "Проверьте настройки в .env"
    exit 1
fi
echo "✅ PostgreSQL подключен"

# Проверка Redis
if ! redis-cli ping > /dev/null 2>&1; then
    echo "⚠️  Redis не доступен (будет использован in-memory cache)"
else
    echo "✅ Redis работает"
fi

echo ""
echo "🚀 Запуск сервера..."
echo "   Логи будут показаны ниже. Нажмите Ctrl+C для остановки."
echo ""

npm run start:dev
