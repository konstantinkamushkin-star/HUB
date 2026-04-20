"""
Port of https://github.com/nikolajbech/underwater-image-color-correction (index.js).

Pixels are RGBA uint8, row-major, same layout as the JS `pixels` array:
index = y * (width * 4) + x * 4 + c, c in 0..3 for R,G,B,A.
"""

from __future__ import annotations

import math
from typing import Any

import numpy as np


def _calculate_average_color(pixels: np.ndarray, width: int, height: int) -> tuple[float, float, float]:
    """JS calculateAverageColor — sums raw channel bytes, divides by width*height."""
    n = width * height
    flat = pixels.reshape(-1, 4)
    avg_r = float(flat[:, 0].sum()) / n
    avg_g = float(flat[:, 1].sum()) / n
    avg_b = float(flat[:, 2].sum()) / n
    return avg_r, avg_g, avg_b


def _hue_shift_red(r: float, g: float, b: float, h: int) -> tuple[float, float, float]:
    """JS hueShiftRed (h in integer degrees, stepped in caller)."""
    u = math.cos(h * math.pi / 180.0)
    w = math.sin(h * math.pi / 180.0)
    r_out = (0.299 + 0.701 * u + 0.168 * w) * r
    g_out = (0.587 - 0.587 * u + 0.330 * w) * g
    b_out = (0.114 - 0.114 * u - 0.497 * w) * b
    return r_out, g_out, b_out


def _normalizing_interval(norm_array: list[int]) -> tuple[int, int]:
    """JS normalizingInterval — norm_array is list of threshold indices including 0 and 255 at ends."""
    high = 255
    low = 0
    max_dist = 0
    for i in range(1, len(norm_array)):
        dist = norm_array[i] - norm_array[i - 1]
        if dist > max_dist:
            max_dist = dist
            high = norm_array[i]
            low = norm_array[i - 1]
    return low, high


def get_color_filter_matrix_rgba(pixels: np.ndarray, width: int, height: int) -> tuple[list[float], int]:
    """JS `getColorFilterMatrix`: same 20 coefficients + final hue shift (degrees steps)."""
    pixels = np.asarray(pixels, dtype=np.uint8)
    if pixels.size != width * height * 4:
        raise ValueError('pixels must have length width*height*4')

    num_of_pixels = width * height
    threshold_ratio = 2000
    threshold_level = num_of_pixels / threshold_ratio
    min_avg_red = 60
    max_hue_shift = 120
    blue_magic_value = 1.2

    hist_r = [0] * 256
    hist_g = [0] * 256
    hist_b = [0] * 256
    normalize_r: list[int] = []
    normalize_g: list[int] = []
    normalize_b: list[int] = []
    hue_shift = 0

    avg_r, avg_g, avg_b = _calculate_average_color(pixels, width, height)

    new_avg_red = avg_r
    while new_avg_red < min_avg_red:
        shifted = _hue_shift_red(avg_r, avg_g, avg_b, hue_shift)
        new_avg_red = shifted[0] + shifted[1] + shifted[2]
        hue_shift += 1
        if hue_shift > max_hue_shift:
            new_avg_red = 60.0
            break

    flat = pixels.reshape(-1, 4)
    for i in range(flat.shape[0]):
        red = int(round(float(flat[i, 0])))
        green = int(round(float(flat[i, 1])))
        blue = int(round(float(flat[i, 2])))
        shifted = _hue_shift_red(float(red), float(green), float(blue), hue_shift)
        red = shifted[0] + shifted[1] + shifted[2]
        red = min(255.0, max(0.0, red))
        red = int(round(red))
        hist_r[red] += 1
        hist_g[green] += 1
        hist_b[blue] += 1

    normalize_r.append(0)
    normalize_g.append(0)
    normalize_b.append(0)

    for i in range(256):
        if hist_r[i] - threshold_level < 2:
            normalize_r.append(i)
        if hist_g[i] - threshold_level < 2:
            normalize_g.append(i)
        if hist_b[i] - threshold_level < 2:
            normalize_b.append(i)

    normalize_r.append(255)
    normalize_g.append(255)
    normalize_b.append(255)

    adjust_r = _normalizing_interval(normalize_r)
    adjust_g = _normalizing_interval(normalize_g)
    adjust_b = _normalizing_interval(normalize_b)

    shifted_one = _hue_shift_red(1.0, 1.0, 1.0, hue_shift)

    span_r = max(1, adjust_r[1] - adjust_r[0])
    span_g = max(1, adjust_g[1] - adjust_g[0])
    span_b = max(1, adjust_b[1] - adjust_b[0])
    red_gain = 256.0 / span_r
    green_gain = 256.0 / span_g
    blue_gain = 256.0 / span_b

    red_offset = (-adjust_r[0] / 256.0) * red_gain
    green_offset = (-adjust_g[0] / 256.0) * green_gain
    blue_offset = (-adjust_b[0] / 256.0) * blue_gain

    adjst_red = shifted_one[0] * red_gain
    adjst_red_green = shifted_one[1] * red_gain
    adjst_red_blue = shifted_one[2] * red_gain * blue_magic_value

    matrix = [
        adjst_red,
        adjst_red_green,
        adjst_red_blue,
        0.0,
        red_offset,
        0.0,
        green_gain,
        0.0,
        0.0,
        green_offset,
        0.0,
        0.0,
        blue_gain,
        0.0,
        blue_offset,
        0.0,
        0.0,
        0.0,
        1.0,
        0.0,
    ]
    return matrix, hue_shift


