# Карточка сервера / репозитория (заполнить и хранить у DevOps)

Скопируйте разделы ниже во внутреннюю wiki или передайте команде. **Пароли, JWT, ключи в чат не копировать** — только «задано в `.env` / в панели».

---

## Git

| Поле | Значение (заполнить) |
|------|----------------------|
| **URL репозитория (HTTPS)** | `https://github.com/konstantinkamushkin-star/HUB.git` |
| **URL репозитория (SSH)** | `git@github.com:konstantinkamushkin-star/HUB.git` |
| **Remote по умолчанию** | `origin` |
| **Ветка на прод** | `main` _(или указать: `release` / иное)_ |

| Поле | Значение (заполнить) |
|------|----------------------|
| **Корень репозитория на сервере** | `/opt/divehub-src/DivePROD` |
| **Каталог backend** | `/opt/divehub-src/DivePROD/backend` |
| **Обновление кода + деплой** | `cd /opt/divehub-src/DivePROD/backend && ./deploy-dive-hub-ru.sh` — скрипт делает `git pull` из родителя `DivePROD/`, если `.git` там; иначе `git pull` в `backend/`. |

Вручную только pull:

```bash
cd /opt/divehub-src/DivePROD && git pull origin main && cd backend && ./deploy-dive-hub-ru.sh
```

---

## CI / quality gate

Перед деплоем в `main` должны быть зелёные CI-задачи:

- **Backend Build**: `npm ci && npm run verify:prod-build` (папка `backend`)
- **Admin Web Build**: `npm ci && npm run build` (папка `admin-web`)
- **Release Audit**: `./scripts/pre_release_audit.sh --skip-build` (проверки на build-артефакты, локальные секреты и запрещённые debug endpoint/header)

Если CI не поднят на сервере GitHub Actions (ограничения/квоты), прогонять эти команды вручную на dev-машине перед `git push`.

Полный локальный прогон перед релизом:

```bash
./scripts/pre_release_audit.sh
```

Быстрый smoke после деплоя (API + legal pages):

```bash
./scripts/post_deploy_smoke.sh "https://api.dive-hub.ru" "https://dive-hub.ru"
```

---

## Сервер (VPS)

| Поле | Значение (заполнить) |
|------|----------------------|
| **SSH (снаружи, без секретов в wiki)** | Публичный **IP** или домен, который резолвится в IP (напр. `root@89.104.94.246`). На **Mac** удобно алиас в `~/.ssh/config` (`Host dive-vps`, `IdentityFile ~/.ssh/...`). |
| **Имя из панели (напр. `cv6364107`)** | Часто **внутреннее** имя хоста: с другой машины в интернете **может не резолвиться** — это не ошибка DNS «у провайдера», а ожидаемо. |
| **Провайдер / hostname** | _(REG.RU / иной; hostname панели)_ |
| **ОС** | Ubuntu 24.04 _(проверка: `lsb_release -a`)_ |
| **Пользователь деплоя** | `root` _(или отдельный пользователь + sudo)_ |

### Уже залогинены на VPS по SSH

**Не** вызывать с сервера `ssh dive-vps` — алиас `dive-vps` есть только в `~/.ssh/config` **на вашем Mac**, на самой VPS такого имени в DNS нет. Сразу:

`cd /opt/divehub-src/DivePROD/backend && ./deploy-dive-hub-ru.sh`

### Деплой из Cursor / агента без вашего SSH

- **Нормальное ограничение:** у среды агента **нет** доступа к вашему приватному ключу `~/.ssh/...` на Mac → `Permission denied (publickey)` при попытке зайти на VPS — **не баг агента**.
- Что можно поручить агенту без SSH: PR, миграции, правки `deploy-dive-hub-ru.sh`, чеклист после деплоя, точные команды **для вашего терминала** (вы выполняете один раз с машины, где есть ключ).
- Формулировка задачи для чата: «подключение только с моего Mac по ключу к `root@<публичный_IP>`, репо `/opt/divehub-src/DivePROD`, деплой `./deploy-dive-hub-ru.sh`».

