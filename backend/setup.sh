#!/bin/bash

# Setup script for DiveHub Backend
set -e

echo "🚀 Setting up DiveHub Backend..."

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Check if Homebrew is installed
if ! command -v brew &> /dev/null; then
    echo -e "${RED}❌ Homebrew not found. Please install Homebrew first:${NC}"
    echo "Visit: https://brew.sh"
    exit 1
fi

echo -e "${GREEN}✅ Homebrew found${NC}"

# Install PostgreSQL
if ! brew list postgresql@15 &> /dev/null; then
    echo -e "${YELLOW}📦 Installing PostgreSQL 15...${NC}"
    brew install postgresql@15
else
    echo -e "${GREEN}✅ PostgreSQL 15 already installed${NC}"
fi

# Install PostGIS
if ! brew list postgis &> /dev/null; then
    echo -e "${YELLOW}📦 Installing PostGIS...${NC}"
    brew install postgis
else
    echo -e "${GREEN}✅ PostGIS already installed${NC}"
fi

# Install Redis
if ! brew list redis &> /dev/null; then
    echo -e "${YELLOW}📦 Installing Redis...${NC}"
    brew install redis
else
    echo -e "${GREEN}✅ Redis already installed${NC}"
fi

# Start PostgreSQL
echo -e "${YELLOW}🔄 Starting PostgreSQL...${NC}"
brew services start postgresql@15 || true
sleep 2

# Start Redis
echo -e "${YELLOW}🔄 Starting Redis...${NC}"
brew services start redis || true
sleep 2

# Check if database exists
echo -e "${YELLOW}🗄️  Setting up database...${NC}"

# Add PostgreSQL to PATH
export PATH="/opt/homebrew/opt/postgresql@15/bin:$PATH"

# Create database if it doesn't exist
if psql -lqt | cut -d \| -f 1 | grep -qw divehub; then
    echo -e "${GREEN}✅ Database 'divehub' already exists${NC}"
else
    echo -e "${YELLOW}📦 Creating database 'divehub'...${NC}"
    createdb divehub || echo -e "${YELLOW}⚠️  Database creation may have failed (might already exist)${NC}"
fi

# Enable PostGIS extension
echo -e "${YELLOW}📦 Enabling PostGIS extension...${NC}"
psql -d divehub -c "CREATE EXTENSION IF NOT EXISTS postgis;" 2>&1 || echo -e "${YELLOW}⚠️  PostGIS extension setup (may already exist)${NC}"

# Apply migration
if [ -f "../backend_examples/migrations/001_create_dive_sites.sql" ]; then
    echo -e "${YELLOW}📦 Applying database migration...${NC}"
    psql -d divehub -f ../backend_examples/migrations/001_create_dive_sites.sql 2>&1 || echo -e "${YELLOW}⚠️  Migration may have errors (tables might already exist)${NC}"
else
    echo -e "${YELLOW}⚠️  Migration file not found, skipping...${NC}"
fi

# Check Redis
if redis-cli ping &> /dev/null; then
    echo -e "${GREEN}✅ Redis is running${NC}"
else
    echo -e "${YELLOW}⚠️  Redis is not running. Start it with: brew services start redis${NC}"
fi

echo -e "${GREEN}✅ Setup complete!${NC}"
echo ""
echo "Next steps:"
echo "1. Review .env file and update database password if needed"
echo "2. Run: npm run start:dev"
echo "3. Test API: curl http://localhost:3000/api/v1/dive-sites/popular"
