#!/usr/bin/env python3
"""
Тест обученной модели на одном фото.

Пример:
  cd backend/underwater-vision-module && source .venv/bin/activate
  PYTHONPATH=src python3 scripts/infer_photo.py \\
    --image "/Users/admin/Downloads/Katzaa/image_set_01/LFT_4007resizedUndistort.tif" \\
    --ckpt ./checkpoints_smoke/best.pt \\
    --output ./out_test.jpg \\
    --input_size 512

Если обучали с другим --input_size, укажи тот же размер здесь.
"""
from __future__ import annotations

import argparse
from pathlib import Path

import cv2
import torch

from uvm.train.infer_utils import bgr_to_model_input, tensor_to_bgr
from uvm.train.model import DepthAwareUNet


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--image", required=True, help="Путь к фото (jpg/png/tif)")
    ap.add_argument("--ckpt", required=True, help="best.pt или last.pt")
    ap.add_argument("--output", default="./infer_out.jpg")
    ap.add_argument("--input_size", type=int, default=512)
    ap.add_argument("--device", default=None, help="cpu | cuda | mps")
    ap.add_argument(
        "--match_original",
        action="store_true",
        help="Апскейл результата до исходного разрешения входа",
    )
    args = ap.parse_args()

    img_path = Path(args.image)
    if not img_path.is_file():
        raise SystemExit(f"Файл не найден: {img_path}")

    ckpt_path = Path(args.ckpt)
    if not ckpt_path.is_file():
        raise SystemExit(f"Чекпоинт не найден: {ckpt_path}")

    dev = torch.device(
        args.device
        or (
            "mps"
            if torch.backends.mps.is_available()
            else ("cuda" if torch.cuda.is_available() else "cpu")
        )
    )

    bgr = cv2.imread(str(img_path), cv2.IMREAD_COLOR)
    if bgr is None:
        raise SystemExit("Не удалось прочитать изображение (cv2.imread)")

    x, orig_hw = bgr_to_model_input(bgr, args.input_size, dev)

    model = DepthAwareUNet(in_ch=4, out_ch=3).to(dev)
    try:
        ckpt = torch.load(ckpt_path, map_location=dev, weights_only=False)
    except TypeError:
        ckpt = torch.load(ckpt_path, map_location=dev)
    model.load_state_dict(ckpt["model"])
    model.eval()

    with torch.no_grad():
        out = model(x)

    out_bgr = tensor_to_bgr(out, orig_hw if args.match_original else None)
    out_path = Path(args.output)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    cv2.imwrite(str(out_path), out_bgr, [cv2.IMWRITE_JPEG_QUALITY, 95])
    vl = ckpt.get("val_loss")
    extra = f" epoch={ckpt.get('epoch', '?')} val_loss={vl:.4f}" if isinstance(vl, float) else ""
    print(f"Сохранено: {out_path.resolve()}")
    print(f"device={dev} input_size={args.input_size}{extra}")


if __name__ == "__main__":
    main()
