# Израильский стерео-датасет → обучение DiveHub

Официальная страница датасета **SQUID** (Stereo Quantitative Underwater Image Dataset) и статьи:  
[Underwater Single Image Color Restoration — Haifa / Treibitz lab](https://csms.haifa.ac.il/profiles/tTreibitz/datasets/ambient_forwardlooking/index.html).  
Код метода **haze-lines** из статьи Berman et al. (TPAMI 2020): [github.com/danaberman/underwater-hl](https://github.com/danaberman/underwater-hl).

## Зачем вообще «пары» в нашем `train.py` — и чем это НЕ похоже на статью

В статье описан **алгоритм восстановления одного кадра** (оценка типа воды, haze-lines, библиотека спектральных профилей и т.д.) и датасет для **количественной оценки** (стерео, **карты расстояний**, цветовые таблицы в кадре). Там **нет** готовой папки «дешёвый снимок → эталонный JPEG для обучения U-Net».

Наш **`train.py`** — это **обычное обучение с учителем**: сеть учится минимизировать расхождение с **целевым изображением** `target`. Без пары `(input, target)` такой U-Net **не к чему** привязать градиенты.

Скрипт **`prepare_israel_stereo_dataset.py`** поэтому **вынужденно** строит **псевдо-пару**:

- `input` — ваш реальный кадр из SQUID (TIF/MAT);
- `target` — результат **нашего классического пайплайна** `inference.process(..., use_ai=False)` с глубиной из карт расстояний.

Это **не метод из статьи** и **не ground truth** авторов — это лишь способ «завести» текущую архитектуру обучения на их данных. Потолок качества ограничен классикой.

**Если цель — «как в статье»:**

1. Запускать **их реализацию** ([underwater-hl](https://github.com/danaberman/underwater-hl)) для инференса/экспериментов, а SQUID использовать по [инструкции на сайте](https://csms.haifa.ac.il/profiles/tTreibitz/datasets/ambient_forwardlooking/index.html) (оценка, цитирование).
2. Либо менять **постановку обучения** в DiveHub: без пар (GAN / циклы), с физикой рассеяния, с loss по цветовым патчам на чартах и т.д. — это отдельная разработка, не текущий `train.py`.

---

Описание из публикации: **57 стереопар**, четыре сайта — два в **Красном море** (тропики), два в **Средиземном** (умеренная вода).

| Сайт | Папка (пример) | Пар | Глубина | Среда |
|------|----------------|-----|---------|--------|
| Katzaa (коралл) | `Katzaa` | 15 | ~10–15 m | риф |
| Satil (транспорт) | `Satil` | 8 | ~20–30 m | затонувший корабль |
| Nachsholim | `Nachsholim` | 13 | ~3–6 m | каменистый риф |
| Michmoret | `Michmoret` | 21 | ~10–12 m | каменистый риф |

В каждой паре `image_set_XX`:

- **NEF** — RAW Nikon (скрипт пока не читает; при необходимости добавьте `rawpy`).
- **`LFT_*resizedUndistort.tif` / `RGT_*`** — 16-bit RGB после undistort (предпочтительно для левой камеры).
- **`distanceFromCamera.mat`** — `dist_map_l` / `dist_map_r`, метры до сцены (где есть файл).
- **`xyzPoints.mat`** — `imgLeftUndistorted`, `xyzPointsLeft` (и симметрично right); у **Nachsholim** часто только этот файл (без `distanceFromCamera.mat` и без `*resizedUndistort.tif`).
- **`stereoParams0.5.mat`** в корне сайта — калибровка (для текущего пайплайна обучения не обязательна).

## Подготовка пар для `train.py`

Скрипт **`prepare_israel_stereo_dataset.py`**:

1. Берёт **левую** камеру по умолчанию (`--side right` для правой).
2. Строит **input**: JPEG (после тонмаппинга 16-bit TIF или из `img*Undistorted` в MAT).
3. Строит **target**: прогон **`inference.process(..., use_ai=False)`** с оценкой **глубины** по медиане `dist_map_*` или по **Z** из `xyzPoints*`, иначе типичная глубина сайта.

Это **псевдо-эталон** (классика), а не ручная ретушь — потолок качества ограничен, но данные **реальные** и с **разными водами/глубинами**.

```bash
cd backend/ai-service/train
pip install scipy tifffile tqdm   # или pip install -r requirements-train.txt
python prepare_israel_stereo_dataset.py \
  --out ./data_israel \
  --roots ~/Downloads/Satil ~/Downloads/Katzaa ~/Downloads/Nachsholim ~/Downloads/Michmoret
```

Появятся `data_israel/input/*.jpg`, `data_israel/target/*.jpg`, `manifest_israel_stereo.json` (источник RGB, оценка глубины).

Повреждённые `.mat` пропускаются с предупреждением; глубина тогда из таблицы сайтов.

## Обучение

```bash
python train.py --data_dir ./data_israel --epochs 100 --batch_size 4 --export ../models/underwater.onnx
```

## Дальнейшие улучшения

- Подмешать **вторую камеру** (`--side right`) — удвоить выборку.
- Экспорт **карты глубины** как 4-й канал или условие (отдельная доработка модели).
- Декод **NEF** через `rawpy` для input «как с камеры».
