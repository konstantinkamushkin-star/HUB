# Создание тестового дайвцентра

Этот скрипт создает тестовый дайвцентр с пользователем, курсами и поездками.

## Требования

1. PostgreSQL с PostGIS
2. База данных `divehub` должна существовать
3. Применены миграции для таблиц `users`, `dive_centers`, `courses`, `trips`

## Установка зависимостей

```bash
cd backend
npm install
```

## Применение миграций

Перед запуском скрипта необходимо применить миграции:

```bash
# Применить миграцию для курсов
psql -d divehub -f migrations/004_create_courses.sql

# Применить миграцию для поездок
psql -d divehub -f migrations/005_create_trips.sql
```

Или используйте скрипт apply-migration.js:

```bash
node apply-migration.js migrations/004_create_courses.sql
node apply-migration.js migrations/005_create_trips.sql
```

## Настройка переменных окружения

Убедитесь, что переменные окружения настроены правильно:

```bash
export DB_HOST=localhost
export DB_PORT=5432
export DB_USERNAME=postgres
export DB_PASSWORD=postgres
export DB_DATABASE=divehub
```

Или создайте файл `.env` в директории `backend/`:

```env
DB_HOST=localhost
DB_PORT=5432
DB_USERNAME=postgres
DB_PASSWORD=postgres
DB_DATABASE=divehub
```

## Запуск скрипта

```bash
node create_test_dive_center.js
```

## Что создается

### Пользователь
- **Email**: `ww@ww.ww`
- **Пароль**: `12345678`
- **Роль**: `DIVE_CENTER_ADMIN`
- **Имя**: Test Dive Center

### Дайвцентр
- **Название**: Test Dive Center
- **Местоположение**: Sharm El Sheikh, Egypt
- **Сертификация**: PADI
- **Языки**: English, Russian
- **Фотографии**: 5 фотографий

### Курсы (10 штук)
1. Open Water Diver (basic)
2. Advanced Open Water (advanced)
3. Rescue Diver (advanced)
4. Divemaster (professional)
5. Enriched Air (Nitrox) (specialization)
6. Deep Diver (specialization)
7. Wreck Diver (specialization)
8. Night Diver (specialization)
9. Underwater Photography (specialization)
10. Peak Performance Buoyancy (specialization)

Все курсы содержат:
- Описание
- Модули программы
- Фотографии
- Системы обучения (PADI, SSI)

### Поездки (10 штук)
- 5 сафари-поездок (liveaboard)
- 5 дневных поездок
- Разные страны: Egypt, Maldives, Indonesia, Philippines, Thailand
- Все поездки содержат:
  - Описание
  - Программу дней
  - Детали цен
  - Дополнительные расходы
  - Фотографии
  - Доступные курсы

## Проверка результата

После выполнения скрипта вы можете проверить созданные данные:

```sql
-- Проверить пользователя
SELECT * FROM users WHERE email = 'ww@ww.ww';

-- Проверить дайвцентр
SELECT * FROM dive_centers WHERE email = 'ww@ww.ww';

-- Проверить курсы
SELECT COUNT(*) FROM courses WHERE dive_center_id = (SELECT id FROM dive_centers WHERE email = 'ww@ww.ww');

-- Проверить поездки
SELECT COUNT(*) FROM trips WHERE organizer_id = (SELECT id FROM dive_centers WHERE email = 'ww@ww.ww');
```

## Удаление тестовых данных

Если нужно удалить созданные тестовые данные:

```sql
-- Удалить поездки
DELETE FROM trips WHERE organizer_id = (SELECT id FROM dive_centers WHERE email = 'ww@ww.ww');

-- Удалить курсы
DELETE FROM courses WHERE dive_center_id = (SELECT id FROM dive_centers WHERE email = 'ww@ww.ww');

-- Удалить дайвцентр
DELETE FROM dive_centers WHERE email = 'ww@ww.ww';

-- Удалить пользователя
DELETE FROM users WHERE email = 'ww@ww.ww';
```

## Примечания

- Скрипт идемпотентен: если пользователь или дайвцентр уже существуют, они будут использованы повторно
- Все фотографии используют placeholder URLs от Unsplash
- Даты поездок генерируются автоматически (начиная с 2 недель от текущей даты)
