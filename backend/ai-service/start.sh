#!/usr/bin/env bash
# Запуск AI-сервиса (ONNX) для DiveHub. Должен совпадать с AI_UNDERWATER_SERVICE_URL в backend/.env (порт по умолчанию 8010).
set -euo pipefail
cd "$(dirname "$0")"
if [ ! -d ".venv" ]; then
  echo "Создаю виртуальное окружение..."
  python3 -m venv .venv
fi
# shellcheck source=/dev/null
source .venv/bin/activate
pip install -q -U -r requirements.txt
PORT="${PORT:-8010}"
echo "AI-сервис: http://127.0.0.1:$PORT  (модели: models/*.onnx)"
echo "Проверка: curl -s http://127.0.0.1:$PORT/health"
exec uvicorn main:app --host 0.0.0.0 --port "$PORT"