Пример с Mac (подставьте свой ключ и публичный IP VPS):

```bash
# IP в примере — один из возможных; подставьте актуальный адрес своего сервера.
ssh -i ~/.ssh/id_ed25519_regcloud_nopass -o IdentitiesOnly=yes root@89.104.94.246 \
  'cd /opt/divehub-src/DivePROD/backend && ./deploy-dive-hub-ru.sh'
```

---

## Как крутится бэкенд

| Поле | Значение (заполнить) |
|------|----------------------|
| **Способ** | **Docker Compose** в каталоге `backend/`: `docker compose build` / `up -d`. Скрипт **`backend/deploy-dive-hub-ru.sh`** поднимает postgres + redis, миграции с хоста, затем `api`. |
| **Дополнительно** | systemd / pm2 / k8s: _(если есть — описать; иначе «нет»)_ |

---

## Порты и Compose (`backend/docker-compose.yml`)

На **хосте** в актуальном файле:

| Сервис | Хост | Примечание |
|--------|------|------------|
| **API** | `${API_PUBLISH_PORT:-3000}` → контейнер `3000` | Если порт занят старым процессом — в `.env` рядом с compose: `API_PUBLISH_PORT=3001`, nginx на `api.dive-hub.ru` и проверки `curl` — на этот порт. |
| **Postgres** | `${POSTGRES_PUBLISH_PORT:-5432}:5432` | Если **Bind for 0.0.0.0:5432 failed** — в `.env`: `POSTGRES_PUBLISH_PORT=5433`. Миграции: `deploy-dive-hub-ru.sh` подставит тот же порт и **`DB_USERNAME=postgres`**. |
| **Redis** | **не** публикуется на хост | Только сеть Docker `api` ↔ `redis` (избегает конфликта с Redis на хосте на `6379`). |

### 152-ФЗ / «белый список» портов у облака

Часто **с интернета** открыты только `22`, `80`, `443`, почтовые и т.д. Это **не мешает** Docker слушать, например, `127.0.0.1:3001` на хосте: снаружи клиенты ходят на **`https://api.dive-hub.ru:443`**, а **nginx/Caddy** на том же сервере проксирует на **`http://127.0.0.1:<API_PUBLISH_PORT>`** (порт из `.env`, см. выше). Публично открывать `3000`/`3001` на firewall **не обязательно**.

Ошибка **`Bind … 3000` / `3001` … address already in use** — на **хосте** выбранный **API_PUBLISH_PORT** уже занят (другой сервис, старый контейнер, второй стек). Решение: в **`backend/.env`** задать **любой свободный** порт (например `3002`, `3010`) и в nginx обновить `proxy_pass` на `127.0.0.1:<этот_порт>`. Проверка: `ss -tlnp | grep -E ':(3000|3001|3002)\\b'` и `docker ps -a`.

---

## HTML-документы на API (вне `/api`)

Nest отдаёт **`GET /privacy`** и **`GET /agreement`** (без префикса `api`). Если **`dive-hub.ru`** смотрит на **Next.js** и в браузере **404 Next** на `/privacy` — либо **обновите сборку** `admin-web` (в репозитории маршруты есть), либо **проксируйте** эти пути на API (ниже).

Пример для **`server_name dive-hub.ru`** — блоки **выше** `location /`, порт = **`API_PUBLISH_PORT`** на хосте (часто `3002`):

```nginx
location = /privacy {
    proxy_pass http://127.0.0.1:3002;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}
location = /agreement {
    proxy_pass http://127.0.0.1:3002;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

Проверка: `curl -fsSI https://dive-hub.ru/privacy | head -n 5` → **200**, `Content-Type: text/html`.

### Caddy (если на VPS нет `/etc/nginx`, а `ss` показывает `caddy` на `:80`/`:443`)

Конфиг обычно **`/etc/caddy/Caddyfile`**. Для **`dive-hub.ru`**: `/privacy` и `/agreement` → **`API_PUBLISH_PORT`** (часто `3002`), остальное → Next (часто `3001`). **`api.dive-hub.ru`** → тот же порт API.

