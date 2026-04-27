#!/usr/bin/env bash
# Деплой под ваш стек: dive-hub.ru + docker-compose (postgres, redis, api) из .env.production.example.
# На VPS один раз: chmod +x deploy-dive-hub-ru.sh
# Обновление: cd .../backend && ./deploy-dive-hub-ru.sh
# Git: либо .git в backend/, либо (часто) корень репозитория — родительская папка DivePROD/
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

if [[ ! -f .env ]]; then
  echo "Нет .env — скопируйте шаблон и задайте JWT_SECRET:"
  echo "  cp .env.production.example .env && nano .env"
  echo "  openssl rand -base64 48"
  exit 1
fi

# На VPS после `pip install -e` появляется каталог *egg-info* внутри src — он не должен жить в дереве git и ломает pull/checkout.
shopt -s nullglob
for d in "$ROOT/underwater-vision-module/src"/*egg-info "$ROOT/underwater-vision-module"/*egg-info; do
  [[ -e "$d" ]] && rm -rf "$d"
done
shopt -u nullglob

if [[ -d ../.git ]] && [[ "$(basename "$ROOT")" == "backend" ]]; then
  REPO_ROOT="$(cd .. && pwd)"
  if [[ -n "$(cd "$REPO_ROOT" && git status --porcelain)" ]]; then
    echo ">>> git pull пропущен: в репозитории есть локальные изменения ($REPO_ROOT)"
    echo ">>> Подсказка: закоммитьте/стэшните изменения, чтобы снова включить авто-pull."
  else
    echo ">>> git pull (репозиторий: $REPO_ROOT)"
    (cd "$REPO_ROOT" && git pull --ff-only)
  fi
elif [[ -d .git ]]; then
  if [[ -n "$(git status --porcelain)" ]]; then
    echo ">>> git pull пропущен: в backend-репозитории есть локальные изменения"
    echo ">>> Подсказка: закоммитьте/стэшните изменения, чтобы снова включить авто-pull."
  else
    echo ">>> git pull"
    git pull --ff-only
  fi
fi

# Порт на ХОСТЕ, куда проброшен api:3000 (см. docker-compose API_PUBLISH_PORT).
PUBLISH_PORT="${API_PUBLISH_PORT:-3000}"
if grep -q '^API_PUBLISH_PORT=' .env 2>/dev/null; then
  PUBLISH_PORT="$(grep '^API_PUBLISH_PORT=' .env | cut -d= -f2- | tr -d '\r' | tr -d ' ')"
fi
# Учётные данные для миграций на 127.0.0.1 — только контейнерный Postgres из compose,
# не DB_USERNAME из .env (там часто admin/внешняя БД для другого окружения).
MIGRATE_DB_USER="${MIGRATE_DB_USER:-postgres}"
if grep -q '^POSTGRES_USER=' .env 2>/dev/null; then
  MIGRATE_DB_USER="$(grep '^POSTGRES_USER=' .env | cut -d= -f2- | tr -d '\r' | tr -d ' ')"
fi
MIGRATE_DB_PASSWORD="${MIGRATE_DB_PASSWORD:-postgres}"
if grep -q '^POSTGRES_PASSWORD=' .env 2>/dev/null; then
  MIGRATE_DB_PASSWORD="$(grep '^POSTGRES_PASSWORD=' .env | cut -d= -f2- | tr -d '\r')"
fi
MIGRATE_DB_DATABASE="${MIGRATE_DB_DATABASE:-divehub}"
if grep -q '^POSTGRES_DB=' .env 2>/dev/null; then
  MIGRATE_DB_DATABASE="$(grep '^POSTGRES_DB=' .env | cut -d= -f2- | tr -d '\r' | tr -d ' ')"
fi
MIGRATE_DB_PORT="${MIGRATE_DB_PORT:-5432}"
if grep -q '^POSTGRES_PUBLISH_PORT=' .env 2>/dev/null; then
  MIGRATE_DB_PORT="$(grep '^POSTGRES_PUBLISH_PORT=' .env | cut -d= -f2- | tr -d '\r' | tr -d ' ')"
fi

echo ">>> Docker: Postgres + Redis"
docker compose up -d postgres redis

echo ">>> ждём Postgres (до 90 с)…"
for i in $(seq 1 90); do
  if docker compose exec -T postgres pg_isready -U postgres -d divehub >/dev/null 2>&1; then
    echo ">>> Postgres готов"
    break
  fi
  sleep 1
  if [[ "$i" -eq 90 ]]; then
    echo "Postgres не поднялся за 90 с"; exit 1
  fi
done

echo ">>> Docker: UVM (Python) — образ с нуля, uv sync --frozen из uv.lock (без наслоения старого opencv)"
docker compose build --no-cache uvm
docker compose up -d uvm

echo ">>> ждём healthcheck UVM (до 300 с; первый импорт torch/cv2 может быть долгим)…"
for i in $(seq 1 150); do
  uvm_id="$(docker compose ps -q uvm 2>/dev/null || true)"
  if [[ -n "$uvm_id" ]]; then
    st="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$uvm_id" 2>/dev/null || echo none)"
    if [[ "$st" == "healthy" ]]; then
      echo ">>> UVM healthy"
      break
    fi
  fi
  sleep 2
  if [[ "$i" -eq 150 ]]; then
    echo "!!! UVM не стал healthy за 300 с"
    docker compose logs uvm --tail 120 2>/dev/null || true
    exit 1
  fi
done

echo ">>> Миграции с хоста → 127.0.0.1:${MIGRATE_DB_PORT} (пользователь ${MIGRATE_DB_USER}, БД ${MIGRATE_DB_DATABASE})"
npm ci --omit=dev
DB_HOST=127.0.0.1 \
  DB_PORT="${MIGRATE_DB_PORT}" \
  DB_USERNAME="${MIGRATE_DB_USER}" \
  DB_PASSWORD="${MIGRATE_DB_PASSWORD}" \
  DB_DATABASE="${MIGRATE_DB_DATABASE}" \
  node scripts/apply-all-migrations.cjs

echo ">>> Сборка и перезапуск контейнера API (Nest, без кэша слоёв образа; хост-порт из .env: ${PUBLISH_PORT} → контейнер :3000)"
docker compose build --no-cache api
set +e
docker compose up -d api
compose_api_rc=$?
set -e
if [[ "$compose_api_rc" -ne 0 ]]; then
  echo "!!! docker compose up -d api завершился с кодом ${compose_api_rc}."
  echo "!!! Часто: «failed to bind host port … address already in use» — занят API_PUBLISH_PORT=${PUBLISH_PORT} (или 3000 по умолчанию)."
  echo "!!! Кто слушает порты: ss -tlnp | grep -E ':(3000|3001|3002|8080)\\b' || true"
  echo "!!! В backend/.env задайте свободный порт, например: API_PUBLISH_PORT=3002"
  echo "!!! sed -i 's/^API_PUBLISH_PORT=.*/API_PUBLISH_PORT=3002/' .env   # подставьте свой порт"
  echo "!!! Затем снова ./deploy-dive-hub-ru.sh и в nginx: proxy_pass http://127.0.0.1:<тот_же_порт> (снаружи достаточно 443)."
  docker compose ps -a 2>/dev/null || true
  exit 1
