#!/usr/bin/env bash
# Запускать на VPS из каталога admin-web после git pull:
#   chmod +x deploy-vps.sh && ./deploy-vps.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

echo ">>> cwd: $ROOT"
echo ">>> git: $(git rev-parse --short HEAD 2>/dev/null || echo '?')"

# Не задавать NODE_ENV=development: `next build` сломается (ошибка <Html> / prerender).
# Не оставлять NODE_ENV=production до npm ci, иначе не поставятся devDependencies.
unset NODE_ENV
npm ci
rm -rf .next
npm run build

echo ">>> Остановите процесс на порту 3001 и выполните: npm run start"
echo ">>> Или: pm2 restart admin-web   (если настроен pm2 из ЭТОГО каталога)"
echo ">>> Проверка: curl -sS http://127.0.0.1:3001/api/deploy-info"
