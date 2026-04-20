#!/usr/bin/env bash
# Запуск UVM с любого места. Корень репозитория не нужен в pwd.
# Зависимости: предпочтительно uv + uv.lock (см. underwater-vision-module/bootstrap-venv.sh).
set -euo pipefail
UVM="$(cd "$(dirname "$0")/underwater-vision-module" && pwd)"
cd "$UVM"

if [[ "${1:-}" == "rebuild-venv" ]]; then
  exec "$UVM/bootstrap-venv.sh"
fi

if command -v uv >/dev/null 2>&1 && [[ -f uv.lock ]]; then
  if [[ ! -d .venv ]]; then
    echo "Создаю .venv через uv sync --frozen…"
    UV_NO_CACHE=1 uv venv .venv --python 3.11
    UV_NO_CACHE=1 UV_PROJECT_ENVIRONMENT="$UVM/.venv" uv sync --frozen --no-dev
  fi
  # shellcheck source=/dev/null
  source .venv/bin/activate
else
  if [[ ! -d .venv ]]; then
    echo "Создаю .venv (python3 -m venv .venv)…"
    python3 -m venv .venv
  fi
  # shellcheck source=/dev/null
  source .venv/bin/activate
  pip install -q -e . 2>/dev/null || pip install -e .
fi

export PYTHONPATH=src
PORT="${PORT:-8010}"
echo "UVM: $UVM → http://0.0.0.0:$PORT  (Nikolaj Bech color correction; если Errno 48: kill старый Python или PORT=8011 $0)"
exec uvicorn uvm.api.app:app --host 0.0.0.0 --port "$PORT"
