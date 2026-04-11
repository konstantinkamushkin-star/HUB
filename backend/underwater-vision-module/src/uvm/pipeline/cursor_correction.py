"""
Алгоритм кнопки «Cursor» в приложении: восстановление подводного кадра (dehaze, баланс, контраст).

Цель — приблизить «сырое/зелёно-голубое» к естественным цветам без монохромного янтаря
и без сильного выжигания теней. Не копирует сторонние приложения.
"""
from __future__ import annotations

from typing import Any

import cv2
import numpy as np


def _percentile_gray_world(bgr_f: np.ndarray, s: float, percentile: float = 85.0) -> np.ndarray:
    """Среднее по ярким пикселям; корректный знаменатель на канал."""
    g = cv2.cvtColor((np.clip(bgr_f, 0, 1) * 255).astype(np.uint8), cv2.COLOR_BGR2GRAY)
    thr = float(np.percentile(g, percentile))
    mask = (g.astype(np.float32) >= max(thr - 12.0, 0.0)).astype(np.float32)
    if mask.sum() < 400:
        mask = np.ones_like(mask, dtype=np.float32)
    denom = float(mask.sum()) + 1e-6
    means = []
    for c in range(3):
        means.append(float((bgr_f[:, :, c] * mask).sum() / denom))
    m = np.array(means, dtype=np.float64) + 1e-6
    gavg = float(m.mean())
    gains = gavg / m
    gains[2] *= 1.0 + 0.38 * s
    gains[0] *= max(0.86 - 0.10 * s, 0.68)
    gains[1] *= 1.0 + 0.05 * s
    gains = np.clip(gains, 0.52, 2.45)
    return np.clip(bgr_f * gains.reshape(1, 1, 3), 0, 1)


def _remove_blue_cast(bgr_f: np.ndarray, s: float) -> np.ndarray:
    """Если B доминирует над R (типично под водой) — усиливаем R, режем B."""
    b, g, r = bgr_f[:, :, 0], bgr_f[:, :, 1], bgr_f[:, :, 2]
    rb = (r + 1e-4) / (b + 1e-4)
    # где сильный синий каст
    cast = np.clip(1.15 - rb, 0, 0.85) ** 0.9
    k = (0.42 + 0.50 * s) * cast
    r2 = np.clip(r * (1.0 + k * 1.15), 0, 1)
    b2 = np.clip(b * (1.0 - k * 0.38), 0, 1)
    return np.stack([b2, g, r2], axis=-1)


def _dual_scale_dehaze(bgr_f: np.ndarray, s: float) -> np.ndarray:
    gray = cv2.cvtColor((bgr_f * 255).astype(np.uint8), cv2.COLOR_BGR2GRAY).astype(np.float32) / 255.0
    h, w = gray.shape
    sig1 = max(h, w) * 0.02
    sig2 = max(h, w) * 0.007
    b1 = cv2.GaussianBlur(gray, (0, 0), sigmaX=sig1)
    b2 = cv2.GaussianBlur(gray, (0, 0), sigmaX=sig2)
    fog = np.clip(1.0 - 0.5 * b1 - 0.38 * b2, 0.0, 1.0) ** 1.2
    t = np.clip(1.0 - 0.72 * s * fog, 0.18, 1.0)[..., None]
    return np.clip(bgr_f / t, 0, 1)


def _tropical_teal_push(bgr_f: np.ndarray, s: float) -> np.ndarray:
    b, g, r = cv2.split((np.clip(bgr_f, 0, 1) * 255).astype(np.uint8))
    bf, gf, rf = b.astype(np.float32), g.astype(np.float32), r.astype(np.float32)
    lum = 0.299 * rf + 0.587 * gf + 0.114 * bf
    water_w = np.clip((lum / 255.0 - 0.22) / 0.58, 0, 1) ** 1.15
    water_w = water_w * (1.0 - np.clip((rf - 35) / 100.0, 0, 1))
    k = 0.1 * s * water_w
    gf = np.clip(gf * (1.0 + k), 0, 255)
    bf = np.clip(bf * (1.0 - 0.55 * k), 0, 255)
    return cv2.merge([bf.astype(np.uint8), gf.astype(np.uint8), rf.astype(np.uint8)]).astype(np.float32) / 255.0


def _lab_clarity_stack(bgr_f: np.ndarray, s: float) -> np.ndarray:
    lab = cv2.cvtColor((np.clip(bgr_f, 0, 1) * 255).astype(np.uint8), cv2.COLOR_BGR2LAB)
    l_ch, a_ch, b_ch = cv2.split(lab)
    lf = l_ch.astype(np.float32)
    t = np.power(np.clip(lf / 255.0, 0, 1), 0.84 - 0.08 * s)
    lf = np.clip(t * 255.0, 0, 255)
    l_u8 = lf.astype(np.uint8)
    c1 = float(np.clip((2.2 + 2.4 * s) * 0.88, 1.9, 4.2))
    c2 = float(np.clip((1.35 + 1.15 * s) * 0.88, 1.15, 2.9))
    c3 = float(np.clip((1.0 + 0.85 * s) * 0.88, 0.95, 2.0))
    l_u8 = cv2.createCLAHE(clipLimit=c1, tileGridSize=(8, 8)).apply(l_u8)
    l_u8 = cv2.createCLAHE(clipLimit=c2, tileGridSize=(16, 16)).apply(l_u8)
    l_u8 = cv2.createCLAHE(clipLimit=c3, tileGridSize=(32, 32)).apply(l_u8)
    # Меньше сдвиг a/b — иначе весь кадр уходит в жёлто-оранжевый «фильтр»
    a_f = a_ch.astype(np.float32) + (5.0 + 7.0 * s)
    b_f = b_ch.astype(np.float32) + (3.5 + 6.0 * s)
    a_u8 = np.clip(a_f, 0, 255).astype(np.uint8)
    b_u8 = np.clip(b_f, 0, 255).astype(np.uint8)
    return cv2.cvtColor(cv2.merge([l_u8, a_u8, b_u8]), cv2.COLOR_LAB2BGR).astype(np.float32) / 255.0


