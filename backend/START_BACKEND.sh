#!/bin/bash

# Start DiveHub Backend
cd "$(dirname "$0")"

echo "🚀 Starting DiveHub Backend..."

# Add PostgreSQL to PATH
export PATH="/opt/homebrew/opt/postgresql@17/bin:$PATH"

# Check if PostgreSQL is running
if ! pg_isready -h localhost -p 5432 > /dev/null 2>&1; then
    echo "⚠️  PostgreSQL is not running. Starting..."
    brew services start postgresql@17
    sleep 3
fi

# Check if Redis is running
if ! redis-cli ping > /dev/null 2>&1; then
    echo "⚠️  Redis is not running. Starting..."
    brew services start redis
    sleep 2
fi

# Start backend
npm run start:dev