```caddy
api.dive-hub.ru {
	reverse_proxy 127.0.0.1:3002
}

www.dive-hub.ru {
	redir https://dive-hub.ru{uri} permanent
}

dive-hub.ru {
	@apiLegal path /privacy /agreement
	handle @apiLegal {
		reverse_proxy 127.0.0.1:3002
	}
	handle {
		reverse_proxy 127.0.0.1:3001
	}
}
```

Проверка: `caddy validate --config /etc/caddy/Caddyfile` → `systemctl reload caddy`.

---

## `backend/.env` (релевантные строки — секреты замазать)

Скопируйте с сервера только **имена** переменных и безопасные значения; секреты: `***`.

```env
NODE_ENV=production
PORT=3000

# Обязательно, если на хосте занят 3000 (Bind failed) — и обновить nginx proxy_pass:
# API_PUBLISH_PORT=3001

JWT_SECRET=***

CORS_ORIGINS=https://dive-hub.ru,https://www.dive-hub.ru,...
# в CORS — origin браузерных приложений, не URL API

# при необходимости за reverse proxy:
# TRUST_PROXY=1

THROTTLE_TTL_MS=60000
THROTTLE_LIMIT=120

# внутри Docker-сети (для контейнера api):
DB_HOST=postgres
DB_PORT=5432
DB_USERNAME=postgres
DB_PASSWORD=***
DB_DATABASE=divehub

REDIS_HOST=redis
REDIS_PORT=6379
```

Если переменные задаются **только** в `docker-compose.yml` через `environment:` — так и напишите: «дублируется в compose» или «часть в `.env`, часть в compose».

---

## Домены и reverse proxy

| Домен | Назначение | Куда прокси (заполнить) |
|-------|------------|-------------------------|
| `dive-hub.ru` | Сайт / Next (admin-web) + юр. страницы | `127.0.0.1:_____` для сайта; для `/privacy` и `/agreement` — на **порт Nest** (как у API) |
| `api.dive-hub.ru` | Nest API | `127.0.0.1:_____` = **API_PUBLISH_PORT** на хосте |
| `www` | редирект на apex / наоборот | _(кратко)_ |

**Nginx:** фрагмент `server { ... }` для API — приложить **без** путей к сертификатам и без секретов.

**Caddy:** фрагмент `reverse_proxy` / блоки `handle` — аналогично.

---

## Опционально (обрывы, 429, дубликаты)

| Вопрос | Ответ |
|--------|--------|
| Второй инстанс API на том же хосте? | _____ |
| Старый контейнер/процесс на том же порту? | _____ |
| CDN / WAF перед API? | _____ |

---

## Быстрая шпаргалка после `git push` на GitHub

### Минимальный цикл на VPS (практика)

1. **SSH:** `ssh root@<IP>` — подставить IP или использовать `Host` из `~/.ssh/config`; ключи/пароли не хранить в репозитории.
2. **Репозиторий на сервере:** `/opt/divehub-src/DivePROD`.
3. **Обновление API** (скрипт сам тянет `git pull` там, где лежит `.git` — см. выше):
   ```bash
   cd /opt/divehub-src/DivePROD/backend && ./deploy-dive-hub-ru.sh
   ```
4. **Проверка снаружи:**
   ```bash
   curl -fsS https://api.dive-hub.ru/api/health
   ```
5. **`backend/.env`** не трогать без нужды.
6. Если **`git pull`** не срабатывает (конфликты, «грязное» дерево) — **не** латать наугад на проде: сначала **чистый `main`** или **backup-ветка** и осознанный merge/reset по вашему регламенту.

На сервере (как в шаблоне ранее):

```bash
cd /opt/divehub-src/DivePROD/backend
./deploy-dive-hub-ru.sh
```

Дополнительная проверка **локально на хосте** (порт подставить по `API_PUBLISH_PORT` или `3000`):

```bash
curl -fsS http://127.0.0.1:3000/api/dive-sites/ping
curl -fsSI http://127.0.0.1:3000/privacy | head -n 5
docker compose ps
```

Обновление только образа из registry — по вашему процессу, если отойдёте от compose-сборки на сервере.
