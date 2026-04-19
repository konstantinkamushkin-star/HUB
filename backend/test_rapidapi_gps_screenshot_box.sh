#!/usr/bin/env bash
# Один тестовый запрос — те же параметры, что в Code Snippet на RapidAPI (скриншот):
#   GET …/divesites/gs?southWestLat=… (как в RapidAPI Code Snippets)
# Это bbox вокруг Британских островов / Ирландии (проверка ключа и сети), не Египет.

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
  echo "Задайте RAPIDAPI_KEY в backend/.env.local" >&2
  exit 1
fi

# Значения из скрина RapidAPI (Playground)
SOUTH_WEST_LAT="50.995577266225524"
NORTH_EAST_LAT="58.59328356952258"
SOUTH_WEST_LNG="-12.542403615716239"
NORTH_EAST_LNG="3.827225290533761"

GPS_PATH="${RAPIDAPI_GPS_PATH:-/divesites/gs}"
URL="https://${RAPIDAPI_HOST}${GPS_PATH}?southWestLat=${SOUTH_WEST_LAT}&northEastLat=${NORTH_EAST_LAT}&southWestLng=${SOUTH_WEST_LNG}&northEastLng=${NORTH_EAST_LNG}"

echo "GET $URL"
echo ""

curl -sS --max-time 60 \
  -H "x-rapidapi-host: ${RAPIDAPI_HOST}" \
  -H "x-rapidapi-key: ${RAPIDAPI_KEY}" \
  -H "Content-Type: application/json" \
  "$URL" | head -c 4000

echo ""
echo ""
echo "(обрезано до 4000 символов; полный ответ сохраните в файл при необходимости)"
