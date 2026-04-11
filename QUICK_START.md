# 🚀 Быстрый старт - Подключение к бэкенду

## Шаг 1: Запустите бэкенд

```bash
cd /Users/admin/Desktop/divehub-backend
npm run start:dev
```

Дождитесь сообщения: `🚀 DIVEHUB BACKEND УСПЕШНО ЗАПУЩЕН!`

## Шаг 2: Настройте iOS приложение

### Для iOS Симулятора:

1. Узнайте IP адрес вашего Mac:
   ```bash
   ifconfig | grep "inet " | grep -v 127.0.0.1 | head -1 | awk '{print $2}'
   ```

2. Откройте `NetworkService.swift` и замените:
   ```swift
   private let baseURL = "http://localhost:3000"
   ```
   на:
   ```swift
   private let baseURL = "http://ВАШ_IP:3000"  // Например: http://192.168.1.100:3000
   ```

### Для физического устройства:

Можно использовать `localhost:3000` или IP адрес Mac.

## Шаг 3: Запустите приложение

1. Откройте проект в Xcode
2. Запустите на симуляторе или устройстве
3. Попробуйте зарегистрироваться или войти

## Шаг 4: Проверьте соединение

В приложении:
- Profile → Settings → API Testing (только в DEBUG)
- Нажмите "Test Backend Connection"

Или через терминал:
```bash
curl http://localhost:3000/api
```

## ✅ Готово!

Теперь приложение подключено к бэкенду. Все запросы будут идти на ваш локальный сервер.

## 📝 Что было изменено:

- ✅ `NetworkService.swift` - обновлен baseURL и endpoints
- ✅ `AuthenticationService.swift` - реальные API вызовы
- ✅ `User.swift` - адаптирован под формат бэкенда
- ✅ Добавлено логирование запросов/ответов
- ✅ Создан тестовый экран API Testing

## 🔍 Отладка

Смотрите логи в консоли Xcode:
- 🌐 - Запросы
- 📤 - Тела запросов  
- 📥 - Ответы
- ❌ - Ошибки
