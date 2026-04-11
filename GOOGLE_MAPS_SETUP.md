# Настройка Google Maps для DiveHub

## Шаг 1: Добавление Google Maps SDK через Swift Package Manager

1. Откройте проект в Xcode
2. Выберите проект в навигаторе (самый верхний элемент)
3. Выберите таргет `DiveHub`
4. Перейдите на вкладку **Package Dependencies**
5. Нажмите кнопку **+** (Add Package Dependency)
6. Введите URL: `https://github.com/googlemaps/ios-maps-sdk`
7. Выберите версию (рекомендуется последняя стабильная версия)
8. Нажмите **Add Package**
9. Убедитесь, что пакет добавлен к таргету `DiveHub`

## Шаг 2: Получение API ключа Google Maps

1. Перейдите на [Google Cloud Console](https://console.cloud.google.com/)
2. Создайте новый проект или выберите существующий
3. Включите **Maps SDK for iOS**:
   - Перейдите в **APIs & Services** > **Library**
   - Найдите "Maps SDK for iOS"
   - Нажмите **Enable**
4. Создайте API ключ:
   - Перейдите в **APIs & Services** > **Credentials**
   - Нажмите **Create Credentials** > **API Key**
   - Скопируйте созданный ключ
5. Ограничьте ключ (рекомендуется):
   - Нажмите на созданный ключ для редактирования
   - В разделе **Application restrictions** выберите **iOS apps**
   - Добавьте Bundle ID вашего приложения

## Шаг 3: Добавление API ключа в проект

### Вариант 1: Через Info.plist (рекомендуется)

1. Найдите файл `Info.plist` в проекте
2. Добавьте новый ключ:
   - Key: `GoogleMapsAPIKey`
   - Type: `String`
   - Value: ваш API ключ

### Вариант 2: Через код (для разработки)

Если вы хотите временно использовать ключ в коде (не рекомендуется для production):

1. Откройте `DiveHubApp.swift`
2. Найдите строку с комментарием `// GoogleMapsService.shared.initialize(apiKey: "YOUR_API_KEY_HERE")`
3. Раскомментируйте и вставьте ваш ключ

## Шаг 4: Настройка разрешений

Убедитесь, что в `Info.plist` есть следующие разрешения:

```xml
<key>NSLocationWhenInUseUsageDescription</key>
<string>Приложению требуется доступ к вашему местоположению для отображения на карте</string>

<key>NSLocationAlwaysAndWhenInUseUsageDescription</key>
<string>Приложению требуется доступ к вашему местоположению для отображения на карте</string>
```

## Шаг 5: Проверка работы

1. Запустите приложение
2. Перейдите на вкладку "Map" (Карта)
3. Убедитесь, что карта загружается
4. Проверьте, что отображаются маркеры дайв-сайтов и дайв-центров
5. Проверьте работу кнопки геолокации

## Устранение проблем

### Карта не загружается
- Проверьте, что API ключ правильно добавлен в `Info.plist`
- Убедитесь, что Maps SDK for iOS включен в Google Cloud Console
- Проверьте, что Bundle ID совпадает с ограничениями API ключа

### Ошибка "API key not found"
- Убедитесь, что ключ `GoogleMapsAPIKey` добавлен в `Info.plist`
- Проверьте правильность написания ключа (без пробелов, кавычек)

### Маркеры не отображаются
- Проверьте, что данные загружаются с сервера
- Убедитесь, что координаты корректны

## Дополнительная информация

- [Документация Google Maps SDK for iOS](https://developers.google.com/maps/documentation/ios-sdk)
- [Руководство по миграции с MapKit](https://developers.google.com/maps/documentation/ios-sdk/migrate)
