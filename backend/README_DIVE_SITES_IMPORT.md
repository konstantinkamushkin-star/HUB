# Импорт дайвсайтов из OpenStreetMap и Google Places API

Этот набор скриптов позволяет получить, обработать и импортировать более 2000 реальных дайвсайтов с точными координатами из различных источников.

## 📋 Обзор процесса

1. **Загрузка из OpenStreetMap** - получение дайвсайтов через Overpass API
2. **Загрузка из Google Places API** - дополнение популярными локациями (опционально)
3. **Верификация координат** - проверка и исправление координат
4. **Обогащение данных** - добавление глубины, типа, сложности
5. **Импорт в базу данных** - сохранение в PostgreSQL

## 🚀 Быстрый старт

### Вариант 1: Полный автоматический процесс

```bash
# Запустить все этапы автоматически
node fetch_and_process_dive_sites.js
```

Этот скрипт выполнит все этапы:
- Загрузку из OSM
- Загрузку из Google Places (если установлен API ключ)
- Объединение источников
- Верификацию координат
- Обогащение данных
- Сохранение финального результата в `dive_sites_final.json`

### Вариант 2: Пошаговое выполнение

```bash
# 1. Загрузка из OpenStreetMap
node fetch_dive_sites_osm_enhanced.js

# 2. Загрузка из Google Places API (опционально, требует API ключ)
export GOOGLE_PLACES_API_KEY=your_api_key
node fetch_dive_sites_google_places.js

# 3. Верификация координат
node verify_dive_site_coordinates.js dive_sites_osm_enhanced.json

# 4. Обогащение данных
node enrich_dive_sites.js dive_sites_verified.json

# 5. Импорт в базу данных
node import_processed_dive_sites.js dive_sites_enriched.json
```

## 📝 Детальное описание скриптов

### 1. `fetch_dive_sites_osm_enhanced.js`

Загружает дайвсайты из OpenStreetMap через Overpass API.

**Особенности:**
- Запросы по регионам для надежности
- Автоматический retry при ошибках
- Извлечение дополнительных данных (глубина, сложность, тип)
- Дедупликация

**Использование:**
```bash
node fetch_dive_sites_osm_enhanced.js
```

**Результат:** `dive_sites_osm_enhanced.json`

### 2. `fetch_dive_sites_google_places.js`

Загружает дайвсайты из Google Places API.

**Требования:**
- API ключ Google Places
- Включенный Places API в Google Cloud Console

**Настройка:**
```bash
export GOOGLE_PLACES_API_KEY=your_api_key
```

**Использование:**
```bash
node fetch_dive_sites_google_places.js
```

**Результат:** `dive_sites_google_places.json`

