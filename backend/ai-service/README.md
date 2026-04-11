# Underwater Image Enhancement AI Service

Сервис обработки подводных фото: цветокоррекция, восстановление красного по глубине, контраст (CLAHE), насыщенность, резкость. Пайплайн ориентирован на приёмы из уроков по про-обработке подводных снимков (баланс белого, восстановление цвета по глубине, контраст и ясность в LAB, насыщенность). Опционально — инференс своей ONNX-модели.

**Где смотреть приёмы и теорию:** уроки по обработке подводных фото в Photoshop/Lightroom (например, [profotovideo.ru](https://profotovideo.ru/obrabotka-fotografiy/obrabotka-podvodnich-fotografiy-v-photoshop-urok-fotoshop), поиск по запросу «обработка подводных фотографий»), статьи про Color correction, CLAHE, depth-based red recovery.

## Запуск

```bash
cd backend/ai-service
python -m venv .venv
source .venv/bin/activate   # Windows: .venv\Scripts\activate
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8000
```

Сервис будет доступен по адресу `http://localhost:8000`.

## API

- **GET /health** — проверка работы сервиса.
- **POST /process** — обработка изображения (multipart/form-data):
  - `image` (file) — изображение (JPG/PNG/HEIC).
  - `depth_m` (float, optional) — глубина в метрах (по умолчанию 10).
  - `strength` (float, optional) — сила коррекции 0–1 (по умолчанию 0.7).
  - `use_ai` (bool, optional) — использовать ONNX-модель, если загружена (по умолчанию true).

Ответ: бинарное изображение JPEG.

### UVM-совместимый маршрут (iOS Dive Editor)

- **`POST /v1/process/photo/cursor`** — тот же алгоритм, что в `underwater-vision-module` (нужен каталог `../underwater-vision-module/src` рядом с `ai-service`).
- **`POST /v1/process/photo/seathru`** — Sea-Thru (CVPR 2019), модуль `sea_thru_cvpr2019.py`, зависимость **scipy** из `requirements.txt`.

Оба — multipart поле **`image`**, query **`strength`**, опционально **`depth_hint_m`**. Ответ JSON: `image_jpeg_base64` (hex), `report`.

Если при `seathru` приходит **400** с текстом вроде *«supports only engine 'cursor'»*, на сервере запущена **старая** версия `main.py`: обновите код, `pip install -r requirements.txt` и перезапустите uvicorn.

## Собственная ONNX-модель

1. **Berman et al.–inspired** (`underwater_berman.onnx`): `train/train_berman_haze.py` — **первый** в `auto`.
2. **Jain et al. ICVGIP’22** (`icvgip_color.onnx`): `train/train_icvgip_color.py` — компактный CNN цветового восстановления (DOI [10.1145/3571600.3571630](https://doi.org/10.1145/3571600.3571630), [arXiv:2211.14821](https://arxiv.org/abs/2211.14821)); в `auto` идёт **после** Berman, **до** U-Net. Полный synthetic→real MUNIT из статьи не портирован — сеть обучается на **ваших** парах.
3. **U-Net** (`underwater.onnx`): `train/train.py`.
4. **Формат ONNX**: один input/output, NCHW `[1, 3, H, W]` или NHWC `[1, H, W, 3]`, float32 0–1.

Если ни одного `.onnx` нет, используется классический пайплайн.

Переменные окружения:
- `UNDERWATER_MODELS_PATH` — путь к папке с моделями (по умолчанию `ai-service/models`).
- `UNDERWATER_ONNX_PREFERENCE` — `auto` (berman → icvgip_color → unet), либо `berman`, `icvgip`, `unet`.
- `UNDERWATER_ONNX` — явный путь к одному `.onnx` файлу.

## Обучение своей модели

Полная инструкция: **[train/README.md](train/README.md)** (данные, GPU, качество).

Кратко:

1. Пары `train/data/input/` и `train/data/target/` — **одинаковые имена файлов**; лучше всего эталон после **ручной** цветокоррекции, не только `generate_targets.py`.
2. Бутстрап (из каталога `train/`): `cd train && mkdir -p data/input data/target` → положить фото в `data/input` → `python generate_targets.py --data_dir ./data`
3. U-Net: `python train.py --data_dir ./data --epochs 80 --export ../models/underwater.onnx`  
   Berman-style: `python train_berman_haze.py --data_dir ./data_israel --export ../models/underwater_berman.onnx`  
   ICVGIP’22 color CNN: `python train_icvgip_color.py --data_dir ./data_israel --export ../models/icvgip_color.onnx`

После экспорта перезапустите uvicorn. В `inference.py` выход ONNX **смешивается** с классикой для устойчивости цвета.

## Интеграция с бэкендом DiveHub

В `.env` бэкенда укажите:

```
AI_UNDERWATER_SERVICE_URL=http://localhost:8000
```

Эндпоинт API бэкенда: **POST /api/v1/underwater-ai/process** (multipart, поле `image` + опционально `depth_m`, `strength`, `use_ai`).
