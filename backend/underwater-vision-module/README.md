# Underwater Vision Module

Отдельный модуль для нейросетевой обработки подводных фото и видео по ТЗ:
- depth-aware коррекция;
- color cast / white balance коррекция;
- dehaze / backscatter подавление;
- detail refinement без halo;
- video temporal consistency.

## Что уже реализовано в каркасе

- Стандартизированный `SampleRecord` для мультимодальных данных (raw/tif/stereo/depth/calibration/metadata).
- Построение manifest по локациям SQUID-подобной структуры.
- Split-стратегии по локациям (`leave-one-site-out`, `holdout-sites`).
- Модульный pipeline (preprocess -> feature extraction -> restoration -> refine -> postprocess).
- API-скелет для фото и видео (с JSON отчетом и картами).
- Тренировочный каркас с комбинированными loss (recon/perceptual/color/edge/depth/dehaze/hist/temporal).

## Данные (локальные пути)

- `/Users/admin/Downloads/Satil`
- `/Users/admin/Downloads/Katzaa`
- `/Users/admin/Downloads/Nachsholim`
- `/Users/admin/Downloads/Michmoret`

## Быстрый старт

```bash
cd backend/underwater-vision-module
python3 -m venv .venv
source .venv/bin/activate
pip install -e .
```

### 1) Собрать manifest из 4 локаций

```bash
python scripts/build_squid_manifest.py \
  --roots "/Users/admin/Downloads/Satil" "/Users/admin/Downloads/Katzaa" "/Users/admin/Downloads/Nachsholim" "/Users/admin/Downloads/Michmoret" \
  --output ./configs/samples_manifest.json
```

### 2) Посмотреть split (leave-one-site-out)

```bash
python scripts/train_photo.py \
  --manifest ./configs/samples_manifest.json \
  --split_strategy loso \
  --heldout_site Satil \
  --dry_run
```

### Обучение (без новых датасетов — сильнее цель и лоссы)

Псевдо-таргет строится тем же классическим пайплайном, но **агрессивнее** (красный канал, CLAHE, WB, насыщенность), чтобы сеть училась заметному улучшению. Добавлены лоссы: **моменты по каналам**, **R/B баланс**, исправленные **квантили**, опционально **VGG perceptual** (нужен `torchvision` и один раз скачиваются веса VGG16).

```bash
cd backend/underwater-vision-module && source .venv/bin/activate
export PYTHONPATH=src
# при смене параметров учителя удалите кэш псевдо-меток:
# rm -rf ./checkpoints/pseudo_train ./checkpoints/pseudo_val

python scripts/train_photo.py \
  --manifest ./configs/samples_manifest.json \
  --split_strategy loso \
  --heldout_site Satil \
  --epochs 60 \
  --checkpoint_dir ./checkpoints_color_v2 \
  --pseudo_strength 0.92 \
  --lambda_perceptual 0.12
```

- **`--no_perceptual`** — без VGG (офлайн / мало RAM).
- Тонкая настройка учителя: `--pseudo_clahe_boost`, `--pseudo_red_scale`, `--pseudo_saturation_gain`, `--pseudo_wb_lo` / `--pseudo_wb_hi`.

HTTP API: слоты **`ai1` / `ai2`** переключаются переменной **`UVM_AI_BACKEND`**: по умолчанию классический **`UnderwaterPipeline`**, режим **`unet`** — выход чекпоинта как в `scripts/infer_photo.py`. Кнопка **`cursor`** — отдельный алгоритм в `cursor_correction.py`.

### Тест чекпоинта на одном фото

После обучения (`checkpoints/.../best.pt`):

```bash
cd backend/underwater-vision-module && source .venv/bin/activate
PYTHONPATH=src python scripts/infer_photo.py \
  --image "/path/to/photo.jpg" \
  --ckpt ./checkpoints_smoke/best.pt \
  --output ./infer_out.jpg \
  --input_size 512
```

Флаг `--match_original` поднимает выход до разрешения исходного кадра. Размер `--input_size` должен совпадать с тем, на чём учили.