**Получение API ключа:**
1. Перейдите в [Google Cloud Console](https://console.cloud.google.com/)
2. Создайте проект или выберите существующий
3. Включите Places API
4. Создайте API ключ
5. Установите переменную окружения

### 3. `verify_dive_site_coordinates.js`

Верифицирует и исправляет координаты дайвсайтов.

**Функции:**
- Проверка валидности координат
- Обнаружение и исправление перепутанных координат
- Проверка по регионам стран

**Использование:**
```bash
node verify_dive_site_coordinates.js input.json [output.json]
```

**Результат:** Верифицированные координаты с исправлениями

### 4. `enrich_dive_sites.js`

Обогащает дайвсайты дополнительными данными.

**Добавляет:**
- Типы дайвсайтов (reef, wreck, wall, cave, etc.)
- Глубину (min/max) на основе паттернов
- Сложность (1-4) на основе глубины и типа
- Морскую жизнь (если доступно)

**Использование:**
```bash
node enrich_dive_sites.js input.json [output.json]
```

**Результат:** Обогащенные данные с оценками

### 5. `fetch_and_process_dive_sites.js`

Главный скрипт, объединяющий все этапы.

**Использование:**
```bash
# Полный процесс
node fetch_and_process_dive_sites.js

# Пропустить определенные этапы
node fetch_and_process_dive_sites.js --skip-google
node fetch_and_process_dive_sites.js --skip-verify
node fetch_and_process_dive_sites.js --skip-enrich
```

**Опции:**
- `--skip-osm` - пропустить загрузку из OSM
- `--skip-google` - пропустить загрузку из Google Places
- `--skip-verify` - пропустить верификацию
- `--skip-enrich` - пропустить обогащение

**Результат:** `dive_sites_final.json`

### 6. `import_processed_dive_sites.js`

Импортирует обработанные дайвсайты в PostgreSQL.

**Требования:**
- PostgreSQL с PostGIS
- Настроенные переменные окружения для БД

**Настройка БД:**
```bash
export DB_HOST=localhost
export DB_PORT=5432
export DB_DATABASE=divehub
export DB_USERNAME=admin
export DB_PASSWORD=your_password
```

**Использование:**
```bash
# Обычный импорт
node import_processed_dive_sites.js dive_sites_final.json

# Проверка без сохранения
node import_processed_dive_sites.js dive_sites_final.json --dry-run

# Импорт с дубликатами
node import_processed_dive_sites.js dive_sites_final.json --no-skip-duplicates

# Изменить размер батча
node import_processed_dive_sites.js dive_sites_final.json --batch-size=50
```

**Опции:**
- `--dry-run` - проверка без сохранения в БД
- `--no-skip-duplicates` - импортировать дубликаты
- `--batch-size=N` - размер батча (по умолчанию: 100)

## 📊 Ожидаемые результаты

После выполнения всех этапов вы получите:

- **1000-1500+ дайвсайтов** из OpenStreetMap
- **200-500+ дайвсайтов** из Google Places API (если используется)
- **Верифицированные координаты** с исправлениями
- **Обогащенные данные** с глубиной, типом, сложностью
- **Импортированные в БД** дайвсайты

## 🔧 Настройка базы данных

Убедитесь, что база данных настроена правильно:

```sql
-- Проверка PostGIS
SELECT PostGIS_version();

-- Проверка таблицы
SELECT COUNT(*) FROM dive_sites;

-- Проверка индексов
\di dive_sites
```

## 📁 Структура файлов

После выполнения скриптов будут созданы следующие файлы:

```
backend/
├── dive_sites_osm_enhanced.json      # Данные из OSM
├── dive_sites_google_places.json     # Данные из Google Places
├── dive_sites_merged.json            # Объединенные данные
├── dive_sites_verified.json          # Верифицированные координаты
├── dive_sites_enriched.json          # Обогащенные данные
└── dive_sites_final.json             # Финальный результат
```

## ⚠️ Важные замечания

1. **Rate Limiting**: Overpass API имеет ограничения. Скрипты включают задержки между запросами.

2. **Google Places API**: Требует API ключ и может иметь квоты. Бесплатный тариф: $200 кредитов в месяц.

3. **Координаты**: Все координаты проверяются и исправляются автоматически.

4. **Дубликаты**: Скрипты автоматически удаляют дубликаты по координатам и названиям.

5. **Время выполнения**: Полный процесс может занять 30-60 минут в зависимости от скорости интернета.

## 🐛 Решение проблем

### Ошибка подключения к Overpass API
- Попробуйте другой endpoint (скрипт автоматически переключается)
- Подождите несколько минут и повторите

### Ошибка Google Places API
- Проверьте API ключ
- Убедитесь, что Places API включен
- Проверьте квоты в Google Cloud Console

### Ошибка подключения к БД
- Проверьте переменные окружения
- Убедитесь, что PostgreSQL запущен
- Проверьте права доступа пользователя

### Недостаточно дайвсайтов
- Попробуйте запустить скрипты несколько раз
- Используйте Google Places API для дополнения
- Проверьте логи на ошибки

## 📈 Мониторинг прогресса

Все скрипты выводят подробную информацию о прогрессе:
- Количество обработанных дайвсайтов
- Статистика по странам и типам
- Ошибки и предупреждения
- Финальные результаты

## 🔄 Обновление данных

Для обновления данных:

```bash
# Полное обновление
node fetch_and_process_dive_sites.js

# Импорт новых данных (дубликаты будут пропущены)
node import_processed_dive_sites.js dive_sites_final.json
```

## 📞 Поддержка

При возникновении проблем:
1. Проверьте логи скриптов
2. Убедитесь в правильности настроек
3. Проверьте подключение к интернету и БД
4. Попробуйте выполнить этапы по отдельности
