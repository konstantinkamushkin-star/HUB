"""
GPT-кнопка: подводное восстановление (ТЗ v2 — доработка архитектуры).

  — Порядок: UW → linear → A → t → refine → dehaze → маски → red → UW-WB → hue → S → L-контраст → denoise → blend → tone → sRGB
  — M_water: синий, низкая текстура/деталь, низкая S, низкая «тёплая» насыщенность, однородность
  — M_obj: текстура, границы, вариативность цвета, t (ближе план), не вода (не просто 1−M_w)
  — M_neutral: низкая S, светлые, близкие RGB-каналы, низкая хрома Lab
  — Dehaze усилен (ω↑); t_min 0.15–0.25
  — Red: R' = R·(1 + kR·(1−t)·M_obj); M_w>0.6 → k≈0; L>0.85 → гашение kR
  — UW-WB: только robust percentiles по нейтралям; per-channel пространственно; синий не поднимать в воде
  — Hue remap: зелёный/болотный → тёплый; циан→синий; вода |Δh|≤~10–15° (в ед. OpenCV)
  — S: S' = S·(1+k·(1−S)), приоритет тёплых оттенков на объекте; вода слабо
  — Контраст по L: сильнее на M_obj, вода гладкая; потолок локального усиления
"""
from __future__ import annotations

from dataclasses import dataclass

import cv2
import numpy as np


@dataclass(frozen=True)
class GptRestoreParams:
    strength: float = 1.0
    preserve_blues: float = 0.72
    detail_boost: float = 0.45
    warmth_bias: float = 0.5
    dehaze_strength: float = 0.55
    red_recovery_strength: float = 0.72
    noise_reduction: float = 0.45


def _clip01(x: np.ndarray) -> np.ndarray:
    return np.clip(x, 0.0, 1.0)


def bgr_to_rgb_linear(bgr: np.ndarray) -> np.ndarray:
    """§5: sRGB → linear RGB [0,1]. Поддержка uint8 и uint16 (PNG/HEIC)."""
    rgb = bgr[..., ::-1]
    if bgr.dtype == np.uint16:
        s = (rgb.astype(np.float32) / 65535.0).clip(0.0, 1.0)
    else:
        s = (rgb.astype(np.float32) / 255.0).clip(0.0, 1.0)
    return np.where(s <= 0.04045, s / 12.92, ((s + 0.055) / 1.055) ** 2.4)


def rgb_linear_to_srgb_u8(rgb_lin: np.ndarray) -> np.ndarray:
    x = _clip01(rgb_lin.astype(np.float32))
    s = np.where(x <= 0.0031308, 12.92 * x, 1.055 * (x ** (1.0 / 2.4)) - 0.055)
    return (s * 255.0 + 0.5).astype(np.uint8)


def rgb_u8_to_rgb_linear(rgb_u8: np.ndarray) -> np.ndarray:
    s = (rgb_u8.astype(np.float32) / 255.0).clip(0.0, 1.0)
    return np.where(s <= 0.04045, s / 12.92, ((s + 0.055) / 1.055) ** 2.4)


def rgb_u8_to_bgr_u8(rgb_u8: np.ndarray) -> np.ndarray:
    return rgb_u8[..., ::-1].copy()


def _rgb_to_luminance_linear(rgb_lin: np.ndarray) -> np.ndarray:
    return (
        0.2126 * rgb_lin[:, :, 0] + 0.7152 * rgb_lin[:, :, 1] + 0.0722 * rgb_lin[:, :, 2]
    ).astype(np.float32)


def _morph_min_gray(img: np.ndarray, patch: int = 15) -> np.ndarray:
    k = cv2.getStructuringElement(cv2.MORPH_RECT, (patch, patch))
    return cv2.erode(img.astype(np.float32), k)


def _texture_map_lum(lum: np.ndarray) -> np.ndarray:
    t = np.abs(cv2.Laplacian(lum, cv2.CV_32F, ksize=3))
    return t / (np.percentile(t, 98.0) + 1e-6)


# --- §4 подводная сцена -------------------------------------------------------


