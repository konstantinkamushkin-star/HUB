#!/usr/bin/env bash
# Полная пересборка venv из pyproject.toml + uv.lock (пины, в т.ч. opencv-python-headless).
# Запуск из каталога underwater-vision-module:
#   chmod +x bootstrap-venv.sh && ./bootstrap-venv.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

if ! command -v uv >/dev/null 2>&1; then
  echo "Установите uv: https://docs.astral.sh/uv/getting-started/installation/"
  exit 1
fi

if [[ ! -f uv.lock ]]; then
  echo "Нет uv.lock — выполните: uv lock"
  exit 1
fi

echo ">>> Удаляю старый .venv"
rm -rf .venv

echo ">>> uv venv + uv sync --frozen (без кэша пакетов)"
UV_NO_CACHE=1 uv venv .venv --python 3.11
UV_NO_CACHE=1 UV_PROJECT_ENVIRONMENT="$ROOT/.venv" uv sync --frozen --no-dev

echo ">>> Готово. Активация: source .venv/bin/activate"
echo ">>> Проверка OpenCV: .venv/bin/python -c \"import cv2; print(cv2.__version__)\""
