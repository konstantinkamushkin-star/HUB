# Финальное исправление проблемы подключения

## Проблема
iOS приложение получает ошибку "Could not connect to the server" - backend сервер не запущен или не слушает порт 3000.

## Решение

### 1. Запустите backend сервер вручную:

```bash
cd /Users/admin/Desktop/appp/DivePROD/backend
./run-server.sh
```

Или вручную:
```bash
cd /Users/admin/Desktop/appp/DivePROD/backend
npm run start:dev
```

### 2. Дождитесь сообщения:

```
🚀 DiveHub Backend is running on: http://localhost:3000
```

### 3. В другом терминале проверьте:

```bash
curl http://localhost:3000/api/dive-sites?limit=10
```

Должен вернуть JSON с дайвсайтами.

### 4. Запустите iOS приложение

После того, как сервер запущен и отвечает на запросы, запустите iOS приложение.

## Что было исправлено:

1. ✅ Исправлена настройка БД (пользователь изменен с `postgres` на `admin`)
2. ✅ Добавлен legacy endpoint `/api/dive-sites` для обратной совместимости
3. ✅ Добавлена обработка ошибок в main.ts
4. ✅ Создан скрипт `run-server.sh` для удобного запуска

## Если сервер не запускается:

Проверьте логи в консоли. Типичные ошибки:

1. **"role postgres does not exist"** - уже исправлено, используйте `DB_USERNAME=admin`
2. **"Unable to connect to the database"** - проверьте, что PostgreSQL запущен:
   ```bash
   brew services list | grep postgresql
   ```
3. **"Redis not available"** - не критично, будет использован in-memory cache

## После успешного запуска:

1. Оставьте сервер запущенным в терминале
2. Запустите iOS приложение
3. Проверьте, что данные загружаются
