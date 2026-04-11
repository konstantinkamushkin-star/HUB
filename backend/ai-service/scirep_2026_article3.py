"""
Scientific Reports (2026) s41598-025-33170-9:
"Underwater image enhancement using colour balancing and morphological
residual processing through gamma correction".

Implements the algorithmic blocks described in the paper:
1) adaptive red/blue compensation + gray-world balancing (Eq. 11, 12),
2) MPR branch (Eq. 2-10),
3) normalized unsharp branch (Eq. 13),
4) multiscale fusion using Laplacian/Gaussian pyramids (Eq. 14-18),
5) final gamma correction.

Paper constants explicitly provided:
- alpha (color compensation) = 1.0
- t (morphological amplitude threshold) = 0.001
- s (size criterion) = 0.1
- c (contrast control) = 2.4
- Gaussian sigma in low-pass filter = 15
- delta in fusion weight normalization (Eq. 15) = 0.1

Assumptions (paper text does not provide exact implementation details):
- area opening threshold `s` is interpreted as fraction of image area.
- final gamma value is fixed to 0.90 (paper mandates final gamma correction
  but does not provide an explicit scalar in the provided equations section).
"""
from __future__ import annotations

import cv2
import numpy as np
from scipy import ndimage as ndi

ALPHA = 1.0
THRESH_T = 0.001
SIZE_S = 0.1
CONTRAST_C = 2.4
LOWPASS_SIGMA = 15.0
DELTA = 0.1
FINAL_GAMMA = 0.90
PYR_LEVELS = 5


def _to_float01(bgr_u8: np.ndarray) -> np.ndarray:
    return bgr_u8.astype(np.float32) / 255.0


def _gray_world_balance(rgb: np.ndarray) -> np.ndarray:
    """Standard gray-world normalization after channel compensation."""
    means = np.maximum(rgb.mean(axis=(0, 1)), 1e-6)
    gmean = float(means.mean())
    out = rgb * (gmean / means.reshape(1, 1, 3))
    return np.clip(out, 0.0, 1.0)


def _color_balance_compensation(rgb: np.ndarray) -> np.ndarray:
    """
    Eq. (11), (12): compensate red and blue against green.
    rgb values are in [0, 1].
    """
    r = rgb[:, :, 0]
    g = rgb[:, :, 1]
    b = rgb[:, :, 2]
    mr = float(r.mean())
    mg = float(g.mean())
    mb = float(b.mean())

    rc = r + ALPHA * (mg - mr) * (1.0 - r * g)
    bc = b + ALPHA * (mg - mb) * (1.0 - b * g)
    out = rgb.copy()
    out[:, :, 0] = rc
    out[:, :, 2] = bc
    return np.clip(out, 0.0, 1.0)


def _gaussian_lowpass(img: np.ndarray, sigma: float = LOWPASS_SIGMA) -> np.ndarray:
    k = max(3, int(round(6 * sigma + 1)) | 1)  # odd kernel ~ 6*sigma
    return cv2.GaussianBlur(img, (k, k), sigmaX=sigma, sigmaY=sigma, borderType=cv2.BORDER_REFLECT101)


def _normalize01(x: np.ndarray) -> np.ndarray:
    lo = float(x.min())
    hi = float(x.max())
    if hi - lo < 1e-8:
        return np.zeros_like(x)
    return (x - lo) / (hi - lo)


def _geodesic_reconstruct(marker: np.ndarray, mask: np.ndarray, footprint: np.ndarray) -> np.ndarray:
    """Morphological reconstruction by dilation: R_mask(marker)."""
    prev = marker.copy()
    while True:
        dil = ndi.grey_dilation(prev, footprint=footprint)
        cur = np.minimum(dil, mask)
        if np.allclose(cur, prev):
            return cur
        prev = cur


def _area_open_binary(sel: np.ndarray, s_frac: float) -> np.ndarray:
    """
    Area opening on binary mask with threshold = s_frac * image_area.
    Eq. (9): S_{t,s}(I) = (I>=t) o(s)
    """
    if not sel.any():
        return sel
    h, w = sel.shape
    min_area = max(1, int(round(s_frac * h * w)))
    labels, n = ndi.label(sel)
    if n == 0:
        return sel
    counts = np.bincount(labels.ravel())
    keep = counts >= min_area
    keep[0] = False
    return keep[labels]


def _morph_operator(res_part: np.ndarray) -> np.ndarray:
    """
    Eq. (7): M(I)=R_I(min(I, S_{t,s}(I)|_{min(I),max(I)})).
    Implemented for non-negative residual part.
    """
    x = np.maximum(res_part, 0.0)
    sel = x >= THRESH_T
    sel = _area_open_binary(sel, SIZE_S)
    if not sel.any():
        return np.zeros_like(x)
    min_v = float(x.min())
    max_v = float(x.max())
    mapped = np.where(sel, max_v, min_v)
    marker = np.minimum(x, mapped)
    footprint = np.array([[0, 1, 0], [1, 1, 1], [0, 1, 0]], dtype=np.uint8)
    rec = _geodesic_reconstruct(marker, x, footprint)
    return rec


def _mpr_enhance(rgb_balanced: np.ndarray) -> np.ndarray:
    """
    MPR branch: Eq. (2)-(10)
    I = u * L, Res = u - I
    I_out = I + (M(I_res+) - M(I_res-)) * c
    """
    base = _gaussian_lowpass(rgb_balanced, LOWPASS_SIGMA)
    out = np.empty_like(rgb_balanced)
    for c in range(3):
        u = rgb_balanced[:, :, c]
        I = base[:, :, c]
        res = u - I
        res_pos = 0.5 * (res + np.abs(res))   # Eq. (4)
        res_neg = 0.5 * (np.abs(res) - res)   # magnitude of negative residual
        m_pos = _morph_operator(res_pos)
        m_neg = _morph_operator(res_neg)
        out[:, :, c] = np.clip(I + (m_pos - m_neg) * CONTRAST_C, 0.0, 1.0)
    return out