### 3) Запуск API модуля

```bash
export PYTHONPATH=src
uvicorn uvm.api.app:app --host 0.0.0.0 --port 8010
```

**Режимы обработки** (`POST /v1/process/photo/{engine}`): тело — **multipart только с файлом** `image`; **`engine`** — сегмент пути (`ai1` | `ai2` | `cursor` | `seathru`); **`strength`**, опционально **`depth_hint_m`** — в **query** (например `.../v1/process/photo/ai1?strength=0.75`). Так надёжнее, чем `engine` только в query рядом с multipart.

| `engine` | Описание |
|----------|----------|
| `ai1` | Слот **ИИ1** — режим задаётся **`UVM_AI_BACKEND`** (см. ниже). |
| `ai2` | Слот **ИИ2** — тот же переключатель, для `pipeline` чуть сильнее настройки, чем у ИИ1. |
| `cursor` | Всегда алгоритм **Cursor** (`cursor_correction.py`). |
| `seathru` | **Sea-Thru** (Akkaynak & Treibitz, CVPR 2019), `depth_hint_m` задаёт масштаб карты дальности. |
| `seasplat` | Для single-image endpoint возвращает `501 not_implemented`; используйте отдельный multi-view workflow `/v1/seasplat/*`. |

### SeaSplat multi-view workflow

Добавлен отдельный контракт (не через single-photo endpoint):

- `POST /v1/seasplat/scenes` — multipart `images` (несколько кадров), опционально query `poses_json`
- `GET /v1/seasplat/scenes/{scene_id}` — статус сцены
- `POST /v1/seasplat/jobs` — JSON `{ "scene_id": "..." }`
- `GET /v1/seasplat/jobs/{job_id}` — статус/прогресс job
- `GET /v1/seasplat/jobs/{job_id}/render` — итоговый render (`image_jpeg_base64`)

Smoke-тест:

```bash
cd backend/underwater-vision-module && source .venv/bin/activate
python scripts/seasplat_smoke.py --uvm http://127.0.0.1:8010 --image "/path/to/photo.jpg"
```

Переменные окружения SeaSplat runtime:

- `UVM_SEASPLAT_WORKDIR` — рабочая директория сцен/job (default `/tmp/uvm-seasplat`)
- `UVM_SEASPLAT_RUNNER` — путь к внешнему SeaSplat runner (script/binary). Если пусто — fallback runtime.
- `UVM_SEASPLAT_TIMEOUT_S` — timeout внешнего runner в секундах (default `900`)

Если `UVM_SEASPLAT_RUNNER` не задан, используется **отдельный fallback SeaSplat-style multi-frame fusion** (median fusion + WB + transmission proxy + CLAHE), чтобы визуально и алгоритмически отличаться от Sea-Thru кнопки.

**`UVM_AI_BACKEND`** (как ведут себя слоты `ai1` / `ai2`):

| Значение | Поведение |
|----------|-----------|
| **`pipeline`** (по умолчанию) | Классический **`UnderwaterPipeline`** — обычно приятнее «голого» UNet на телефоне. |
| **`unet`** | Строго инференс чекпоинта (как учили). Нужны веса, иначе `503`. |
| **`cursor`** | Тот же алгоритм, что у кнопки Cursor (как в старом баге, когда все кнопки попадали в cursor). |

Переменные окружения (опционально):

- `UVM_AI_BACKEND` — `pipeline` \| `unet` \| `cursor` (см. таблицу)  
- `UVM_CKPT_AI1` / `UVM_CKPT_AI2` — пути к `best.pt` (для режима `unet`)  
- `UVM_INPUT_SIZE` — размер входа сети (по умолчанию `512`)

`GET /health` → поле **`ai_slots_backend`**, а `engines.ai1`/`ai2` — готовы ли слоты (для `unet` нужен чекпоинт; для `pipeline`/`cursor` всегда `true`).

## Примечание по видео

В `api` уже есть endpoint-заготовка для видео. Для production нужно подключить temporal module:
- optical-flow guided warping;
- temporal loss и flicker метрики;
- keyframe strategy.