def _vibrance_hsv(bgr_f: np.ndarray, s: float) -> np.ndarray:
    u8 = (np.clip(bgr_f, 0, 1) * 255).astype(np.uint8)
    hsv = cv2.cvtColor(u8, cv2.COLOR_BGR2HSV).astype(np.float32)
    sat = hsv[:, :, 1]
    headroom = (255.0 - sat) / 255.0
    sat = np.clip(sat * (1.0 + 0.22 * s) + headroom * (38.0 * s), 0, 255)
    hsv[:, :, 1] = sat
    hsv[:, :, 2] = np.clip(hsv[:, :, 2] * (1.0 + 0.05 * s), 0, 255)
    return cv2.cvtColor(hsv.astype(np.uint8), cv2.COLOR_HSV2BGR).astype(np.float32) / 255.0


def _shadow_lift_masked(bgr_f: np.ndarray, s: float) -> np.ndarray:
    lab = cv2.cvtColor((np.clip(bgr_f, 0, 1) * 255).astype(np.uint8), cv2.COLOR_BGR2LAB)
    l_ch, a_ch, b_ch = cv2.split(lab)
    lf = l_ch.astype(np.float32)
    shadow = np.clip(1.0 - lf / 110.0, 0, 1) ** 1.45
    l_ch = np.clip(lf + 12.0 * s * shadow, 0, 255).astype(np.uint8)
    return cv2.cvtColor(cv2.merge([l_ch, a_ch, b_ch]), cv2.COLOR_LAB2BGR).astype(np.float32) / 255.0


def _unsharp_lab(bgr_f: np.ndarray, s: float) -> np.ndarray:
    lab = cv2.cvtColor((np.clip(bgr_f, 0, 1) * 255).astype(np.uint8), cv2.COLOR_BGR2LAB)
    l2, a2, b2 = cv2.split(lab)
    blur_l = cv2.GaussianBlur(l2, (0, 0), sigmaX=1.0)
    amt = 0.48 * s
    l2 = np.clip(l2.astype(np.float32) + (l2.astype(np.float32) - blur_l.astype(np.float32)) * amt, 0, 255).astype(np.uint8)
    return cv2.cvtColor(cv2.merge([l2, a2, b2]), cv2.COLOR_LAB2BGR).astype(np.float32) / 255.0


def _soft_knee_highlights_fixed(proc_f: np.ndarray, s: float) -> np.ndarray:
    g = cv2.cvtColor((np.clip(proc_f, 0, 1) * 255).astype(np.uint8), cv2.COLOR_BGR2GRAY).astype(np.float32) / 255.0
    thr = float(np.percentile(g, 97.0))
    hi = np.clip((g - thr) / max(1e-3, 1.0 - thr), 0, 1) ** 1.5
    w = (0.18 + 0.12 * s) * hi
    w3 = w[..., None]
    return np.clip(proc_f * (1.0 - 0.2 * w3), 0, 1)


def run_cursor_underwater_correct(
    bgr: np.ndarray,
    strength: float = 0.75,
    depth_hint_m: float | None = None,
) -> tuple[np.ndarray, dict[str, Any]]:
    s = float(np.clip(strength, 0.25, 1.0))
    orig = bgr.astype(np.float32) / 255.0
    img = orig.copy()

    img = _dual_scale_dehaze(img, s)
    img = _remove_blue_cast(img, s)
    img = _percentile_gray_world(img, s)

    red_boost = 1.0 + 0.32 * s
    if depth_hint_m is not None:
        red_boost += min(float(depth_hint_m) / 28.0, 1.0) * 0.32 * s
    gains = np.ones(3, dtype=np.float64)
    gains[2] = float(red_boost)
    img = np.clip(img * gains.reshape(1, 1, 3), 0, 1)

    img = _tropical_teal_push(img, s)
    img = _lab_clarity_stack(img, s)
    img = _vibrance_hsv(img, s)
    img = _shadow_lift_masked(img, s)
    img = _unsharp_lab(img, s)
    img = _soft_knee_highlights_fixed(img, s)  # без подмешивания исходника

    # Больше исходника в миксе — меньше «пережаренный» контраст и монохром
    wmix = 0.74 + 0.18 * s
    out = np.clip(orig * (1.0 - wmix) + img * wmix, 0, 1)
    out_u8 = (out * 255).astype(np.uint8)

    report = {
        'engine': 'cursor',
        'strength': s,
        'depth_hint_m': depth_hint_m,
        'red_boost_applied': float(red_boost),
        'mix_weight': float(wmix),
        'preset': 'tropical_v4_natural',
    }
    return out_u8, report
