#!/usr/bin/env bash
# Локальный стек для теста нейросети из приложения:
#   1) Python ai-service (ONNX) на порту 8010
#   2) NestJS с AI_UNDERWATER_SERVICE_URL → прокси /api/v1/underwater-ai/process
#
# Приложение (симулятор): Profile → Настройки → базовый URL http://localhost:3000
# На iPhone: тот же экран → http://<IP_вашего_Mac>:3000
#
# Остановка: Ctrl+C (скрипт завершит и Python).
set -euo pipefail
BACKEND="$(cd "$(dirname "$0")" && pwd)"
cd "$BACKEND"

ensure_env() {
  if [[ ! -f .env ]]; then
    if [[ -f .env.example ]]; then
      cp .env.example .env
      echo "Создан $BACKEND/.env из .env.example — при необходимости допишите БД/Redis."
    else
      echo 'AI_UNDERWATER_SERVICE_URL=http://127.0.0.1:8010' > .env
      echo "Создан минимальный $BACKEND/.env"
    fi
  fi
  if ! grep -qE '^[[:space:]]*AI_UNDERWATER_SERVICE_URL=' .env 2>/dev/null; then
    echo '' >> .env
    echo 'AI_UNDERWATER_SERVICE_URL=http://127.0.0.1:8010' >> .env
    echo "Добавлен AI_UNDERWATER_SERVICE_URL в .env"
  fi
}

ensure_env

AI_PORT="${AI_SERVICE_PORT:-8010}"
AISVC="$BACKEND/ai-service"

if [[ ! -d "$AISVC/.venv" ]]; then
  echo "Создаю venv в ai-service..."
  python3 -m venv "$AISVC/.venv"
fi
# shellcheck source=/dev/null
source "$AISVC/.venv/bin/activate"
pip install -q -U -r "$AISVC/requirements.txt"

echo "▶ Запуск ai-service на порту $AI_PORT ..."
(cd "$AISVC" && exec uvicorn main:app --host 0.0.0.0 --port "$AI_PORT") &
AI_PID=$!

cleanup() {
  if kill -0 "$AI_PID" 2>/dev/null; then
    kill "$AI_PID" 2>/dev/null || true
    wait "$AI_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT INT TERM

sleep 1
if ! curl -sf "http://127.0.0.1:$AI_PORT/health" >/dev/null; then
  echo "⚠️  ai-service не ответил на /health. Проверьте порт (занят? → AI_SERVICE_PORT=8011 $0)"
  exit 1
fi
echo "✓ ai-service OK (http://127.0.0.1:$AI_PORT)"

if ! command -v npm >/dev/null 2>&1; then
  echo "npm не найден — Nest не запущен. Оставляю только ai-service; для приложения укажите прямой URL в настройках или запустите Nest вручную."
  wait "$AI_PID"
  exit 0
fi

echo "▶ Запуск NestJS (npm run start:dev) — нужны рабочие .env (БД и т.д.)"
npm run start:dev