def _normalized_unsharp(rgb_balanced: np.ndarray) -> np.ndarray:
    """
    Eq. (13): S = ( I + N{ I - G*I } ) / 2
    """
    blur = _gaussian_lowpass(rgb_balanced, LOWPASS_SIGMA)
    hp = rgb_balanced - blur
    nhp = np.stack([_normalize01(hp[:, :, c]) for c in range(3)], axis=2)
    out = 0.5 * (rgb_balanced + nhp)
    return np.clip(out, 0.0, 1.0)


def _laplacian_contrast_weight(rgb: np.ndarray) -> np.ndarray:
    lum = cv2.cvtColor((np.clip(rgb, 0, 1) * 255.0).astype(np.uint8), cv2.COLOR_RGB2GRAY).astype(np.float32) / 255.0
    lap = cv2.Laplacian(lum, cv2.CV_32F, ksize=3)
    return np.abs(lap)


def _saliency_weight(rgb: np.ndarray) -> np.ndarray:
    """
    Frequency-tuned saliency (Achanta et al. 2009): ||Lab - mean(Lab)||.
    """
    lab = cv2.cvtColor((np.clip(rgb, 0, 1) * 255.0).astype(np.uint8), cv2.COLOR_RGB2LAB).astype(np.float32)
    m = lab.reshape(-1, 3).mean(axis=0).reshape(1, 1, 3)
    d = lab - m
    return np.sqrt(np.sum(d * d, axis=2))


def _saturation_weight(rgb: np.ndarray) -> np.ndarray:
    """Eq. (14)."""
    r = rgb[:, :, 0]
    g = rgb[:, :, 1]
    b = rgb[:, :, 2]
    L = 0.299 * r + 0.587 * g + 0.114 * b
    return np.sqrt((1.0 / 3.0) * ((r - L) ** 2 + (g - L) ** 2 + (b - L) ** 2))


def _build_gaussian_pyramid(img: np.ndarray, levels: int) -> list[np.ndarray]:
    pyr = [img]
    cur = img
    for _ in range(1, levels):
        cur = cv2.pyrDown(cur)
        pyr.append(cur)
    return pyr


def _build_laplacian_pyramid(img: np.ndarray, levels: int) -> list[np.ndarray]:
    gp = _build_gaussian_pyramid(img, levels)
    lp: list[np.ndarray] = []
    for i in range(levels - 1):
        up = cv2.pyrUp(gp[i + 1], dstsize=(gp[i].shape[1], gp[i].shape[0]))
        lp.append(gp[i] - up)
    lp.append(gp[-1])
    return lp


def _reconstruct_from_laplacian(lp: list[np.ndarray]) -> np.ndarray:
    cur = lp[-1]
    for i in range(len(lp) - 2, -1, -1):
        cur = cv2.pyrUp(cur, dstsize=(lp[i].shape[1], lp[i].shape[0])) + lp[i]
    return cur


def _fuse_multiscale(inp1: np.ndarray, inp2: np.ndarray) -> np.ndarray:
    """
    Eq. (14)-(18) with K=2 inputs.
    """
    w1 = _laplacian_contrast_weight(inp1) + _saliency_weight(inp1) + _saturation_weight(inp1)
    w2 = _laplacian_contrast_weight(inp2) + _saliency_weight(inp2) + _saturation_weight(inp2)
    den = w1 + w2 + 2.0 * DELTA
    nw1 = (w1 + DELTA) / np.maximum(den, 1e-8)  # Eq. (15)
    nw2 = (w2 + DELTA) / np.maximum(den, 1e-8)

    levels = max(2, min(PYR_LEVELS, int(np.floor(np.log2(min(inp1.shape[:2])))) - 2))
    g1 = _build_gaussian_pyramid(nw1.astype(np.float32), levels)
    g2 = _build_gaussian_pyramid(nw2.astype(np.float32), levels)

    l1 = _build_laplacian_pyramid(inp1.astype(np.float32), levels)
    l2 = _build_laplacian_pyramid(inp2.astype(np.float32), levels)

    fused_lp = []
    for i in range(levels):
        w1i = g1[i][..., None]
        w2i = g2[i][..., None]
        fused_lp.append(w1i * l1[i] + w2i * l2[i])  # Eq. (18)
    fused = _reconstruct_from_laplacian(fused_lp)
    return np.clip(fused, 0.0, 1.0)


def enhance_scirep_article3(bgr_u8: np.ndarray) -> np.ndarray:
    """
    Full article-3 pipeline output as BGR uint8.
    """
    rgb = cv2.cvtColor(bgr_u8, cv2.COLOR_BGR2RGB).astype(np.float32) / 255.0

    comp = _color_balance_compensation(rgb)   # Eq. 11,12
    cb = _gray_world_balance(comp)            # after compensation per paper text

    inp_mpr = _mpr_enhance(cb)
    inp_unsharp = _normalized_unsharp(cb)

    fused = _fuse_multiscale(inp_mpr, inp_unsharp)
    final = np.power(np.clip(fused, 0.0, 1.0), FINAL_GAMMA)  # final gamma correction
    out_rgb = (final * 255.0).clip(0, 255).astype(np.uint8)
    return cv2.cvtColor(out_rgb, cv2.COLOR_RGB2BGR)

