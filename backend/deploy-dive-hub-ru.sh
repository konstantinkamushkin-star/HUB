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

if [[ -d ../.git ]] && [[ "$(basename "$ROOT")" == "backend" ]]; then
  echo ">>> git pull (репозиторий: $(cd .. && pwd))"
  (cd .. && git pull --ff-only)
elif [[ -d .git ]]; then
  echo ">>> git pull"
  git pull --ff-only
fi

# Порт на ХОСТЕ, куда проброшен api:3000 (см. docker-compose API_PUBLISH_PORT).
PUBLISH_PORT="${API_PUBLISH_PORT:-3000}"
if grep -q '^API_PUBLISH_PORT=' .env 2>/dev/null; then
  PUBLISH_PORT="$(grep '^API_PUBLISH_PORT=' .env | cut -d= -f2- | tr -d '\r' | tr -d ' ')"
fi
# Пароль Postgres на 127.0.0.1 (как в docker-compose POSTGRES_PASSWORD, если не задан в .env).
MIGRATE_DB_PASSWORD="${MIGRATE_DB_PASSWORD:-postgres}"
if grep -q '^POSTGRES_PASSWORD=' .env 2>/dev/null; then
  MIGRATE_DB_PASSWORD="$(grep '^POSTGRES_PASSWORD=' .env | cut -d= -f2- | tr -d '\r')"
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

echo ">>> Миграции с хоста → 127.0.0.1 (пароль как у контейнера postgres)"
npm ci --omit=dev
DB_HOST=127.0.0.1 DB_PASSWORD="${MIGRATE_DB_PASSWORD}" node scripts/apply-all-migrations.cjs

echo ">>> Сборка и перезапуск контейнера API"
docker compose build api
docker compose up -d api

echo ">>> Проверки на localhost:${PUBLISH_PORT} (внешний порт API)"
curl -fsS "http://127.0.0.1:${PUBLISH_PORT}/api/health" | head -c 500 && echo ""
curl -fsSI "http://127.0.0.1:${PUBLISH_PORT}/privacy" | head -n 8

echo ""
echo "Снаружи: curl -fsS https://api.dive-hub.ru/api/health"
echo "Для https://dive-hub.ru/privacy — прокси на этот же порт (см. legal-pages.controller.ts в репозитории)."
