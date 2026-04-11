"""
Пары изображений: data/input/* ↔ data/target/* (одинаковые имена файлов).
"""
from __future__ import annotations

import random
from pathlib import Path

import cv2
import numpy as np
import torch
from torch.utils.data import Dataset


class PairedUnderwaterDataset(Dataset):
    def __init__(
        self,
        data_dir: Path,
        size: int = 256,
        augment: bool = True,
    ):
        self.input_dir = data_dir / "input"
        self.target_dir = data_dir / "target"
        self.size = size
        self.augment = augment
        exts = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}
        self.pairs: list[tuple[Path, Path]] = []
        if not self.input_dir.is_dir() or not self.target_dir.is_dir():
            return
        for p in sorted(self.input_dir.iterdir()):
            if p.suffix.lower() not in exts:
                continue
            t = self.target_dir / p.name
            if t.is_file():
                self.pairs.append((p, t))

    def __len__(self):
        return len(self.pairs)

    def _load_bgr(self, path: Path) -> np.ndarray:
        bgr = cv2.imread(str(path), cv2.IMREAD_COLOR)
        if bgr is None:
            raise ValueError(f"Cannot read {path}")
        return bgr

    def __getitem__(self, idx: int):
        ip, tp = self.pairs[idx]
        inp = self._load_bgr(ip)
        tgt = self._load_bgr(tp)
        h, w = inp.shape[:2]
        if (h, w) != tgt.shape[:2]:
            tgt = cv2.resize(tgt, (w, h), interpolation=cv2.INTER_AREA)

        if self.augment:
            if random.random() < 0.5:
                inp = cv2.flip(inp, 1)
                tgt = cv2.flip(tgt, 1)
            if random.random() < 0.3:
                k = random.uniform(0.85, 1.15)
                inp = np.clip(inp.astype(np.float32) * k, 0, 255).astype(np.uint8)

        inp = cv2.resize(inp, (self.size, self.size), interpolation=cv2.INTER_AREA)
        tgt = cv2.resize(tgt, (self.size, self.size), interpolation=cv2.INTER_AREA)

        inp_rgb = cv2.cvtColor(inp, cv2.COLOR_BGR2RGB).astype(np.float32) / 255.0
        tgt_rgb = cv2.cvtColor(tgt, cv2.COLOR_BGR2RGB).astype(np.float32) / 255.0

        xi = torch.from_numpy(inp_rgb).permute(2, 0, 1)
        xt = torch.from_numpy(tgt_rgb).permute(2, 0, 1)
        return xi, xt
