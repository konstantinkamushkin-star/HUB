# DiveHub Underwater AI (`ai-service`)

Лёгкий FastAPI-сервис:

- **`POST /process`** — legacy `multipart` → `inference.process` (JPEG bytes).
- **`POST /v1/process/photo/{engine}`** — контракт как у UVM: `multipart` поле **`image`**, ответ JSON с **`image_jpeg_base64`** (hex JPEG). Движки `ai1` \| `ai2` \| `cursor` \| `seathru` — один порт **[Nikolaj Bech underwater color correction](https://github.com/nikolajbech/underwater-image-color-correction)** из соседнего пакета `underwater-vision-module` (нужен каталог `../underwater-vision-module/src` на `PYTHONPATH`).
- **`POST /v1/process/video/{engine}`** — multipart **`video`**, ответ MP4; покадрово тот же алгоритм.

## Зависимости

См. `requirements.txt`. Для `/v1/process/*` нужны **OpenCV + NumPy + Pillow** и исходники UVM рядом с `ai-service`, как описано выше.