fi
sleep 2

echo ">>> Проверки на localhost:${PUBLISH_PORT} (внешний порт API)"
if ! curl -fsS --connect-timeout 3 "http://127.0.0.1:${PUBLISH_PORT}/api/health" >/dev/null 2>&1; then
  echo "!!! API не отвечает на порту ${PUBLISH_PORT}."
  echo "!!! Проверьте, что контейнер api в состоянии running: docker compose ps; логи: docker compose logs api --tail 80"
  echo "!!! Если порт занят при старте — смените API_PUBLISH_PORT в .env (см. сообщение выше при ошибке compose)."
  docker compose ps
  docker compose logs api --tail 50 2>/dev/null || true
  exit 1
fi
curl -fsS "http://127.0.0.1:${PUBLISH_PORT}/api/health" | head -c 500 && echo ""
curl -fsS -X POST "http://127.0.0.1:${PUBLISH_PORT}/api/v1/underwater-ai/health" | head -c 200 && echo ""
curl -fsSI "http://127.0.0.1:${PUBLISH_PORT}/privacy" | head -n 8

echo ""
echo "Снаружи: curl -fsS https://api.dive-hub.ru/api/health"
echo "Подводный AI: curl -fsS -X POST https://api.dive-hub.ru/api/v1/underwater-ai/health  (ожидается {\"available\":true})"
echo "Для https://dive-hub.ru/privacy — прокси на этот же порт (см. legal-pages.controller.ts в репозитории)."
