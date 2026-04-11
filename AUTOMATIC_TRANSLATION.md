# Автоматический перевод контента

## Обзор

Реализована система автоматического перевода всех текстовых полей (кроме полей с названиями) на язык пользователя.

## Что было сделано

### 1. TranslationService
Создан новый сервис `TranslationService.swift` для автоматического перевода текста:
- Поддержка перевода одиночных текстов и пакетного перевода
- Кэширование переводов для оптимизации
- Автоматическое определение языка пользователя
- Интеграция с бэкенд API для перевода

### 2. Обновлены модели данных

Добавлена поддержка переводов в следующие модели:
- **Trip**: `localizedDescription` для описания поездки, переводы для `TripProgramDay.description`, `ProgramActivity.activity` и `notes`, `AdditionalExpense.description`
- **DiveCenter**: `localizedDescription` для описания центра
- **Course**: `localizedDescription` для описания курса, переводы для `CourseModule.description`
- **Hotel**: `localizedDescription` для описания отеля, переводы для `RoomType.description`
- **Yacht**: `localizedDescription` для описания яхты, переводы для `CabinType.description`
- **Service**: `localizedDescription` для описания услуги
- **Instructor**: `localizedBio` и `localizedDescription` для биографии и описания инструктора

### 3. Computed Properties

Добавлены computed properties для автоматического получения переведенного контента:
- `displayDescription` - возвращает переведенное описание или оригинал, если перевод недоступен
- `displayActivity`, `displayNotes`, `displayBio` - аналогично для других полей

Эти свойства автоматически проверяют язык пользователя и возвращают соответствующий перевод.

### 4. Обновлены представления (Views)

Все представления обновлены для использования переведенного контента:
- `TripsListView` - использует `trip.displayDescription`
- `DiveCenterDetailView` - использует `center.displayDescription` и `service.displayDescription`
- `CourseRowView` и `CourseDetailView` - используют `course.displayDescription` и `module.displayDescription`
- `DiveSiteDetailView` - уже использует `site.displayDescription` (было реализовано ранее)

### 5. NetworkService

Обновлен `NetworkService`:
- Добавлены методы `translateText()` и `translateTextBatch()` для работы с API перевода
- Автоматическое добавление заголовка `Accept-Language` во все запросы для указания языка пользователя
- Бэкенд может использовать этот заголовок для автоматического перевода контента

## Как это работает

1. **При загрузке данных**: Бэкенд получает заголовок `Accept-Language` с языком пользователя и может автоматически переводить контент перед отправкой.

2. **При отображении**: Views используют computed properties (например, `displayDescription`), которые:
   - Проверяют наличие перевода для текущего языка пользователя
   - Если перевод есть - возвращают его
   - Если перевода нет - возвращают оригинальный текст

3. **Автоматический перевод**: Если бэкенд не предоставил перевод, можно использовать `TranslationService` для перевода на лету (требует настройки на бэкенде).

## Что нужно сделать на бэкенде

### 1. API для перевода

Реализовать следующие endpoints:

```
POST /api/translate
Body: {
  "text": "Text to translate",
  "sourceLanguage": "en",
  "targetLanguage": "ru"
}
Response: {
  "translatedText": "Переведенный текст",
  "sourceLanguage": "en",
  "targetLanguage": "ru"
}

POST /api/translate/batch
Body: {
  "texts": ["Text 1", "Text 2"],
  "sourceLanguage": "en",
  "targetLanguage": "ru"
}
Response: {
  "translatedTexts": ["Текст 1", "Текст 2"],
  "sourceLanguage": "en",
  "targetLanguage": "ru"
}
```

### 2. Автоматический перевод при загрузке данных

При получении запросов с заголовком `Accept-Language`:
1. Определить язык пользователя из заголовка
2. Если контент не на этом языке, автоматически перевести его
3. Вернуть переведенный контент в поле `localizedDescription[language]` или в основном поле, если это единственный язык

### 3. Хранение переводов

Рекомендуется хранить переводы в базе данных:
- Для каждого текстового поля хранить оригинал и переводы в формате `{ "en": "...", "ru": "...", "es": "..." }`
- При создании/обновлении контента автоматически переводить на все поддерживаемые языки
- Кэшировать переводы для оптимизации

### 4. Интеграция с сервисом перевода

Использовать один из следующих сервисов:
- **Google Cloud Translation API** (платный, высокое качество)
- **DeepL API** (платный, отличное качество)
- **LibreTranslate** (бесплатный, open-source, можно развернуть самостоятельно)
- **Azure Translator** (платный, хорошее качество)

## Примеры использования

### На фронтенде

```swift
// Автоматически получает переведенное описание
Text(trip.displayDescription)

// Автоматически переводит текст
let translated = await TranslationService.shared.translateToUserLanguage(
    text: "Hello",
    from: "en"
)
```

### На бэкенде (пример для Node.js)

```javascript
// Middleware для автоматического перевода
app.use((req, res, next) => {
  const userLanguage = req.headers['accept-language'] || 'en';
  req.userLanguage = userLanguage;
  next();
});

// При загрузке поездок
app.get('/api/trips', async (req, res) => {
  const trips = await getTrips();
  const translatedTrips = await Promise.all(
    trips.map(trip => translateTrip(trip, req.userLanguage))
  );
  res.json(translatedTrips);
});

async function translateTrip(trip, targetLanguage) {
  if (trip.localizedDescription?.[targetLanguage]) {
    return trip; // Перевод уже есть
  }
  
  // Автоматически перевести
  const translated = await translateService.translate(
    trip.description,
    'en', // исходный язык
    targetLanguage
  );
  
  trip.localizedDescription = trip.localizedDescription || {};
  trip.localizedDescription[targetLanguage] = translated;
  
  return trip;
}
```

## Поддерживаемые языки

- English (en)
- Russian (ru)
- Spanish (es)
- German (de)
- French (fr)
- Chinese (zh)

## Примечания

- Поля с названиями (name) НЕ переводятся автоматически, как и было запрошено
- Переводы кэшируются на фронтенде для оптимизации
- Если перевод недоступен, отображается оригинальный текст
- Бэкенд должен реализовать логику автоматического перевода при создании/обновлении контента
