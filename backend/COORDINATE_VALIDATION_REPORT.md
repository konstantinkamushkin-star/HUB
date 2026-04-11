# Отчет о валидации координат

## ✅ Результаты проверки

**Дата проверки:** 2026-01-16
**Всего дайвсайтов:** 712
**Статус:** ✅ ВСЕ КООРДИНАТЫ ВАЛИДНЫ

### Проверки выполнены:

1. ✅ **Базовая валидность**
   - Все координаты в диапазоне: latitude [-90, 90], longitude [-180, 180]
   - Нет NULL значений
   - Нет NaN значений

2. ✅ **Соответствие PostGIS location**
   - Все latitude/longitude колонки соответствуют location::geometry
   - Разница < 0.0001 градуса (точность ~10 метров)

3. ✅ **API возвращает правильно**
   - API endpoint `/api/v1/dive-sites/search` возвращает координаты в правильном формате
   - Формат: `{"latitude": 27.7333, "longitude": 34.2833}`

4. ✅ **Примеры валидных координат:**
   - Egypt (Red Sea): lat=27.7333, lng=34.2833 ✅
   - Maldives: lat=3.8667, lng=73.3667 ✅
   - Indonesia: lat=-8.7167, lng=115.5167 ✅
   - Philippines: lat=11.3333, lng=124.0667 ✅
   - Thailand: lat=9.3667, lng=98.0167 ✅

## 🔍 Если координаты не отображаются правильно на карте

### Возможные причины:

1. **Проблема в iOS приложении:**
   - Проверьте логи в Xcode Console
   - Убедитесь, что приложение использует последнюю версию кода
   - Перезапустите приложение после изменений

2. **Проблема с декодированием:**
   - API возвращает: `{"latitude": 27.7333, "longitude": 34.2833}`
   - Swift должен декодировать: `latitude` → `location.latitude`, `longitude` → `location.longitude`
   - CLLocationCoordinate2D создается как: `CLLocationCoordinate2D(latitude: lat, longitude: lng)`

3. **Проблема с начальной позицией карты:**
   - Начальная позиция: `CLLocationCoordinate2D(latitude: 20.0, longitude: -80.0)` (Карибское море)
   - Если дайвсайты в другом регионе, карта может не показывать их сразу

### Как проверить:

1. **Проверить API напрямую:**
   ```bash
   curl "http://localhost:3000/api/v1/dive-sites/search?lat=27.5&lng=34.0&radius=100000&limit=5"
   ```

2. **Проверить координаты в БД:**
   ```sql
   SELECT name, latitude, longitude, country 
   FROM dive_sites 
   WHERE is_active = true 
   LIMIT 10;
   ```

3. **Проверить логи в Xcode:**
   - Откройте Xcode Console
   - Ищите сообщения с "COORD" или "coordinate"
   - Проверьте, что координаты декодируются правильно

## 🛠️ Скрипты для проверки

- `validate_all_coordinates.js` - полная проверка всех координат
- `fix_coordinates_in_db.js` - исправление перепутанных координат
- `test_api_coordinates.js` - тестирование API

## 📝 Структура координат в БД

```sql
-- PostGIS location (основной источник)
location GEOGRAPHY(POINT, 4326) NOT NULL

-- Вычисляемые колонки (автоматически из location)
latitude DOUBLE PRECISION GENERATED ALWAYS AS (ST_Y(location::geometry)) STORED
longitude DOUBLE PRECISION GENERATED ALWAYS AS (ST_X(location::geometry)) STORED
```

**Важно:** 
- PostGIS использует формат (longitude, latitude) для POINT
- ST_X возвращает longitude (первая координата)
- ST_Y возвращает latitude (вторая координата)

## ✅ Заключение

Все координаты в базе данных валидны и корректны. Если проблема с отображением на карте сохраняется, проверьте:

1. Логи iOS приложения
2. Ответы API
3. Начальную позицию карты
4. Правильность декодирования в Swift
