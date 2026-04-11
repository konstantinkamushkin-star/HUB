# Миграция с Google Maps на Apple MapKit

## Что было изменено

### ✅ Заменено:
- `GoogleMapView` → `MapKitView` (использует нативный Apple MapKit)
- Удалена зависимость от Google Maps SDK
- Удалена инициализация Google Maps API ключа

### 📁 Новые файлы:
- `DiveHub/Views/Map/MapKitView.swift` - новая реализация карты на MapKit
- `DiveHub/Models/MapRegion.swift` - общая модель для работы с регионами карты

### 🔄 Обновленные файлы:
- `DiveHub/Views/Explore/ExploreTabView.swift` - использует `MapKitView` вместо `GoogleMapView`
- `DiveHub/Views/Map/MapTabView.swift` - использует `MapKitView` вместо `GoogleMapView`
- `DiveHub/DiveHubApp.swift` - удалена инициализация Google Maps

### 📝 Устаревшие файлы (можно удалить):
- `DiveHub/Views/Map/GoogleMapView.swift` - больше не используется
- `DiveHub/Services/GoogleMapsService.swift` - больше не нужен
- `GOOGLE_MAPS_SETUP.md` - устаревшая документация

## Преимущества MapKit

1. **Бесплатно** - нет необходимости в API ключах
2. **Нативный** - встроен в iOS, не требует дополнительных зависимостей
3. **Производительность** - оптимизирован для iOS
4. **Приватность** - данные не передаются третьим лицам

## Что нужно сделать

1. **Удалить Google Maps из проекта:**
   - В Xcode: Project Settings → Package Dependencies
   - Удалите пакет GoogleMaps

2. **Удалить API ключ из Info.plist:**
   - Удалите ключ `GoogleMapsAPIKey` (если был добавлен)

3. **Удалить устаревшие файлы** (опционально):
   - `GoogleMapView.swift`
   - `GoogleMapsService.swift`
   - `GOOGLE_MAPS_SETUP.md`

4. **Проверить работу:**
   - Запустите приложение
   - Проверьте отображение карты
   - Проверьте работу маркеров и аннотаций

## API совместимость

`MapKitView` имеет тот же интерфейс, что и `GoogleMapView`, поэтому замена была простой:
- Те же параметры: `region`, `annotations`, `showsUserLocation`
- Те же колбэки: `onAnnotationTapped`, `onMapTapped`
- Та же модель: `MapRegion`

## Примечания

- MapKit использует Apple Maps, которые могут отличаться от Google Maps по внешнему виду
- Для офлайн-карт можно использовать MapKit с предзагруженными регионами
- MapKit поддерживает кастомные тайлы, если нужно использовать OpenStreetMap
