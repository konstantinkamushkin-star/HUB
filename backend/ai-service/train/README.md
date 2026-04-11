# Обучение модели подводной цветокоррекции

Классический пайплайн в `inference.py` даёт предсказуемый, но «плоский» результат. **Нейросеть учится на ваших парах** «как снято → как хотите видеть».

## 0. Готовый датасет: Израиль, 57 стереопар

Если у вас лежат папки **Satil, Katzaa, Nachsholim, Michmoret** (TIF/MAT), см. **[ISRAEL_STEREO_DATASET.md](ISRAEL_STEREO_DATASET.md)** и скрипт **`prepare_israel_stereo_dataset.py`**.

## 1. Данные (важнее архитектуры)

### Таблички на одном кадре (без пары `target/`)

Если на каждом фото есть **цветовые таблички** и вы знаете **эталонный RGB** патчей (ColorChecker, свои эталоны), пары `input`/`target` **не нужны**. Разметка: `images/` + `labels/<имя>.json` — формат в **[chart_dataset.py](chart_dataset.py)**. Обучение:

```bash
python train_berman_chart.py --data_dir ./data_charts --epochs 80 --export ../models/underwater_berman.onnx
```

Сеть штрафуется только за средний цвет внутри ваших прямоугольников; остальное изображение удерживается слабым членом `identity_weight` к исходнику.

| Тип пар | Качество |
|--------|----------|
| **A.** Сырой подводный кадр → **ручная** доработка в Lightroom/Photoshop (ваш стиль) | Лучше всего |
| **B.** Пара из двух камер / экспозиций, где target ближе к «идеалу» | Хорошо |
| **C.** Только `generate_targets.py` (эталон = классический пайплайн) | Низкий потолок: сеть копирует то же самое |

Структура:

```
train/data/
  input/   # исходники
    001.jpg
  target/  # эталоны, **то же имя файла**
    001.jpg
```

Рекомендации:

- не меньше **200–500** пар для заметного эффекта; для теста хватит 20–50;
- разнообразие: глубина, мутность, дневной/вспышка, зелёная/синяя вода;
- разрешение любое — при обучении всё ресайзится до `--size` (по умолчанию 256).

## 2. Быстрый бутстрап (слабый эталон)

Из корня `ai-service`:

```bash
cd train
mkdir -p data/input data/target
# скопируйте подводные фото в data/input/
python generate_targets.py --data_dir ./data
```

Это заполнит `data/target/` классической обработкой — только чтобы проверить пайплайн обучения.

## 3. Обучение

### 3a. U-Net (пиксельное image→image)

```bash
cd backend/ai-service/train
python -m venv .venv-train
source .venv-train/bin/activate   # Windows: .venv-train\Scripts\activate
pip install -r requirements-train.txt
python train.py --data_dir ./data --epochs 80 --batch_size 8 --ssim_weight 0.35 --export ../models/underwater.onnx
```

### 3b. Сеть в духе Berman et al. (SQUID / haze-lines, TPAMI 2020)

Глобальные **A** и **ω** + модель `I = J·t + A(1−t)` и лёгкий refine — см. `berman_haze_net.py`. Это **не** полный порт [underwater-hl](https://github.com/danaberman/underwater-hl), а обучаемый аналог для ваших пар.

```bash
python train_berman_haze.py --data_dir ./data_israel --epochs 120 --batch_size 8 --export ../models/underwater_berman.onnx
```

`inference.py` в режиме `auto`: **`underwater_berman.onnx`** → **`icvgip_color.onnx`** → **`underwater.onnx`** (`UNDERWATER_ONNX_PREFERENCE`, `UNDERWATER_ONNX`).

### 3c. ICVGIP 2022 — color restoration CNN (Jain et al.)

Статья: *Towards Realistic Underwater Dataset Generation and Color Restoration* (ICVGIP’22, [arXiv:2211.14821](https://arxiv.org/abs/2211.14821)). В репозитории — **блок восстановления цвета** (`icvgip_color_net.py`), обучаемый на тех же парах, что и U-Net. Доменная адаптация synthetic→real из статьи **не реализована** (официальный [TRUDGCR](https://github.com/nehamjain10/TRUDGCR) без весов).

```bash
python train_icvgip_color.py --data_dir ./data_israel --epochs 80 --export ../models/icvgip_color.onnx
```

Параметры (U-Net):

- `--size` — размер стороны (и фиксированный вход ONNX), по умолчанию 256. Для лучшей детализации попробуйте 384 при достаточной VRAM.
- `--ssim_weight` — баланс L1 и структуры (SSIM-подобный штраф); 0.25–0.5 обычно ок.
- Чекпоинт: `../models/underwater.pt`, лучший по val сохраняется автоматически.

CPU обучение возможно, но медленное; GPU сильно ускоряет.

## 4. Проверка сервиса

```bash
cd ../..
source ai-service/.venv/bin/activate   # или ваш venv с onnxruntime
pip install -r ai-service/requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8000
```

В `.env` бэкенда: `AI_UNDERWATER_SERVICE_URL=http://localhost:8000`.

## 5. Как улучшать цвет дальше

1. **Больше ручных пар** в вашем визуальном стиле.  
2. **Perceptual / VGG loss** — можно добавить в `train.py` (зависимость от `torchvision.models`).  
3. **Больше разрешение** (`--size 384`) и чуть больше `base` в `model.py` (например 48) — больше VRAM.  
4. Публичные датасеты подводных сцен (поиск *underwater image enhancement dataset*) — проверить лицензию и домен (море/озеро/бассейн).

## 6. Ограничения текущей U-Net

Фиксированный квадрат 256×256 на инференсе: в `inference.py` изображение масштабируется под модель и обратно. Для 4K лучше обучать с большим `--size` или перейти на полносверточную модель с произвольным H/W в ONNX (отдельная доработка экспорта).
