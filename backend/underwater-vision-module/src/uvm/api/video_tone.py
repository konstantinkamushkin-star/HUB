"""Пост-обработка яркости для экспорта underwater video — иначе MP4 часто визуально тёмнее исходника."""
from __future__ import annotations

import cv2
import numpy as np


def post_lift_underwater_video_bgr(bgr: np.ndarray, *, engine: str | None = None, amount: float = 1.0) -> np.ndarray:
    """
    amount: 0 = выкл., 1.0 = по умолчанию. Sea-Thru обычно уже светлее — ослабляем коэффициент.
    """
    if amount <= 0 or bgr is None or bgr.size == 0:
        return bgr
    amt = float(min(max(amount, 0.0), 1.75))
    eng = (engine or "").strip().lower()
    if eng == "seathru":
        amt *= 0.62
    lab = cv2.cvtColor(bgr, cv2.COLOR_BGR2LAB)
    l_ch, a_ch, b_ch = cv2.split(lab)
    clip = float(1.75 + 0.95 * amt)
    clahe = cv2.createCLAHE(clipLimit=clip, tileGridSize=(8, 8))
    l2 = clahe.apply(l_ch)
    bias = int(round(7 + 9 * amt))
    l2 = cv2.add(l2, bias)
    l2 = np.clip(l2, 0, 255).astype(np.uint8)
    merged = cv2.merge((l2, a_ch, b_ch))
    return cv2.cvtColor(merged, cv2.COLOR_LAB2BGR)


def downscale_bgr_for_process(bgr: np.ndarray, max_side: int) -> tuple[np.ndarray, tuple[int, int]]:
    """Обработка на уменьшенном кадре — сильно быстрее; возвращает (work, (orig_w, orig_h))."""
    h, w = bgr.shape[:2]
    if max_side <= 0:
        return bgr, (w, h)
    m = max(h, w)
    if m <= max_side:
        return bgr, (w, h)
    scale = max_side / float(m)
    nw, nh = int(round(w * scale)), int(round(h * scale))
    work = cv2.resize(bgr, (nw, nh), interpolation=cv2.INTER_AREA)
    return work, (w, h)


def upscale_bgr_to_original(bgr: np.ndarray, orig_wh: tuple[int, int]) -> np.ndarray:
    tw, th = orig_wh
    h, w = bgr.shape[:2]
    if w == tw and h == th:
        return bgr
    return cv2.resize(bgr, (tw, th), interpolation=cv2.INTER_LINEAR)
