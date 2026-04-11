# Инструкция по подключению бэкенда

## ✅ Что было сделано

1. ✅ Обновлен `NetworkService.swift`:
   - Изменен `baseURL` на `http://localhost:3000` (для разработки)
   - Добавлено логирование запросов/ответов в DEBUG режиме
   - Обновлены все endpoints (убрано `/v1`, используется `/api`)
   - Добавлены методы для работы с токенами

2. ✅ Обновлен `AuthenticationService.swift`:
   - Реализованы реальные API вызовы для `signIn` и `signUp`
   - Добавлена обработка токенов (accessToken, refreshToken)
   - Добавлена обработка ошибок

3. ✅ Обновлена модель `User.swift`:
   - Добавлены поля `firstName`, `lastName` (соответствуют бэкенду)
   - `displayName` теперь computed property
   - Добавлена поддержка формата ролей бэкенда (DIVER_BASIC и т.д.)
   - Добавлены CodingKeys для маппинга полей

4. ✅ Обновлен `EditProfileView.swift`:
   - Разделены поля на firstName и lastName

5. ✅ Создан `APITestView.swift`:
   - Тестовый экран для проверки API соединения

## 🚀 Как запустить и протестировать

### Шаг 1: Запустите бэкенд

```bash
cd /Users/admin/Desktop/divehub-backend
npm run start:dev
```

Бэкенд должен запуститься на `http://localhost:3000`

### Шаг 2: Настройте baseURL для симулятора

Если вы используете iOS симулятор, `localhost` не будет работать. Вам нужно:

1. Узнайте IP адрес вашего Mac:
   ```bash
   ifconfig | grep "inet " | grep -v 127.0.0.1
   ```
   Например: `192.168.1.100`

2. Обновите `baseURL` в `NetworkService.swift`:
   ```swift
   private let baseURL = "http://192.168.1.100:3000" // Замените на ваш IP
   ```

### Шаг 3: Настройте Info.plist

Добавьте в `Info.plist` для разрешения HTTP запросов:

```xml
<key>NSAppTransportSecurity</key>
<dict>
    <key>NSAllowsLocalNetworking</key>
    <true/>
    <key>NSExceptionDomains</key>
    <dict>
        <key>localhost</key>
        <dict>
            <key>NSExceptionAllowsInsecureHTTPLoads</key>
            <true/>
        </dict>
    </dict>
</dict>
```

### Шаг 4: Протестируйте соединение

1. Запустите приложение в симуляторе
2. Перейдите в Profile → Settings → API Testing (только в DEBUG режиме)
3. Нажмите "Test Backend Connection"
4. Если видите ✅ - соединение работает!

### Шаг 5: Протестируйте аутентификацию

1. Сначала создайте тестового пользователя через бэкенд или через приложение
2. Попробуйте войти через экран Login
3. Проверьте логи в консоли Xcode - должны быть видны запросы и ответы

## 📝 Формат API

### Регистрация
```
POST /api/auth/register
Body: {
  "email": "user@example.com",
  "password": "password123",
  "firstName": "John",
  "lastName": "Doe",
  "phone": "+1234567890" (optional)
}

Response: {
  "accessToken": "...",
  "refreshToken": "...",
  "user": { ... }
}
```

### Вход
```
POST /api/auth/login
Body: {
  "email": "user@example.com",
  "password": "password123"
}

Response: {
  "accessToken": "...",
  "refreshToken": "...",
  "user": { ... }
}
```

### Вход через Apple
```
POST /api/auth/apple
Body: {
  "idToken": "apple_id_token",
  "email": "user@example.com" (optional),
  "firstName": "John" (optional),
  "lastName": "Doe" (optional)
}

Response: {
  "accessToken": "...",
  "refreshToken": "...",
  "user": { ... }
}
```

### Вход через Google
```
POST /api/auth/google
Body: {
  "idToken": "google_id_token",
  "accessToken": "google_access_token" (optional),
  "email": "user@example.com" (optional),
  "firstName": "John" (optional),
  "lastName": "Doe" (optional)
}

Response: {
  "accessToken": "...",
  "refreshToken": "...",
  "user": { ... }
}
```

## 🔍 Отладка

### Проверьте логи в Xcode
В консоли вы увидите:
- 🌐 Запросы к API
- 📤 Тела запросов
- 📥 Ответы от сервера
- ❌ Ошибки

### Проверьте бэкенд
Убедитесь, что:
- Бэкенд запущен на порту 3000
- PostgreSQL запущен
- База данных создана
- CORS настроен правильно

### Тестирование через curl

```bash
# Тест регистрации
curl -X POST http://localhost:3000/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "test123456",
    "firstName": "Test",
    "lastName": "User"
  }'

# Тест входа
curl -X POST http://localhost:3000/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "test123456"
  }'
```

## ⚠️ Важные замечания

1. **Для симулятора**: Используйте IP адрес Mac, а не localhost
2. **Для физического устройства**: Можно использовать localhost через USB или IP адрес
3. **Токены**: Сохраняются в UserDefaults (для продакшена лучше использовать Keychain)
4. **Безопасность**: В продакшене обязательно используйте HTTPS

## 🐛 Решение проблем

### Ошибка "Network unavailable"
- Проверьте, что бэкенд запущен
- Проверьте правильность baseURL
- Для симулятора используйте IP вместо localhost

### Ошибка 401 (Unauthorized)
- Проверьте правильность email/password
- Убедитесь, что пользователь существует в базе

### Ошибка декодирования
- Проверьте формат ответа бэкенда
- Убедитесь, что модели соответствуют API

## 📚 Дополнительно

- Swagger документация: http://localhost:3000/api/docs
- Health check: http://localhost:3000/api/health
