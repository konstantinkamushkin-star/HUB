# Create 10 Shops Script

This script creates 10 fully configured shops with users (logins and passwords) in the database.

## Usage

```bash
cd backend
node create_10_shops.js
```

## Prerequisites

1. Database must be running and accessible
2. Migration `006_create_shops.sql` must be applied
3. Environment variables (or .env file):
   - `DB_HOST` (default: localhost)
   - `DB_PORT` (default: 5432)
   - `DB_USERNAME` (default: admin)
   - `DB_PASSWORD` (default: empty)
   - `DB_DATABASE` (default: divehub)

## What it creates

The script creates 10 shops with:
- Full shop information (name, description, location, contact info)
- Localized names and descriptions (English and Russian)
- Multiple brands per shop
- Photos
- Owner users with login credentials

## Shop List

1. **Deep Blue Diving Equipment** (Sharm El Sheikh, Egypt)
   - Email: shop1@deepblue.com
   - Password: Shop1@2024

2. **Coral Reef Gear Online** (Dubai, UAE) - Online shop
   - Email: shop2@coralreef.com
   - Password: Shop2@2024

3. **Tropical Diving Supplies** (Colombo, Sri Lanka)
   - Email: shop3@tropical.com
   - Password: Shop3@2024

4. **Ocean Pro Equipment** (Bali, Indonesia)
   - Email: shop4@oceanpro.com
   - Password: Shop4@2024

5. **Dive Tech Global** (Singapore) - Online shop
   - Email: shop5@divetech.com
   - Password: Shop5@2024

6. **Blue Water Diving Store** (Cebu, Philippines)
   - Email: shop6@bluewater.com
   - Password: Shop6@2024

7. **Reef Masters Equipment** (Darwin, Australia)
   - Email: shop7@reefmasters.com
   - Password: Shop7@2024

8. **Aqua World Online** (Mexico City, Mexico) - Online shop
   - Email: shop8@aquaworld.com
   - Password: Shop8@2024

9. **Pacific Diving Gear** (Honolulu, Hawaii, USA)
   - Email: shop9@pacific.com
   - Password: Shop9@2024

10. **Mediterranean Dive Shop** (Santorini, Greece)
    - Email: shop10@mediterranean.com
    - Password: Shop10@2024

## Notes

- If a shop or user already exists, the script will update the existing record
- All shops are set as active
- All users have the role `DIVE_CENTER_ADMIN`
- Each shop has multiple brands, photos, and complete contact information
