# 🚀 Быстрый старт: Импорт дайвсайтов

## Самый простой способ (рекомендуется)

```bash
# 1. Перейти в директорию backend
cd backend

# 2. Запустить автоматический процесс
node fetch_and_process_dive_sites.js

# 3. Импортировать в базу данных
node import_processed_dive_sites.js dive_sites_final.json
```

Готово! Вы получите 1000-2000+ дайвсайтов в базе данных.

## С Google Places API (для большего количества)

```bash
# 1. Получить API ключ: https://console.cloud.google.com/
# 2. Установить переменную окружения
export GOOGLE_PLACES_API_KEY=your_api_key

# 3. Запустить процесс (Google Places будет включен автоматически)
node fetch_and_process_dive_sites.js

# 4. Импортировать
node import_processed_dive_sites.js dive_sites_final.json
```

## Настройка базы данных

Убедитесь, что переменные окружения установлены:

```bash
export DB_HOST=localhost
export DB_PORT=5432
export DB_DATABASE=divehub
export DB_USERNAME=admin
export DB_PASSWORD=your_password
```

## Проверка результата

```bash
# Подключиться к PostgreSQL
psql -h localhost -U admin -d divehub

# Проверить количество дайвсайтов
SELECT COUNT(*) FROM dive_sites WHERE is_active = true;

# Посмотреть примеры
SELECT name, country, region, latitude, longitude 
FROM dive_sites 
WHERE is_active = true 
LIMIT 10;
```

## Что дальше?

После импорта дайвсайты будут доступны через ваш API:
- `/api/v1/dive-sites/search` - поиск по геолокации
- `/api/v1/dive-sites/map` - для карты
- `/api/dive-sites` - список всех дайвсайтов

## Нужна помощь?

См. подробную документацию: `README_DIVE_SITES_IMPORT.md`
