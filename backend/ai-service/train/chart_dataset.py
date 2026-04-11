"""
Одно изображение + разметка цветовых табличек (без пары target-кадра).

Структура каталога:
  data_dir/
    images/   — фото (jpg/png/…)
    labels/   — для каждого кадра JSON с тем же именем файла (stem)

Формат labels/<stem>.json:
{
  "patches": [
    {
      "bbox_norm": [x0, y0, x1, y1],
      "reference_rgb": [r, g, b]
    }
  ]
}

bbox_norm — доли от ширины/высоты исходного кадра: лево, верх, право, низ в [0, 1].
reference_rgb — эталонные значения **после** коррекции (как вы хотите видеть табличку),
  в диапазоне 0..1 (линейный RGB как в обучении пар; при необходимости sRGB ≈ то же).
"""
from __future__ import annotations

import json
from pathlib import Path

import cv2
import numpy as np
import torch
from torch.utils.data import Dataset


class ChartUnderwaterDataset(Dataset):
    def __init__(self, data_dir: Path, size: int = 256, augment: bool = False):
        self.images_dir = Path(data_dir) / "images"
        self.labels_dir = Path(data_dir) / "labels"
        self.size = int(size)
        self.augment = augment
        exts = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}
        self.items: list[tuple[Path, Path]] = []
        if not self.images_dir.is_dir() or not self.labels_dir.is_dir():
            return
        for p in sorted(self.images_dir.iterdir()):
            if p.suffix.lower() not in exts:
                continue
            lab = self.labels_dir / f"{p.stem}.json"
            if not lab.is_file():
                continue
            self.items.append((p, lab))

    def __len__(self) -> int:
        return len(self.items)

    @staticmethod
    def _load_patches(lab_path: Path) -> list[tuple[np.ndarray, np.ndarray]]:
        """Returns list of (bbox_norm float32 [4], ref_rgb float32 [3])."""
        raw = json.loads(lab_path.read_text(encoding="utf-8"))
        patches = raw.get("patches") or raw.get("charts")
        if not patches:
            return []
        out: list[tuple[np.ndarray, np.ndarray]] = []
        for ent in patches:
            if "bbox_norm" in ent:
                box = np.array(ent["bbox_norm"], dtype=np.float32).reshape(4)
            else:
                box = np.array(
                    [ent["x0"], ent["y0"], ent["x1"], ent["y1"]],
                    dtype=np.float32,
                ).reshape(4)
            ref = ent.get("reference_rgb") or ent.get("rgb")
            if ref is None:
                continue
            ref = np.array(ref, dtype=np.float32).reshape(3)
            if ref.max() > 1.5:
                ref = ref / 255.0
            ref = np.clip(ref, 0.0, 1.0)
            out.append((box, ref))
        return out

    def __getitem__(self, idx: int):
        import random

        ip, lp = self.items[idx]
        bgr = cv2.imread(str(ip), cv2.IMREAD_COLOR)
        if bgr is None:
            raise ValueError(f"Cannot read {ip}")
        patches = self._load_patches(lp)
        if not patches:
            raise ValueError(f"No patches in {lp}")

        if self.augment and random.random() < 0.5:
            bgr = cv2.flip(bgr, 1)
            for i, (box, ref) in enumerate(patches):
                x0, y0, x1, y1 = box
                nx0 = 1.0 - x1
                nx1 = 1.0 - x0
                patches[i] = (np.array([nx0, y0, nx1, y1], dtype=np.float32), ref)

        bgr = cv2.resize(bgr, (self.size, self.size), interpolation=cv2.INTER_AREA)
        rgb = cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB).astype(np.float32) / 255.0
        x = torch.from_numpy(rgb).permute(2, 0, 1)

        boxes_px = []
        refs = []
        for box, ref in patches:
            x0, y0, x1, y1 = [float(t) for t in box]
            x0 = int(np.clip(x0 * self.size, 0, self.size - 1))
            y0 = int(np.clip(y0 * self.size, 0, self.size - 1))
            x1 = int(np.clip(x1 * self.size, x0 + 1, self.size))
            y1 = int(np.clip(y1 * self.size, y0 + 1, self.size))
            if x1 <= x0 or y1 <= y0:
                continue
            boxes_px.append([x0, y0, x1, y1])
            refs.append(ref)
        if not boxes_px:
            raise ValueError(f"All invalid bboxes for {ip}")

        boxes_t = torch.tensor(boxes_px, dtype=torch.long)
        refs_t = torch.tensor(np.stack(refs, axis=0), dtype=torch.float32)
        return x, boxes_t, refs_t
