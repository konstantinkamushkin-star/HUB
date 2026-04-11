# Инструкция по запуску сервера

## Проблема
Backend сервер не запускается автоматически. Нужно запустить его вручную, чтобы увидеть ошибки.

## Решение

### 1. Откройте терминал и перейдите в папку backend:

```bash
cd /Users/admin/Desktop/appp/DivePROD/backend
```

### 2. Убедитесь, что .env файл настроен правильно:

```bash
cat .env
```

Должно быть:
```
DB_USERNAME=admin
DB_PASSWORD=
```

### 3. Запустите сервер:

```bash
npm run start:dev
```

### 4. Дождитесь сообщения:

```
🚀 DiveHub Backend is running on: http://localhost:3000
```

### 5. В другом терминале протестируйте:

```bash
curl http://localhost:3000/api/dive-sites?limit=10
```

## Если видите ошибки:

1. **"role postgres does not exist"** - уже исправлено, используйте `DB_USERNAME=admin`
2. **"Unable to connect to the database"** - проверьте, что PostgreSQL запущен:
   ```bash
   brew services list | grep postgresql
   ```
3. **"Redis not available"** - не критично, будет использован in-memory cache

## После успешного запуска:

1. Оставьте сервер запущенным
2. Запустите iOS приложение
3. Проверьте, что данные загружаются
