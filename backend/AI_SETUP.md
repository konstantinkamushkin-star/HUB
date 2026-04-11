# Почему не работает AI в редактировании фото

Режим **AI** в приложении вызывает **ваш бэкенд**, а бэкенд — отдельный **Python AI-сервис**. Если что-то из этого не запущено или не настроено, в приложении будет «AI недоступен, локальная обработка».

## Что нужно сделать (по шагам)

### 1. Запустить Python AI-сервис

Из корня проекта:

```bash
cd backend/ai-service
chmod +x start.sh
./start.sh
```

Или вручную:

```bash
cd backend/ai-service
python3 -m venv .venv
source .venv/bin/activate   # Windows: .venv\Scripts\activate
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8000
```

Должно появиться что-то вроде: `Uvicorn running on http://0.0.0.0:8000`.

Проверка в другом терминале:

```bash
curl http://localhost:8000/health
# Ответ: {"status":"ok","service":"underwater-ai"}
```

### 2. Указать URL AI-сервиса в бэкенде DiveHub

В файле **backend/.env** добавьте (или измените):

```
AI_UNDERWATER_SERVICE_URL=http://localhost:8000
```

Если бэкенд уже был запущен — **перезапустите** его (после изменения `.env`).

### 3. Запустить бэкенд DiveHub

```bash
cd backend
npm run start:dev
```

В логах при старте должна быть строка про Underwater AI.

### 4. Приложение должно стучаться в этот бэкенд

В симуляторе по умолчанию используется `http://localhost:3000`. Убедитесь, что бэкенд слушает порт 3000 (или измените `baseURL` в приложении под свой хост/порт).

---

## Итог

| Кто              | Где/как |
|------------------|--------|
| Python AI-сервис | `backend/ai-service` → `./start.sh` или `uvicorn ... --port 8000` |
| Переменная       | В `backend/.env`: `AI_UNDERWATER_SERVICE_URL=http://localhost:8000` |
| Бэкенд NestJS    | `cd backend && npm run start:dev` (после правки .env — перезапуск) |
| Приложение       | Подключается к бэкенду (например, localhost:3000) |

После этого режим **AI** в редактировании фото будет отправлять снимок на бэкенд, а бэкенд — в ваш Python AI-сервис (классический пайплайн или ваша ONNX-модель в `ai-service/models/underwater.onnx`).