def apply_color_filter_matrix_rgba_inplace(data: np.ndarray, flt: list[float]) -> None:
    """
    JS loop from README (mutates RGBA buffer).
    data: uint8 flat or (N,4), values 0-255.
    """
    data = np.asarray(data, dtype=np.float64)
    if data.ndim == 1:
        data = data.reshape(-1, 4)
    f = flt
    r = data[:, 0]
    g = data[:, 1]
    b = data[:, 2]
    data[:, 0] = np.minimum(255.0, np.maximum(0.0, r * f[0] + g * f[1] + b * f[2] + f[4] * 255.0))
    data[:, 1] = np.minimum(255.0, np.maximum(0.0, g * f[6] + f[9] * 255.0))
    data[:, 2] = np.minimum(255.0, np.maximum(0.0, b * f[12] + f[14] * 255.0))
    # alpha unchanged (JS does not touch i+3)


def process_bgr_uint8(bgr: np.ndarray, strength: float) -> tuple[np.ndarray, dict[str, Any]]:
    """
    Nikolaj Bech color matrix on RGBA (same as upstream `index.js` + README apply loop), then BGR out.

    Upstream (npm / GitHub) applies the matrix at 100% — there is no “strength” in `index.js`.
    Here `strength` is an optional **linear blend** toward the original (product extension):
    ``out = strength * corrected + (1 - strength) * original``, clamped to ``[0, 1]``.
    Use ``strength=1.0`` for byte-for-byte same correction as applying the README loop to all pixels.
    """
    if bgr.ndim != 3 or bgr.shape[2] != 3:
        raise ValueError('bgr must be HxWx3')
    orig = np.asarray(bgr, dtype=np.uint8)
    h, w = orig.shape[:2]
    rgb = orig[..., ::-1].copy()
    rgba = np.concatenate([rgb, np.full((h, w, 1), 255, dtype=np.uint8)], axis=-1)
    flat = rgba.reshape(-1, 4).astype(np.uint8, copy=False)

    flt, hue_shift_used = get_color_filter_matrix_rgba(flat, w, h)
    work = flat.astype(np.float64)
    apply_color_filter_matrix_rgba_inplace(work, flt)
    corrected_rgb = work[:, :3].reshape(h, w, 3)
    corrected_bgr = corrected_rgb[..., ::-1].astype(np.float64)

    s = float(min(1.0, max(0.0, strength)))
    out_f = s * corrected_bgr + (1.0 - s) * orig.astype(np.float64)
    out = np.clip(np.round(out_f), 0, 255).astype(np.uint8)

    report: dict[str, Any] = {
        'backend': 'nikolaj_bech_underwater_color_correction',
        'upstream': 'https://github.com/nikolajbech/underwater-image-color-correction',
        'strength': strength,
        'blend_strength': s,
        'hue_shift_deg': hue_shift_used,
    }
    return out, report