def is_underwater_scene(rgb_lin: np.ndarray) -> tuple[bool, float]:
    """
    §4.2: score = w1*(μB−μR) + w2*(μG−μR) + w3*pBlue − w4*pWarm.
    Дополнительно: низкая доля насыщенных красно-оранжевых оттенков усиливает скор.
    """
    h, w = rgb_lin.shape[:2]
    if h * w < 64:
        return False, 0.0
    m = rgb_lin.reshape(-1, 3).mean(axis=0)
    mu_r, mu_g, mu_b = float(m[0]), float(m[1]), float(m[2])
    srgb = rgb_linear_to_srgb_u8(rgb_lin)
    hsv = cv2.cvtColor(srgb, cv2.COLOR_RGB2HSV)
    hh = hsv[:, :, 0].astype(np.float32)
    ss = hsv[:, :, 1].astype(np.float32) / 255.0
    # тёплые hue OpenCV: 0–28 и 165–180
    warm = ((hh <= 28.0) | (hh >= 165.0)) & (ss > 0.12)
    p_warm = float(np.mean(warm))
    # синий/голубой фон
    blue_hue = (hh > 95.0) & (hh < 138.0) & (ss > 0.08)
    p_blue = float(np.mean(blue_hue))
    # красно-оранжевый с заметной S
    red_orange = ((hh < 22.0) | (hh > 168.0)) & (ss > 0.18)
    p_ro_sat = float(np.mean(red_orange))

    w1, w2, w3, w4, w5 = 2.8, 1.9, 1.15, 0.85, 0.9
    score = (
        w1 * (mu_b - mu_r)
        + w2 * (mu_g - mu_r)
        + w3 * p_blue
        - w4 * p_warm
        - w5 * p_ro_sat
    )
    cond = (mu_b > mu_r + 0.012) and (mu_g > mu_r - 0.01) and (p_blue > 0.06)
    threshold = 0.085
    is_uw = bool(cond and score > threshold)
    return is_uw, float(score)


def mild_enhancement_bgr(bgr: np.ndarray) -> np.ndarray:
    """§4.3: без red boost, без hue warming, без физической модели."""
    if bgr.dtype == np.uint16:
        rgb = rgb_linear_to_srgb_u8(bgr_to_rgb_linear(bgr))
    else:
        rgb = cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB)
    lab = cv2.cvtColor(rgb, cv2.COLOR_RGB2LAB)
    l, a, b_ch = cv2.split(lab)
    clahe = cv2.createCLAHE(clipLimit=1.25, tileGridSize=(16, 16))
    l2 = clahe.apply(l)
    out = cv2.cvtColor(cv2.merge([l2, a, b_ch]), cv2.COLOR_LAB2RGB)
    out = cv2.bilateralFilter(out, 5, 25.0, 25.0)
    return cv2.cvtColor(out, cv2.COLOR_RGB2BGR)


# --- §6 water light A ---------------------------------------------------------


