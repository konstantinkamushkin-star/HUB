#!/bin/bash

# Quick script to create test dive center data
# This script applies migrations and runs the test data creation script

set -e

echo "🚀 Creating test dive center data..."

# Check if database connection variables are set
if [ -z "$DB_HOST" ]; then
    echo "⚠️  DB_HOST not set, using defaults"
    export DB_HOST=${DB_HOST:-localhost}
    export DB_PORT=${DB_PORT:-5432}
    export DB_USERNAME=${DB_USERNAME:-postgres}
    export DB_PASSWORD=${DB_PASSWORD:-postgres}
    export DB_DATABASE=${DB_DATABASE:-divehub}
fi

echo "📋 Database configuration:"
echo "   Host: $DB_HOST"
echo "   Port: $DB_PORT"
echo "   Database: $DB_DATABASE"
echo "   User: $DB_USERNAME"

# Apply migrations
echo ""
echo "📦 Applying migrations..."

if [ -f "migrations/004_create_courses.sql" ]; then
    echo "   Applying courses migration..."
    PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USERNAME -d $DB_DATABASE -f migrations/004_create_courses.sql
else
    echo "   ⚠️  migrations/004_create_courses.sql not found, skipping..."
fi

if [ -f "migrations/005_create_trips.sql" ]; then
    echo "   Applying trips migration..."
    PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USERNAME -d $DB_DATABASE -f migrations/005_create_trips.sql
else
    echo "   ⚠️  migrations/005_create_trips.sql not found, skipping..."
fi

# Run the script
echo ""
echo "🎯 Creating test data..."
node create_test_dive_center.js

echo ""
echo "✅ Done! Test data created successfully."
echo ""
echo "📧 Email: ww@ww.ww"
echo "🔑 Password: 12345678"
