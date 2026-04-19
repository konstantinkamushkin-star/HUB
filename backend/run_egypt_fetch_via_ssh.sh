#!/usr/bin/env bash
# Запуск выгрузки Egypt+RapidAPI на удалённом VPS (EU/UK/US), где RapidAPI не отдаёт 451.
# На Mac: ssh, rsync, scp; на VPS: Node.js 18+.
#
#   export VPS_SSH=ubuntu@203.0.113.50
#   ./run_egypt_fetch_via_ssh.sh
# или:
#   ./run_egypt_fetch_via_ssh.sh debian@your-vps.eu
#
# Нужен backend/.env.local с RAPIDAPI_KEY.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REMOTE_TARGET="${1:-${VPS_SSH:-}}"
# Папка в домашнем каталоге на VPS
REMOTE_REL="${REMOTE_SUBDIR:-divehub-egypt-fetch}"

if [[ -z "${REMOTE_TARGET}" ]]; then
  echo "Укажите SSH:  export VPS_SSH=user@host   или   $0 user@host" >&2
  exit 1
fi

if [[ ! -f "${SCRIPT_DIR}/.env.local" ]]; then
  echo "Нет ${SCRIPT_DIR}/.env.local — создайте файл с RAPIDAPI_KEY" >&2
  exit 1
fi

if ! command -v ssh >/dev/null || ! command -v rsync >/dev/null; then
  echo "Нужны ssh и rsync" >&2
  exit 1
fi

echo "→ VPS: ${REMOTE_TARGET}"
echo "→ Папка на сервере: ~/${REMOTE_REL}"

ssh -o ConnectTimeout=20 "${REMOTE_TARGET}" "mkdir -p '${REMOTE_REL}'"

rsync -az -e ssh \
  "${SCRIPT_DIR}/fetch_egypt_divesites_rapidapi.js" \
  "${SCRIPT_DIR}/.env.local" \
  "${REMOTE_TARGET}:${REMOTE_REL}/"

echo "→ node на VPS (проверка: node -v)…"
ssh "${REMOTE_TARGET}" "cd '${REMOTE_REL}' && node fetch_egypt_divesites_rapidapi.js"

echo "→ Копирование JSON на этот Mac…"
scp "${REMOTE_TARGET}:${REMOTE_REL}/dive_sites_egypt_rapidapi.json" "${SCRIPT_DIR}/"
scp "${REMOTE_TARGET}:${REMOTE_REL}/dive_sites_egypt_rapidapi.fetch.json" "${SCRIPT_DIR}/" || true

echo ""
echo "Готово:"
echo "  ${SCRIPT_DIR}/dive_sites_egypt_rapidapi.json"
echo "  ${SCRIPT_DIR}/dive_sites_egypt_rapidapi.fetch.json"
