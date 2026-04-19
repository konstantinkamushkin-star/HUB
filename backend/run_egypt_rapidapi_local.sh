#!/usr/bin/env bash
# Локальный запуск выгрузки дайвсайтов Египта через RapidAPI (20 GPS-запросов).
# Требуется Node.js. Ключ и host читаются из .env.local или .env (см. .env.local.example).

set -euo pipefail
cd "$(dirname "$0")"

export RAPIDAPI_HOST="${RAPIDAPI_HOST:-world-scuba-diving-sites-api.p.rapidapi.com}"

if [[ -f .env.local ]]; then
  set -a
  # shellcheck disable=SC1091
  source .env.local
  set +a
elif [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
fi

if [[ -z "${RAPIDAPI_KEY:-}" ]]; then
  echo "Задайте RAPIDAPI_KEY в backend/.env.local (скопируйте из .env.local.example)." >&2
  exit 1
fi

echo "Host: $RAPIDAPI_HOST"
echo "Running fetch_egypt_divesites_rapidapi.js …"
node fetch_egypt_divesites_rapidapi.js
