#!/usr/bin/env bash
# Подготовка data_israel из ~/Downloads/... — с любого места.
set -euo pipefail
TRAIN="$(cd "$(dirname "$0")/ai-service/train" && pwd)"
AIS="$(cd "$(dirname "$0")/ai-service" && pwd)"
cd "$TRAIN"
if [[ ! -d "$AIS/.venv" ]]; then
  echo "Создаю $AIS/.venv..."
  python3 -m venv "$AIS/.venv"
fi
# shellcheck source=/dev/null
source "$AIS/.venv/bin/activate"
pip install -q scipy tifffile tqdm opencv-python-headless numpy Pillow onnxruntime fastapi uvicorn python-multipart
echo "Train dir: $TRAIN"
exec python3 prepare_israel_stereo_dataset.py \
  --out ./data_israel \
  --roots "$HOME/Downloads/Satil" "$HOME/Downloads/Katzaa" "$HOME/Downloads/Nachsholim" "$HOME/Downloads/Michmoret" \
  "$@"
