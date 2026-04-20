# Underwater Vision Module

Обработка подводных **фото** и **видео** через порт алгоритма **[underwater-image-color-correction](https://github.com/nikolajbech/underwater-image-color-correction)** (Nikolaj Bech Andersen): авто-коррекция RGB по гистограмме и сдвигу оттенка красного канала.

## Алгоритм и лицензия

- Реализация: [`src/uvm/pipeline/nikolaj_bech_color_correction.py`](src/uvm/pipeline/nikolaj_bech_color_correction.py) — построчный порт логики из upstream `index.js`.
- Upstream: <https://github.com/nikolajbech/underwater-image-color-correction> — проверьте лицензию репозитория перед коммерческим использованием.

## Параметр `strength` (расширение API)

В оригинальном JS-пакете силы нет. Расширение API: **`out = strength * corrected + (1 - strength) * original`** с клампом `0…1`. Значение **`strength=1.0`** соответствует upstream (полное применение матрицы, как в README репозитория).

## Запуск

```bash
cd backend/underwater-vision-module
python3 -m venv .venv
source .venv/bin/activate
pip install -e .
export PYTHONPATH=src
uvicorn uvm.api.app:app --host 0.0.0.0 --port 8010
```

Или из корня `backend`: `./run-underwater-vision-module.sh`

Если используете **`uv`** и `uv.lock`: после смены зависимостей выполните `uv lock` и `uv sync` локально (в среде CI без `uv` используйте `pip install -e .`).

## HTTP API

### `POST /v1/process/photo/{engine}`

- **Тело**: `multipart/form-data`, поле **`image`** (файл).
- **Путь**: `engine` — один из `ai1` | `ai2` | `cursor` | `seathru` (все ведут себя **одинаково**, это алиасы для совместимости с клиентом).
- **Query**: `strength` (0…1, по умолчанию **1.0** — как upstream без ослабления); `depth_hint_m`, `quality`, `mode` принимаются и **игнорируются**.

**Ответ**: JSON `image_jpeg_base64` (шестнадцатеричная строка JPEG, как раньше) и `report` (в т.ч. `backend`, `hue_shift_deg`, `blend_strength`).

### `POST /v1/process/video/{engine}`

Тот же `engine`; вход — multipart поле **`video`** (MP4); выход — **сырой MP4** в теле ответа. Каждый кадр обрабатывается тем же алгоритмом, что и фото.

### `GET /health`

Краткий статус и список движков.

## Проверка порта (опционально)

```bash
export PYTHONPATH=src
python scripts/compare_nikolaj_bech_matrix.py
```

Быстрая проверка: матрица 20 коэффициентов конечна.

Сравнение с **npm** `underwater-image-color-correction` (нужны Node и `npm install` пакета в каталоге модуля или глобально):

```bash
export PYTHONPATH=src
python scripts/compare_nikolaj_bech_port.py
```

См. также [`NOTICE.md`](NOTICE.md) про upstream и лицензию.
