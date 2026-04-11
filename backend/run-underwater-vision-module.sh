#!/usr/bin/env bash
# Запуск UVM с любого места. Корень репозитория не нужен в pwd.
set -euo pipefail
UVM="$(cd "$(dirname "$0")/underwater-vision-module" && pwd)"
cd "$UVM"
if [[ ! -d .venv ]]; then
  echo "Создаю .venv (python3 -m venv .venv)..."
  python3 -m venv .venv
fi
# shellcheck source=/dev/null
source .venv/bin/activate
pip install -q -e . 2>/dev/null || pip install -e .
export PYTHONPATH=src
# По умолчанию AI-слоты работают как pipeline (не cursor).
# Можно переопределить явно: UVM_AI_BACKEND=unet|cursor ./run-underwater-vision-module.sh
export UVM_AI_BACKEND="${UVM_AI_BACKEND:-pipeline}"
PORT="${PORT:-8010}"
echo "UVM: $UVM → http://0.0.0.0:$PORT  (если Errno 48: kill старый Python или PORT=8011 $0)"
echo "UVM_AI_BACKEND=$UVM_AI_BACKEND"
exec uvicorn uvm.api.app:app --host 0.0.0.0 --port "$PORT"
