# OpenStreetMap Integration

## Обзор

Приложение теперь использует OpenStreetMap вместо Apple Maps или Google Maps. Это полностью бесплатное решение без необходимости в API ключах.

## Реализация

### OpenStreetMapView.swift

Использует нативный `MKMapView` с кастомным тайловым оверлеем для отображения тайлов OpenStreetMap.

**Особенности:**
- ✅ Полностью бесплатно
- ✅ Не требует API ключей
- ✅ Нативная производительность
- ✅ Поддержка всех стандартных функций MapKit
- ✅ Кастомные маркеры и аннотации

### Tile Overlay

Используется класс `OpenStreetMapTileOverlay`, который:
- Загружает тайлы с официальных серверов OpenStreetMap
- Поддерживает поддомены (a, b, c) для балансировки нагрузки
- Поддерживает зум от 0 до 19

## Использование

```swift
OpenStreetMapView(
    region: $region,
    annotations: $annotations,
    showsUserLocation: $showsUserLocation,
    onAnnotationTapped: { annotation in
        // Handle annotation tap
    }
)
```

## Альтернативные тайл-серверы

Если нужно использовать другие стили карт, можно изменить URL в `OpenStreetMapTileOverlay`:

### CartoDB Positron (светлый стиль)
```swift
super.init(urlTemplate: "https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png")
```

### CartoDB Dark Matter (темный стиль)
```swift
super.init(urlTemplate: "https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png")
```

### Stamen Watercolor (художественный стиль)
```swift
super.init(urlTemplate: "https://stamen-tiles-{s}.a.ssl.fastly.net/watercolor/{z}/{x}/{y}.jpg")
```

### Stamen Terrain (рельеф)
```swift
super.init(urlTemplate: "https://stamen-tiles-{s}.a.ssl.fastly.net/terrain/{z}/{x}/{y}{r}.png")
```

## Ограничения

⚠️ **Важно**: OpenStreetMap имеет правила использования:
- Максимум 2 запроса в секунду на один IP
- Для production рекомендуется использовать собственный тайл-сервер
- Или использовать кэширование тайлов

## Кэширование тайлов

Для улучшения производительности можно добавить кэширование:

```swift
class CachedOpenStreetMapTileOverlay: MKTileOverlay {
    private let cache = NSCache<NSString, NSData>()
    
    override func loadTile(at path: MKTileOverlayPath, result: @escaping (Data?, Error?) -> Void) {
        let cacheKey = "\(path.z)-\(path.x)-\(path.y)" as NSString
        
        if let cachedData = cache.object(forKey: cacheKey) {
            result(cachedData as Data, nil)
            return
        }
        
        let url = self.url(forTilePath: path)
        URLSession.shared.dataTask(with: url) { data, response, error in
            if let data = data {
                self.cache.setObject(data as NSData, forKey: cacheKey)
                result(data, nil)
            } else {
                result(nil, error)
            }
        }.resume()
    }
}
```

## Интеграция с бэкендом

Бэкенд предоставляет API для работы с геолокацией:
- `POST /api/maps/geocode` - геокодирование адреса
- `POST /api/maps/reverse-geocode` - обратное геокодирование
- `GET /api/maps/search-radius` - поиск дайв-сайтов в радиусе
- `GET /api/maps/distance` - расчет расстояния

Все эндпоинты используют OpenStreetMap (Nominatim) для геокодирования.

## Полезные ссылки

- [OpenStreetMap](https://www.openstreetmap.org/)
- [OpenStreetMap Tile Usage Policy](https://operations.osmfoundation.org/policies/tiles/)
- [MapKit Documentation](https://developer.apple.com/documentation/mapkit)
- [Nominatim API](https://nominatim.org/release-docs/develop/api/Overview/)