def estimate_water_light_A(rgb_lin: np.ndarray) -> np.ndarray:
    """
    §6.1: dark channel → верхний процентиль по D → среди кандидатов высокая яркость,
    низкая текстура → усреднить цвет = A.
    """
    dc = np.min(rgb_lin, axis=2)
    dc_min = _morph_min_gray(dc, 15)
    h, w = dc_min.shape
    n = max(int(h * w * 0.001), 80)
    flat = dc_min.ravel()
    idx = np.argpartition(flat, -n)[-n:]
    lum = _rgb_to_luminance_linear(rgb_lin).ravel()[idx]
    tex_map = _texture_map_lum(_rgb_to_luminance_linear(rgb_lin))
    tex = tex_map.ravel()[idx]
    # предпочтение: яркие и гладкие (дымка/вода)
    merit = lum.astype(np.float32) - 0.42 * np.clip(tex, 0.0, 1.0)
    k = max(24, min(n // 4, 120))
    sub = np.argpartition(-merit, k - 1)[:k]
    pix = rgb_lin.reshape(-1, 3)[idx[sub]]
    A = np.maximum(pix.mean(axis=0).astype(np.float32), 1e-3)
    return np.clip(A, 1e-3, 0.99)


# --- §7 transmission (underwater DCP) -----------------------------------------


def _refine_transmission_fallback(t: np.ndarray, d: int = 9) -> np.ndarray:
    t8 = (np.clip(t, 0, 1) * 255.0).astype(np.uint8)
    return (cv2.bilateralFilter(t8, d, 45, 45).astype(np.float32) / 255.0).clip(0.05, 1.0)


def refine_transmission(t: np.ndarray, guide_lum: np.ndarray) -> np.ndarray:
    """§7.3: joint bilateral по яркости-гиду или bilateral по t."""
    t8 = (np.clip(t, 0, 1) * 255.0).astype(np.uint8)
    gmx = float(np.maximum(guide_lum.max(), 1e-6))
    g8 = (np.clip(guide_lum / gmx, 0, 1) * 255.0).astype(np.uint8)
    d = 9
    xp = getattr(cv2, "ximgproc", None)
    if xp is not None and hasattr(xp, "jointBilateralFilter"):
        try:
            t_ref = xp.jointBilateralFilter(g8, t8, d, 30.0, 9.0)
            return (t_ref.astype(np.float32) / 255.0).clip(0.05, 1.0)
        except cv2.error:
            pass
    return _refine_transmission_fallback(t, d)


def estimate_transmission_map(rgb_lin: np.ndarray, A: np.ndarray, omega: float) -> np.ndarray:
    """
    §7.1: D_u = min(αR·Ir/Ar, αG·Ig/Ag, αB·Ib/Ab), αR > αG ≥ αB; t = 1 − ω·D_u.
    """
    eps = 1e-6
    Ir, Ig, Ib = rgb_lin[:, :, 0], rgb_lin[:, :, 1], rgb_lin[:, :, 2]
    Ar, Ag, Ab = float(A[0]) + eps, float(A[1]) + eps, float(A[2]) + eps
    a_r, a_g, a_b = 1.38, 1.07, 1.0
    du = np.minimum(
        np.minimum(a_r * Ir / Ar, a_g * Ig / Ag),
        a_b * Ib / Ab,
    )
    du = _morph_min_gray(du, 15)
    t = 1.0 - float(np.clip(omega, 0.2, 0.98)) * du
    return np.clip(t, 0.06, 1.0).astype(np.float32)


def remove_haze_linear(rgb_lin: np.ndarray, A: np.ndarray, t: np.ndarray, t_min: float) -> np.ndarray:
    """§8.1–8.2: J = (I−A)/max(t,t_min) + A, clip."""
    A3 = A.reshape(1, 1, 3)
    t3 = np.maximum(t[..., None], float(t_min))
    j = (rgb_lin - A3) / t3 + A3
    return _clip01(j)


# --- §10 маски (после dehaze, на J) --------------------------------------------


def estimate_water_mask(
    rgb_lin: np.ndarray,
    t: np.ndarray,
    rgb_srgb_u8: np.ndarray,
) -> np.ndarray:
    """
    §4.1: вода — высокий B, низкая текстура и локальная деталь, низкая S,
    низкая доля тёплых насыщенных тонов, однородный фон.
    """
    r, g, b = rgb_lin[:, :, 0], rgb_lin[:, :, 1], rgb_lin[:, :, 2]
    eps = 1e-6
    b_high = np.clip((b - np.maximum(r, g)) / (b + r + g + eps), 0.0, 1.0)
    lum = _rgb_to_luminance_linear(rgb_lin)
    mu = cv2.GaussianBlur(lum, (0, 0), 7.0)
    var = cv2.GaussianBlur((lum - mu) ** 2, (0, 0), 7.0)
    loc_std = np.sqrt(np.maximum(var, 0.0))
    low_var = 1.0 - np.clip(loc_std / (np.percentile(loc_std, 97.0) + 1e-6), 0.0, 1.0)
    tex = _texture_map_lum(lum)
    low_tex = np.clip(1.0 - tex, 0.0, 1.0)
    hsv = cv2.cvtColor(rgb_srgb_u8, cv2.COLOR_RGB2HSV).astype(np.float32)
    hh, sm = hsv[:, :, 0], hsv[:, :, 1] / 255.0
    low_sat = np.clip(1.0 - sm * 1.2, 0.0, 1.0)
    warm_sat = (((hh <= 32.0) | (hh >= 162.0)) & (sm > 0.14)).astype(np.float32)
    low_warm_sat = 1.0 - cv2.GaussianBlur(warm_sat, (0, 0), 3.0)
    std_rgb = np.std(rgb_lin, axis=2)
    homog = 1.0 - np.clip(std_rgb / (np.percentile(std_rgb, 93.0) + 1e-6), 0.0, 1.0)
    far = np.clip(1.0 - t, 0.0, 1.0)
    w = (
        0.30 * b_high
        + 0.22 * low_var
        + 0.18 * low_tex
        + 0.14 * low_sat
        + 0.10 * low_warm_sat
        + 0.12 * homog
        + 0.08 * far
    )
    w = cv2.GaussianBlur(w.astype(np.float32), (0, 0), 3.0)
    return np.clip(w, 0.0, 1.0)


def estimate_object_mask(
    rgb_lin: np.ndarray,
    water_mask: np.ndarray,
    t: np.ndarray,
) -> np.ndarray:
    """
    §4.2: риф/передний план — высокая текстура, границы, вариативность цвета, структура;
    подавление в зоне воды.
    """
    lum = _rgb_to_luminance_linear(rgb_lin)
    tex = np.clip(_texture_map_lum(lum), 0.0, 1.0)
    lu8 = (np.clip(lum, 0.0, 1.0) * 255.0).astype(np.uint8)
    edges = cv2.Canny(lu8, 22, 72).astype(np.float32) / 255.0
    edges = cv2.GaussianBlur(edges, (0, 0), 1.4)
    ch_std = np.std(rgb_lin, axis=2)
    chv = np.clip(ch_std / (np.percentile(ch_std, 96.0) + 1e-6), 0.0, 1.0)
    near = np.clip(t, 0.0, 1.0)
    wm = np.clip(water_mask, 0.0, 1.0)
    score = 0.36 * tex + 0.26 * edges + 0.22 * chv + 0.22 * near
    score *= np.power(np.clip(1.0 - 0.92 * wm, 0.0, 1.0), 1.08)
    score = cv2.GaussianBlur(score.astype(np.float32), (0, 0), 2.2)
    # согласованность с «не-водой»: подмешиваем 1−M_w, но не заменяем признаки объекта
    score = np.clip(0.58 * score + 0.42 * (1.0 - wm), 0.0, 1.0)
    score = cv2.GaussianBlur(score, (0, 0), 1.8)
    return np.clip(score, 0.0, 1.0)


def estimate_neutral_mask_from_srgb(rgb_u8: np.ndarray, chroma_thresh: float = 11.5) -> np.ndarray:
    """
    §4.3: низкая S, светлые, серо-белые, близкие каналы RGB, низкая хрома Lab.
    """
    hsv = cv2.cvtColor(rgb_u8, cv2.COLOR_RGB2HSV).astype(np.float32)
    sm = hsv[:, :, 1] / 255.0
    lab = cv2.cvtColor(rgb_u8, cv2.COLOR_RGB2LAB)
    lf = lab[:, :, 0].astype(np.float32) / 255.0
    _, a, b = cv2.split(lab)
    af = a.astype(np.float32) - 128.0
    bf = b.astype(np.float32) - 128.0
    chroma = np.sqrt(af * af + bf * bf)
    low_chroma = (chroma < chroma_thresh).astype(np.float32)
    core = ((sm < 0.15) & (lf > 0.6)).astype(np.float32)
    rf = rgb_u8[:, :, 0].astype(np.float32) / 255.0
    gf = rgb_u8[:, :, 1].astype(np.float32) / 255.0
    bf_u = rgb_u8[:, :, 2].astype(np.float32) / 255.0
    mx = np.maximum(np.maximum(rf, gf), bf_u)
    mn = np.minimum(np.minimum(rf, gf), bf_u)
    spread = (mx - mn) / (mx + 1e-6)
    close_rgb = np.clip(1.0 - spread / 0.22, 0.0, 1.0)
    m = np.clip(
        0.5 * core
        + 0.35 * low_chroma * np.clip((lf - 0.32) / 0.58, 0.0, 1.0)
        + 0.15 * close_rgb * np.clip((lf - 0.25) / 0.65, 0.0, 1.0) * (1.0 - sm),
        0.0,
        1.0,
    )
    m = cv2.GaussianBlur(m, (0, 0), 2.0)
    return np.clip(m, 0.0, 1.0)


def build_skin_gear_protection_mask(rgb_srgb_u8: np.ndarray) -> np.ndarray:
    hsv = cv2.cvtColor(rgb_srgb_u8, cv2.COLOR_RGB2HSV).astype(np.float32)
    h, s, v = hsv[:, :, 0], hsv[:, :, 1] / 255.0, hsv[:, :, 2] / 255.0
    skin = ((h < 30.0) | (h > 156.0)) & (s > 0.11) & (s < 0.62) & (v > 0.18)
    near_white = (v > 0.86) & (s < 0.28)
    prot = (skin | near_white).astype(np.float32)
    prot = cv2.GaussianBlur(prot, (0, 0), 2.0)
    return np.clip(prot, 0.0, 1.0)


# --- §9 red recovery ------------------------------------------------------------


def adaptive_red_recovery(
    rgb_lin: np.ndarray,
    t: np.ndarray,
    water_mask: np.ndarray,
    object_mask: np.ndarray,
    neutral_mask: np.ndarray,
    protect_mask: np.ndarray,
    red_recovery_strength: float,
) -> np.ndarray:
    """
    §6 / §14.2: R' = R · (1 + kR · (1−t) · M_obj).
    M_water > 0.6 → k≈0; L > 0.85 → ослабление; глубже (меньше t) — сильнее; R' ≤ 2.5·R.
    """
    r = rgb_lin[:, :, 0].copy()
    g = rgb_lin[:, :, 1]
    b = rgb_lin[:, :, 2]
    lum = _rgb_to_luminance_linear(rgb_lin)
    hi = np.clip((lum - 0.85) / 0.15, 0.0, 1.0)
    m_obj = np.clip(object_mask, 0.0, 1.0)
    m_w = np.clip(water_mask, 0.0, 1.0)
    one_minus_t = np.clip(1.0 - t, 0.0, 1.0)
    # плавное обнуление при M_w > 0.6
    water_gate = np.clip(1.0 - (m_w - 0.6) / 0.12, 0.0, 1.0)
    kR = float(np.clip(red_recovery_strength, 0.0, 1.0)) * 2.05
    k = kR * one_minus_t * m_obj * water_gate
    k *= (1.0 - np.clip(protect_mask, 0.0, 1.0))
    k *= (1.0 - 0.88 * np.clip(neutral_mask, 0.0, 1.0))
    k *= (1.0 - hi) ** 1.45
    rg_balance = np.clip((g + b) * 0.5 / (r + 1e-6), 0.5, 4.0)
    need_red = np.clip(rg_balance - 0.88, 0.0, 1.5) / 1.5
    k *= 0.35 + 0.65 * need_red
    k = np.clip(k, 0.0, 1.55)
    r_boosted = r * (1.0 + k)
    r_cap = np.minimum(r_boosted, 2.5 * r + 1e-6)
    r2 = np.clip(r_cap, 0.0, 1.0)
    out = rgb_lin.copy()
    out[:, :, 0] = r2
    return _clip01(out)


# --- §12 WB --------------------------------------------------------------------


def underwater_white_balance_neutral_masked(
    rgb_lin: np.ndarray,
    water_mask: np.ndarray,
    neutral_mask: np.ndarray,
    object_mask: np.ndarray,
    mix_strength: float,
) -> np.ndarray:
    """
    §7: только neutral mask + robust percentiles; каналы по-разному;
    синий не поднимать в воде (нет фиолетового); без глобального warm shift.
    """
    lum = _rgb_to_luminance_linear(rgb_lin)
    light = np.clip((lum - 0.1) / 0.55, 0.0, 1.0)
    wm = np.clip(water_mask, 0.0, 1.0)
    w_stat = np.clip(neutral_mask * (1.0 - 0.78 * wm) * light, 0.0, 1.0)
    if float(w_stat.sum()) < 80.0:
        w_stat = np.clip(neutral_mask * (1.0 - 0.55 * wm) * 0.65, 0.06, 1.0)

    def pct_channel(ch: np.ndarray, p: float) -> float:
        ww = w_stat.reshape(-1)
        vals = ch.reshape(-1)
        m = ww > 0.32
        if int(np.sum(m)) < 40:
            m = ww > 0.06
        if int(np.sum(m)) < 20:
            return float(np.median(vals))
        return float(np.percentile(vals[m], p))

    pr = pct_channel(rgb_lin[:, :, 0], 56.0)
    pg = pct_channel(rgb_lin[:, :, 1], 56.0)
    pb = pct_channel(rgb_lin[:, :, 2], 56.0)
    eps = 1e-6
    gray = (pr + pg + pb) / 3.0 + eps
    kr = float(np.clip(gray / (pr + eps), 1.0, 2.5))
    kg = float(np.clip(gray / (pg + eps), 0.92, 1.48))
    kb = float(np.clip(gray / (pb + eps), 0.82, 1.18))
    eff = float(np.clip(mix_strength, 0.0, 1.0))
    base_w = np.clip(
        object_mask * (0.18 + 0.82 * neutral_mask) * (1.0 - 0.9 * wm) * light,
        0.0,
        1.0,
    )
    base_w = cv2.GaussianBlur(base_w.astype(np.float32), (0, 0), 2.2)
    w_b = base_w * (1.0 - 0.88 * wm)
    w_b = cv2.GaussianBlur(w_b, (0, 0), 2.5)
    dr = (kr - 1.0) * base_w * eff
    dg = (kg - 1.0) * base_w * eff
    db = (kb - 1.0) * w_b * eff
    out = rgb_lin.copy()
    out[:, :, 0] = np.clip(rgb_lin[:, :, 0] * (1.0 + dr), 0.0, 1.0)
    out[:, :, 1] = np.clip(rgb_lin[:, :, 1] * (1.0 + dg), 0.0, 1.0)
    out[:, :, 2] = np.clip(rgb_lin[:, :, 2] * (1.0 + db), 0.0, 1.0)
    return _clip01(out)


# --- §11 hue и saturation отдельно (fix v2) ------------------------------------


def selective_hue_only(
    rgb_u8: np.ndarray,
    warmth_bias: float,
    water_mask: np.ndarray,
    object_mask: np.ndarray,
    neutral_mask: np.ndarray,
) -> np.ndarray:
    """
    §8: remap — зелёный/болотный → тёплый; циан → синий; пурпур подавлять.
    Вода: |Δh| ≤ ~10–15° (здесь ≤5 ед. OpenCV ≈10° при wm>0.25).
    """
    wb = float(np.clip(warmth_bias, 0.0, 1.0))
    hsv = cv2.cvtColor(rgb_u8, cv2.COLOR_RGB2HSV).astype(np.float32)
    h, s, v = hsv[:, :, 0], hsv[:, :, 1], hsv[:, :, 2]
    h0 = h.copy()
    sm = s / 255.0
    wm = np.clip(water_mask, 0.0, 1.0)
    om = np.clip(object_mask, 0.0, 1.0)

    ctrl = om * (1.0 - 0.88 * neutral_mask) * (1.0 - 0.72 * wm) * wb
    low_sat = np.clip((0.2 - sm) / 0.2, 0.0, 1.0)
    amp = np.clip(ctrl * (1.0 - 0.82 * low_sat), 0.0, 1.0)

    green = ((h0 > 28.0) & (h0 < 78.0)).astype(np.float32)
    swamp = ((h0 > 78.0) & (h0 < 98.0)).astype(np.float32)
    cyan = ((h0 > 96.0) & (h0 < 108.0)).astype(np.float32)

    dh = np.zeros_like(h0, dtype=np.float32)
    dh -= green * amp * 14.0 * (1.0 - sm * 0.42)
    dh -= swamp * amp * 11.5 * (1.0 - sm * 0.38)
    dh += cyan * amp * 6.5 * np.clip(1.0 - 0.7 * wm, 0.0, 1.0)

    magenta = ((h0 > 128.0) & (h0 < 172.0)).astype(np.float32)
    dh -= magenta * amp * 5.0 * sm * (1.0 - wm)

    kill = np.clip((wm - 0.52) / 0.16, 0.0, 1.0)
    dh *= 1.0 - kill
    dh = np.where(wm > 0.6, 0.0, dh)

    blue_bin = (h0 > 99.0) & (h0 < 127.0)
    dh = np.where(blue_bin & (wm > 0.34) & (dh > 0.0), 0.0, dh)

    cyan_risk = (h0 > 94.0) & (h0 < 108.0)
    dh = np.where(cyan_risk & (wm > 0.3) & (dh > 2.8), 2.8, dh)
    dh = np.where(cyan_risk & (wm > 0.3) & (dh < -6.0), -6.0, dh)

    neut = (sm < 0.13) | (neutral_mask > 0.52)
    dh = np.where(neut, dh * 0.1, dh)

    cap_w = np.where(wm > 0.18, 5.0, 7.5)
    cap_o = np.where(om > 0.35, 16.0, 11.0)
    cap = np.minimum(cap_w, cap_o)
    dh = np.clip(dh, -cap, cap)

    h = np.mod(h0 + dh, 180.0)
    hsv2 = np.stack([h, s, v], axis=2)
    return cv2.cvtColor(hsv2.astype(np.uint8), cv2.COLOR_HSV2RGB)


def apply_nonlinear_saturation(
    rgb_u8: np.ndarray,
    strength: float,
    water_mask: np.ndarray,
    neutral_mask: np.ndarray,
    object_mask: np.ndarray,
) -> np.ndarray:
    """§9 / §14.3: S' = S·(1+k·(1−S)); S'≤0.85–0.9; тёплые зоны на объекте сильнее; вода слабо."""
    st = float(np.clip(strength, 0.0, 1.0))
    wm = np.clip(water_mask, 0.0, 1.0)
    nm = np.clip(neutral_mask, 0.0, 1.0)
    om = np.clip(object_mask, 0.0, 1.0)
    s_max = 0.87

    hsv = cv2.cvtColor(rgb_u8, cv2.COLOR_RGB2HSV).astype(np.float32)
    h, s, v = hsv[:, :, 0], hsv[:, :, 1], hsv[:, :, 2]
    sm = s / 255.0

    warm_band = ((h > 22.0) & (h < 95.0)).astype(np.float32)
    warm_w = 1.0 + 0.42 * warm_band * sm
    k_obj = 0.52 * st * om * (1.0 - wm) * (1.0 - 0.86 * nm) * warm_w
    k_water = 0.08 * st
    k = k_obj + wm * k_water
    sp = sm * (1.0 + k * (1.0 - sm))
    sp = np.minimum(sp, s_max)
    sp = sp * (1.0 - 0.7 * nm) + sm * (0.7 * nm)

    s_out = np.clip(sp * 255.0, 0.0, 255.0)
    hsv2 = np.stack([h, s_out, v], axis=2)
    return cv2.cvtColor(hsv2.astype(np.uint8), cv2.COLOR_HSV2RGB)


# --- §13 local contrast (L only) ----------------------------------------------


def local_contrast_on_luminance(
    rgb_u8: np.ndarray,
    detail_boost: float,
    water_mask: np.ndarray,
    object_mask: np.ndarray,
) -> np.ndarray:
    """§10: CLAHE + high-pass по L; сильнее на объекте, вода гладкая; локальный потолок ~30%%."""
    db = float(np.clip(detail_boost, 0.0, 1.0))
    lab = cv2.cvtColor(rgb_u8, cv2.COLOR_RGB2LAB)
    l, a, b_ch = cv2.split(lab)
    lf = l.astype(np.float32)
    wm = water_mask.astype(np.float32)
    om = object_mask.astype(np.float32)
    clip_strong = float(np.clip(1.15 + 1.25 * db, 1.15, 2.85))
    clip_soft = float(np.clip(0.88 + 0.42 * db, 0.85, 1.55))
    cl_str = cv2.createCLAHE(clipLimit=clip_strong, tileGridSize=(16, 16))
    cl_soft = cv2.createCLAHE(clipLimit=clip_soft, tileGridSize=(16, 16))
    l_s = cl_str.apply(l).astype(np.float32)
    l_w = cl_soft.apply(l).astype(np.float32)
    w_detail = np.clip(om * (1.0 - 0.86 * wm), 0.0, 1.0)
    w_detail = cv2.GaussianBlur(w_detail, (0, 0), 2.0)
    l2 = l_w * (1.0 - w_detail) + l_s * w_detail
    blur = cv2.GaussianBlur(l2, (0, 0), 1.15 + 1.2 * db)
    dgain = (0.22 + 0.55 * db) * (0.25 + 0.75 * om) * (0.18 + 0.82 * (1.0 - wm))
    detail = (l2 - blur) * dgain
    cap = 0.3 * np.maximum(lf, 8.0)
    detail = np.clip(detail, -cap, cap)
    l3 = np.clip(l2 + detail, 0.0, 255.0).astype(np.uint8)
    out = cv2.merge([l3, a, b_ch])
    return cv2.cvtColor(out, cv2.COLOR_LAB2RGB)


# --- §14 denoise (chroma then lum) ---------------------------------------------


def edge_aware_denoise_ordered(rgb_u8: np.ndarray, noise_reduction: float) -> np.ndarray:
    """§14.3: сначала chroma bilateral, затем лёгкий luminance edge-aware."""
    nr = float(np.clip(noise_reduction, 0.0, 1.0))
    lab = cv2.cvtColor(rgb_u8, cv2.COLOR_RGB2LAB)
    l, a, b_ch = cv2.split(lab)
    h, w = l.shape
    d = max(3, int(round(3.0 + 5.0 * nr)))
    if h > 32 and w > 32:
        a2 = cv2.bilateralFilter(a, d, 14.0 + 12.0 * nr, 14.0 + 12.0 * nr)
        b2 = cv2.bilateralFilter(b_ch, d, 14.0 + 12.0 * nr, 14.0 + 12.0 * nr)
        l2 = cv2.edgePreservingFilter(
            l,
            flags=cv2.RECURS_FILTER,
            sigma_s=26.0 + 28.0 * nr,
            sigma_r=0.09 + 0.09 * nr,
        )
    else:
        l2, a2, b2 = l, a, b_ch
    rgb = cv2.cvtColor(cv2.merge([l2, a2, b2]), cv2.COLOR_LAB2RGB)
    lin = rgb_u8_to_rgb_linear(rgb)
    lum = _rgb_to_luminance_linear(lin)
    dark = np.clip((0.24 - lum) / 0.24, 0.0, 1.0)
    g = (lin[..., 0] + lin[..., 1] + lin[..., 2]) / 3.0
    mix = np.clip(0.32 * dark * nr, 0.0, 1.0)[..., None]
    lin2 = lin * (1.0 - mix) + g[..., None] * mix
    return rgb_linear_to_srgb_u8(_clip01(lin2))


# --- §11 blend water / §15 tone -------------------------------------------------


def apply_water_preservation_blend(
    warm_rgb_u8: np.ndarray,
    cool_rgb_u8: np.ndarray,
    water_mask: np.ndarray,
    preserve_blues: float,
) -> np.ndarray:
    """Смешивание с холодным dehaze в воде; без взрыва S (fix v2)."""
    pb = float(np.clip(preserve_blues, 0.0, 1.0))
    a = np.clip(pb * water_mask[..., None], 0.0, 1.0)
    blend = (warm_rgb_u8.astype(np.float32) * (1.0 - a) + cool_rgb_u8.astype(np.float32) * a).clip(
        0, 255
    ).astype(np.uint8)
    hsv = cv2.cvtColor(blend, cv2.COLOR_RGB2HSV).astype(np.float32)
    hh, ss, vv = hsv[:, :, 0], hsv[:, :, 1], hsv[:, :, 2]
    bm = (water_mask > 0.32).astype(np.float32)
    sm = ss / 255.0
    sm = sm * (1.0 + bm * 0.08 * pb)
    sm = np.minimum(sm, 0.88)
    ss = np.clip(sm * 255.0, 0.0, 255.0)
    hsv2 = np.stack([hh, ss, vv], axis=2).astype(np.uint8)
    return cv2.cvtColor(hsv2, cv2.COLOR_HSV2RGB)


def final_tone_map_soft(
    rgb_u8: np.ndarray,
    strength: float,
    water_mask: np.ndarray,
    neutral_mask: np.ndarray,
) -> np.ndarray:
    """
    Fix v2 §2.7: мягкий tone map L/(L+a), лёгкая S-кривая, компрессия L>0.9,
    без провала в чистый чёрный.
    """
    lab = cv2.cvtColor(rgb_u8, cv2.COLOR_RGB2LAB)
    l, a, b_ch = cv2.split(lab)
    x = l.astype(np.float32) / 255.0
    st = float(np.clip(strength, 0.0, 1.0))
    aa = float(np.clip(0.12 + 0.12 * (1.0 - st), 0.07, 0.28))
    z_tm = x / (x + aa)
    y = 3.0 * x**2 - 2.0 * x**3
    wmix = st * 0.1
    z = 0.52 * z_tm + 0.48 * ((1.0 - wmix) * x + wmix * y)
    gamma = 0.97 + 0.03 * (1.0 - st)
    z = np.clip(z, 1e-4, 1.0) ** gamma
    hi = x > 0.9
    z_hi = 0.9 + (x - 0.9) * 0.3
    z = np.where(hi, (1.0 - 0.55 * st) * z + (0.55 * st) * z_hi, z)
    prot = np.clip(0.45 * water_mask + 0.36 * neutral_mask, 0.0, 1.0)
    z = z * (1.0 - prot) + x * prot
    z = np.clip(z, 0.018, 1.0)
    l2 = (z * 255.0 + 0.5).astype(np.uint8)
    out = cv2.merge([l2, a, b_ch])
    return cv2.cvtColor(out, cv2.COLOR_LAB2RGB)


# --- §21 оркестрация -----------------------------------------------------------


def underwater_color_restore(
    bgr: np.ndarray,
    strength: float = 1.0,
    preserve_blues: float = 0.72,
    detail_boost: float = 0.45,
    warmth_bias: float = 0.5,
    dehaze_strength: float = 0.55,
    red_recovery_strength: float = 0.72,
    noise_reduction: float = 0.45,
) -> np.ndarray:
    """
    Полный пайплайн по ТЗ. Вход: BGR uint8 или uint16 → выход BGR uint8.
    """
    p = GptRestoreParams(
        strength=float(np.clip(strength, 0.0, 1.0)),
        preserve_blues=float(np.clip(preserve_blues, 0.0, 1.0)),
        detail_boost=float(np.clip(detail_boost, 0.0, 1.0)),
        warmth_bias=float(np.clip(warmth_bias, 0.0, 1.0)),
        dehaze_strength=float(np.clip(dehaze_strength, 0.2, 0.95)),
        red_recovery_strength=float(np.clip(red_recovery_strength, 0.0, 1.0)),
        noise_reduction=float(np.clip(noise_reduction, 0.0, 1.0)),
    )

    rgb_lin = bgr_to_rgb_linear(bgr)
    is_uw, uw_score = is_underwater_scene(rgb_lin)
    if not is_uw:
        return mild_enhancement_bgr(bgr)

    # Плавное ослабление на пограничных скорах (без «ломания» сухопутных кадров)
    edge = float(np.clip((uw_score - 0.05) / 0.12, 0.0, 1.0))
    eff = p.strength * (0.55 + 0.45 * edge)

    # §6–7
    A = estimate_water_light_A(rgb_lin)
    omega = float(np.clip(0.5 + 0.46 * p.dehaze_strength * eff, 0.38, 0.97))
    t = estimate_transmission_map(rgb_lin, A, omega)
    lum_in = _rgb_to_luminance_linear(rgb_lin)
    t = refine_transmission(t, lum_in)

    # §3 fix v2: dehaze до масок и цветокоррекции; t_min 0.15–0.25
    t_min = float(np.clip(0.16 + 0.09 * (1.0 - eff), 0.15, 0.25))
    J = remove_haze_linear(rgb_lin, A, t, t_min=t_min)
    cool_ref = rgb_linear_to_srgb_u8(J)

    water_mask = estimate_water_mask(J, t, cool_ref)
    object_mask = estimate_object_mask(J, water_mask, t)
    neutral_mask = estimate_neutral_mask_from_srgb(cool_ref)

    protect = build_skin_gear_protection_mask(cool_ref)
    protect = np.clip(protect + 0.28 * neutral_mask * (1.0 - water_mask), 0.0, 1.0)

    J = adaptive_red_recovery(
        J,
        t,
        water_mask,
        object_mask,
        neutral_mask,
        protect,
        p.red_recovery_strength * eff,
    )
    J = underwater_white_balance_neutral_masked(
        J,
        water_mask,
        neutral_mask,
        object_mask,
        mix_strength=0.88 * eff,
    )

    rgb8 = rgb_linear_to_srgb_u8(J)
    neutral8 = estimate_neutral_mask_from_srgb(rgb8)
    rgb8 = selective_hue_only(
        rgb8,
        p.warmth_bias * eff,
        water_mask,
        object_mask,
        neutral8,
    )
    rgb8 = apply_nonlinear_saturation(rgb8, eff, water_mask, neutral8, object_mask)
    rgb8 = local_contrast_on_luminance(rgb8, p.detail_boost * eff, water_mask, object_mask)
    om_mean = float(np.mean(object_mask))
    nr_w = p.noise_reduction * (0.5 + 0.5 * eff) * (1.0 - 0.1 * np.clip(om_mean - 0.28, 0.0, 0.45))
    rgb8 = edge_aware_denoise_ordered(rgb8, nr_w)
    rgb8 = apply_water_preservation_blend(
        rgb8,
        cool_ref,
        water_mask,
        p.preserve_blues * (0.5 + 0.5 * eff),
    )
    neutral_final = estimate_neutral_mask_from_srgb(rgb8)
    rgb8 = final_tone_map_soft(rgb8, eff, water_mask, neutral_final)

    return rgb_u8_to_bgr_u8(rgb8)


# Обратная совместимость: раньше принимали только uint8 BGR
def bgr_u8_to_rgb_linear(bgr_u8: np.ndarray) -> np.ndarray:
    return bgr_to_rgb_linear(bgr_u8)
