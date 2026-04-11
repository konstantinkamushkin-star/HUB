"""Препроцессинг для инференса — совпадает с ManifestPhotoDataset."""
from __future__ import annotations

import cv2
import numpy as np
import torch


def bgr_to_model_input(bgr: np.ndarray, size: int, device: torch.device) -> tuple[torch.Tensor, tuple[int, int]]:
    """
    bgr: uint8 BGR, любой размер.
    Возвращает батч [1, 4, size, size] и (orig_h, orig_w) для апскейла результата.
    """
    orig_h, orig_w = bgr.shape[:2]
    g = cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY).astype(np.float32) / 255.0
    depth_proxy = cv2.GaussianBlur(1.0 - g, (11, 11), 0)

    small = cv2.resize(bgr, (size, size), interpolation=cv2.INTER_LINEAR)
    depth_proxy = cv2.resize(depth_proxy, (size, size), interpolation=cv2.INTER_LINEAR)

    rgb = cv2.cvtColor(small, cv2.COLOR_BGR2RGB).astype(np.float32) / 255.0
    x = np.concatenate([rgb, depth_proxy[..., None]], axis=2)
    t = torch.from_numpy(x).permute(2, 0, 1).float().unsqueeze(0).to(device)
    return t, (orig_h, orig_w)


def tensor_to_bgr(out: torch.Tensor, orig_hw: tuple[int, int] | None = None) -> np.ndarray:
    """out: [1,3,H,W] 0..1 -> BGR uint8."""
    x = out.detach().cpu().clamp(0, 1).squeeze(0).permute(1, 2, 0).numpy()
    x = (x * 255).astype(np.uint8)
    bgr = cv2.cvtColor(x, cv2.COLOR_RGB2BGR)
    if orig_hw is not None:
        h, w = orig_hw
        bgr = cv2.resize(bgr, (w, h), interpolation=cv2.INTER_LINEAR)
    return bgr
