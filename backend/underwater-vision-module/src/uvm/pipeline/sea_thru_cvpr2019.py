"""
Robust Sea-Thru style pipeline for single JPEG.

Implements product requirements:
- scene suitability check (underwater / likely / not underwater)
- robust percentile-based backscatter point selection per depth bin
- backscatter model: B(d)=B_inf*(1-exp(-beta_B*d^gamma))
- transmission model: T(d)=exp(-beta_D*d) with safe floor
- reconstruction: J=(I-B)/T
- soft finishing only (percentile stretch + bounded channel balancing)
- quality presets: fast / balanced / quality
- fallback chain: full -> simplified -> soft enhancement -> original
"""

from __future__ import annotations

import os
from functools import lru_cache
from pathlib import Path

import cv2
import numpy as np
from scipy.optimize import least_squares


_PRESETS = {
    "fast": {
        "opt_iters": 3,
        "neighborhood_iters": 5,
        "illum_iters": 10,
        "bs_percentiles": (0.5, 8.0),
        "max_side": 768,
    },
    "balanced": {
        "opt_iters": 8,
        "neighborhood_iters": 10,
        "illum_iters": 20,
        "bs_percentiles": (0.5, 8.0),
        "max_side": 1024,
    },
    "quality": {
        "opt_iters": 15,
        "neighborhood_iters": 15,
        "illum_iters": 30,
        "bs_percentiles": (0.5, 8.0),
        "max_side": 1400,
    },
}

_VALID_DEPTH_ENCODERS = {"vits", "vitb", "vitl", "vitg"}
_DEPTH_BACKEND_STATUS: dict = {
    "configured": False,
    "loaded": False,
    "backend": "proxy",
    "message": "DepthAnythingV2 not initialized; fallback proxy enabled",
    "checkpoint": "",
    "encoder": "vits",
    "device_request": "auto",
    "device_actual": "cpu",
}


def _bgr_to_rgb01(bgr: np.ndarray) -> np.ndarray:
    rgb = cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB).astype(np.float64) / 255.0
    return np.clip(rgb, 0.0, 1.0)


def _rgb01_to_bgr_u8(rgb: np.ndarray) -> np.ndarray:
    x = np.clip(rgb * 255.0, 0.0, 255.0).astype(np.uint8)
    return cv2.cvtColor(x, cv2.COLOR_RGB2BGR)


def estimate_range_map_z(rgb: np.ndarray, depth_hint_m: float | None) -> np.ndarray:
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    dc = np.minimum(np.minimum(r, g), b)
    k = max(9, min(29, int(round(0.024 * max(rgb.shape[0], rgb.shape[1])))))
    if k % 2 == 0:
        k += 1
    kernel = cv2.getStructuringElement(cv2.MORPH_RECT, (k, k))
    dark = cv2.erode(dc.astype(np.float32), kernel)
    z_rel = -np.log(np.clip(dark.astype(np.float64), 0.025, 1.0))
    z_rel = cv2.GaussianBlur(z_rel, (0, 0), sigmaX=2.2)
    lo, hi = np.percentile(z_rel, [2.0, 98.0])
    if hi <= lo + 1e-9:
        hi = lo + 1e-3
    z_n = (z_rel - lo) / (hi - lo)
    z_n = np.clip(z_n, 0.0, 1.0)
    span = max(float(depth_hint_m or 12.0), 1.5) * 1.2
    z = 0.18 + z_n * span
    z = cv2.GaussianBlur(z.astype(np.float32), (0, 0), sigmaX=2.2)
    return np.clip(z, 0.12, None).astype(np.float64)


@lru_cache(maxsize=4)
def _load_depth_anything_v2(encoder: str, checkpoint_path: str, device_pref: str):
    """
    Lazy loader for DepthAnythingV2.
    Returns (model, torch, device_string) or (None, None, None) on failure.
    """
    try:
        import torch  # type: ignore
    except Exception:
        return None, None, None
    try:
        from depth_anything_v2.dpt import DepthAnythingV2  # type: ignore
    except Exception:
        try:
            from depth_anything_v2 import DepthAnythingV2  # type: ignore
        except Exception:
            return None, None, None
    if device_pref == "auto":
        candidates = ["cuda", "mps", "cpu"]
    else:
        candidates = [device_pref, "cpu"] if device_pref != "cpu" else ["cpu"]
    if not checkpoint_path or not os.path.isfile(checkpoint_path):
        return None, None, None
    model_cfg = {
        "vits": {"features": 64, "out_channels": [48, 96, 192, 384]},
        "vitb": {"features": 128, "out_channels": [96, 192, 384, 768]},
        "vitl": {"features": 256, "out_channels": [256, 512, 1024, 1024]},
        "vitg": {"features": 384, "out_channels": [1536, 1536, 1536, 1536]},
    }
    cfg = model_cfg.get(encoder, model_cfg["vits"])
    last_err = None
    for dev in candidates:
        if dev == "cuda" and not torch.cuda.is_available():
            continue
        if dev == "mps" and not (getattr(torch.backends, "mps", None) and torch.backends.mps.is_available()):
            continue
        try:
            model = DepthAnythingV2(encoder=encoder, **cfg)
            state = torch.load(checkpoint_path, map_location="cpu")
            if isinstance(state, dict) and "state_dict" in state:
                state = state["state_dict"]
            model.load_state_dict(state, strict=False)
            model.eval()
            model.to(dev)
            return model, torch, dev
        except Exception as e:
            last_err = e
            continue
    if last_err is not None:
        raise RuntimeError(f"DepthAnythingV2 load failed on candidates {candidates}: {last_err}") from last_err
    return None, None, None


def _depth_default_checkpoint_path() -> str:
    here = Path(__file__).resolve().parent
    default_ckpt = here / "models" / "depth_anything_v2_vits.pth"
    return str(default_ckpt)


def _depth_config_from_env() -> tuple[str, str, str]:
    encoder = (os.environ.get("SEATHRU_DEPTH_ENCODER") or "vits").strip().lower()
    ckpt_env = (os.environ.get("SEATHRU_DEPTHANYTHINGV2_CHECKPOINT") or "").strip()
    ckpt = ckpt_env if ckpt_env else _depth_default_checkpoint_path()
    device_pref = (os.environ.get("SEATHRU_DEPTH_DEVICE") or "auto").strip().lower()
    return encoder, ckpt, device_pref


def initialize_depth_backend() -> dict:
    """
    Validate and initialize depth backend at startup.
    Returns status dict used in health/log/report.
    """
    global _DEPTH_BACKEND_STATUS
    encoder, ckpt, device_pref = _depth_config_from_env()
    status = {
        "configured": False,
        "loaded": False,
        "backend": "proxy",
        "message": "",
        "checkpoint": ckpt,
        "encoder": encoder,
        "device_request": device_pref,
        "device_actual": "cpu",
    }
    if encoder not in _VALID_DEPTH_ENCODERS:
        status["message"] = (
            f"Invalid SEATHRU_DEPTH_ENCODER='{encoder}'. "
            f"Allowed: {sorted(_VALID_DEPTH_ENCODERS)}. Fallback proxy enabled."
        )
        _DEPTH_BACKEND_STATUS = status
        return dict(_DEPTH_BACKEND_STATUS)
    if device_pref not in {"auto", "cpu", "cuda", "mps"}:
        status["message"] = (
            f"Invalid SEATHRU_DEPTH_DEVICE='{device_pref}'. Allowed: auto/cpu/cuda/mps. "
            "Fallback proxy enabled."
        )
        _DEPTH_BACKEND_STATUS = status
        return dict(_DEPTH_BACKEND_STATUS)
    env_ckpt_set = bool((os.environ.get("SEATHRU_DEPTHANYTHINGV2_CHECKPOINT") or "").strip())
    if not os.path.isfile(ckpt):
        status["message"] = (
            f"DepthAnythingV2 checkpoint not found: {ckpt}. "
            + ("SEATHRU_DEPTHANYTHINGV2_CHECKPOINT was explicitly set. " if env_ckpt_set else "")
            + "Fallback proxy enabled."
        )
        _DEPTH_BACKEND_STATUS = status
        return dict(_DEPTH_BACKEND_STATUS)
    status["configured"] = True
    try:
        model, torch, dev = _load_depth_anything_v2(encoder, ckpt, device_pref)
        if model is None or torch is None:
            status["message"] = "DepthAnythingV2 unavailable (import failed). Fallback proxy enabled."
            _DEPTH_BACKEND_STATUS = status
            return dict(_DEPTH_BACKEND_STATUS)
        status["loaded"] = True
        status["backend"] = "depth_anything_v2"
        status["device_actual"] = dev
        status["message"] = f"DepthAnythingV2 loaded ({encoder}) on {dev}."
    except Exception as e:
        status["message"] = f"DepthAnythingV2 initialization error: {e}. Fallback proxy enabled."
    _DEPTH_BACKEND_STATUS = status
    return dict(_DEPTH_BACKEND_STATUS)


def get_depth_backend_status() -> dict:
    return dict(_DEPTH_BACKEND_STATUS)


def _estimate_depth_map(
    I: np.ndarray,
    *,
    depth_hint_m: float | None,
    quality: str,
) -> tuple[np.ndarray, float, str]:
    """
    DepthAnythingV2 if available; otherwise fallback to proxy depth.
    Returns (z_map, depth_confidence, source).
    """
    encoder, ckpt, device_pref = _depth_config_from_env()
    model, torch, dev = _load_depth_anything_v2(encoder, ckpt, device_pref)
    if model is None or torch is None:
        return estimate_range_map_z(I, depth_hint_m), 0.0, "proxy"
    try:
        # Use smaller side for fast mode to keep preview responsive.
        side = 640 if quality == "fast" else (896 if quality == "balanced" else 1152)
        h, w = I.shape[:2]
        s = min(1.0, side / float(max(h, w)))
        if s < 1.0:
            rs = cv2.resize(I, (int(round(w * s)), int(round(h * s))), interpolation=cv2.INTER_AREA)
        else:
            rs = I
        rgb_u8 = np.clip(rs * 255.0, 0, 255).astype(np.uint8)
        bgr_u8 = cv2.cvtColor(rgb_u8, cv2.COLOR_RGB2BGR)
        with torch.no_grad():
            # Common API variants across repos.
            if hasattr(model, "infer_image"):
                d = model.infer_image(bgr_u8)
            elif hasattr(model, "infer"):
                d = model.infer(bgr_u8)
            else:
                x = torch.from_numpy(rgb_u8).permute(2, 0, 1).float().unsqueeze(0) / 255.0
                x = x.to(dev)
                d = model(x)
        d = np.asarray(d, dtype=np.float32)
        if d.ndim == 3:
            d = d.squeeze()
        if d.ndim != 2 or not np.isfinite(d).any():
            raise RuntimeError("invalid depth output shape")
        d = cv2.GaussianBlur(d, (0, 0), sigmaX=1.2)
        # Normalize and scale to metric-like range for physical terms.
        lo, hi = np.percentile(d, [2.0, 98.0])
        if hi <= lo + 1e-8:
            raise RuntimeError("degenerate depth range")
        d_n = np.clip((d - lo) / (hi - lo), 0.0, 1.0)
        span = max(float(depth_hint_m or 12.0), 1.5) * 1.2
        z = 0.18 + d_n * span
        if s < 1.0:
            z = cv2.resize(z, (w, h), interpolation=cv2.INTER_CUBIC)
        z = np.clip(z, 0.12, None).astype(np.float64)
        # Confidence proxy: depth dynamic range + edge agreement with luminance.
        lum = np.mean(I, axis=2).astype(np.float32)
        gx1 = cv2.Sobel(lum, cv2.CV_32F, 1, 0, ksize=3)
        gy1 = cv2.Sobel(lum, cv2.CV_32F, 0, 1, ksize=3)
        g1 = np.sqrt(gx1 * gx1 + gy1 * gy1)
        gx2 = cv2.Sobel(z.astype(np.float32), cv2.CV_32F, 1, 0, ksize=3)
        gy2 = cv2.Sobel(z.astype(np.float32), cv2.CV_32F, 0, 1, ksize=3)
        g2 = np.sqrt(gx2 * gx2 + gy2 * gy2)
        corr = float(np.corrcoef(g1.ravel(), g2.ravel())[0, 1]) if np.std(g1) > 1e-6 and np.std(g2) > 1e-6 else 0.0
        depth_conf = float(np.clip(0.55 + 0.35 * corr, 0.0, 1.0))
        return z, depth_conf, "depth_anything_v2"
    except Exception as e:
        global _DEPTH_BACKEND_STATUS
        _DEPTH_BACKEND_STATUS["message"] = f"DepthAnythingV2 inference failed: {e}. Fallback proxy enabled."
        return estimate_range_map_z(I, depth_hint_m), 0.2, "proxy_fallback"


def _collect_robust_backscatter_points(
    I: np.ndarray,
    z: np.ndarray,
    *,
    n_clusters: int = 12,
    low_pct: float = 0.8,
    high_pct: float = 10.0,
) -> tuple[list[np.ndarray], list[np.ndarray]]:
    """
    Robust selection: percentile interval within each depth bin (per channel).
    Returns per-channel (depths, values).
    """
    zf = z.ravel()
    If = I.reshape(-1, 3)
    zmin, zmax = float(np.min(zf)), float(np.max(zf))
    if zmax <= zmin:
        zmax = zmin + 1e-3
    edges = np.linspace(zmin, zmax, n_clusters + 1)
    z_ch: list[list[np.ndarray]] = [[], [], []]
    v_ch: list[list[np.ndarray]] = [[], [], []]
    for k in range(n_clusters):
        lo, hi = edges[k], edges[k + 1]
        mask = (zf >= lo) & (zf < hi) if k < n_clusters - 1 else (zf >= lo) & (zf <= hi)
        idx = np.nonzero(mask)[0]
        if idx.size == 0:
            continue
        for c in range(3):
            vals = If[idx, c]
            if vals.size < 24:
                sel = idx
            else:
                p_lo = np.percentile(vals, low_pct)
                p_hi = np.percentile(vals, high_pct)
                m = (vals >= p_lo) & (vals <= p_hi)
                sel = idx[m]
                # Extra robust trimming inside percentile band.
                if sel.size > 24:
                    vsel = If[sel, c]
                    med = np.median(vsel)
                    mad = np.median(np.abs(vsel - med)) + 1e-6
                    m2 = np.abs(vsel - med) <= 2.6 * mad
                    sel = sel[m2]
                if sel.size < 8:
                    sel = idx
            z_ch[c].append(zf[sel])
            v_ch[c].append(If[sel, c])
    out_z = [np.concatenate(x) if x else np.array([float(np.median(zf))]) for x in z_ch]
    out_v = [np.concatenate(x) if x else np.array([float(np.median(If[:, i]))]) for i, x in enumerate(v_ch)]
    return out_z, out_v


def _backscatter_model(d: np.ndarray, b_inf: float, beta_b: float, gamma: float) -> np.ndarray:
    return b_inf * (1.0 - np.exp(-beta_b * np.power(np.maximum(d, 1e-6), gamma)))


def _fit_backscatter_one_channel(
    z_s: np.ndarray,
    i_s: np.ndarray,
    *,
    opt_iters: int,
) -> tuple[float, float, float]:
    """
    Robust fit of B_inf, beta_B, gamma with bounded search + regularized MAE.
    """
    z_s = np.maximum(np.asarray(z_s, dtype=np.float64), 1e-5)
    i_s = np.asarray(i_s, dtype=np.float64)
    b0 = float(np.percentile(i_s, 96.0))
    b0 = float(np.clip(b0, 0.05, 0.98))
    init = np.array([b0, 0.18, 1.0], dtype=np.float64)
    lb = np.array([0.02, 0.005, 0.65], dtype=np.float64)
    ub = np.array([1.00, 2.50, 1.60], dtype=np.float64)
    lam = 0.05

    def objective(x: np.ndarray) -> np.ndarray:
        pred = _backscatter_model(z_s, x[0], x[1], x[2])
        mae = pred - i_s
        reg = lam * (x - init)
        return np.concatenate([mae, reg])

    res = least_squares(
        objective,
        x0=init,
        bounds=(lb, ub),
        max_nfev=max(80, 40 * max(1, opt_iters)),
        loss="soft_l1",
        f_scale=0.02,
    )
    p = np.clip(res.x, lb, ub)
    return float(p[0]), float(p[1]), float(p[2])


def _backscatter_image(z: np.ndarray, params: np.ndarray) -> np.ndarray:
    out = np.zeros((*z.shape, 3), dtype=np.float64)
    for c in range(3):
        b_inf, beta_b, gamma = params[c]
        out[..., c] = _backscatter_model(z, b_inf, beta_b, gamma)
    return out


def _enforce_backscatter_channel_consistency(params: np.ndarray) -> np.ndarray:
    """
    Keep RGB backscatter params physically close across channels to avoid chromatic blotches.
    """
    p = np.array(params, dtype=np.float64)
    b_inf_mean = float(np.mean(p[:, 0]))
    beta_mean = float(np.mean(p[:, 1]))
    gamma_mean = float(np.mean(p[:, 2]))
    # Gentle pull to channel means (regularization across channels).
    p[:, 0] = 0.8 * p[:, 0] + 0.2 * b_inf_mean
    p[:, 1] = 0.75 * p[:, 1] + 0.25 * beta_mean
    p[:, 2] = 0.8 * p[:, 2] + 0.2 * gamma_mean
    p[:, 0] = np.clip(p[:, 0], 0.02, 1.00)
    p[:, 1] = np.clip(p[:, 1], 0.005, 2.50)
    p[:, 2] = np.clip(p[:, 2], 0.65, 1.60)
    return p


def _spatially_stabilize_backscatter(B: np.ndarray, luma: np.ndarray) -> np.ndarray:
    """
    Spatial consistency prior for B: smooth in texture/noise regions but preserve major edges.
    """
    out = np.zeros_like(B, dtype=np.float64)
    for c in range(3):
        ch = np.clip(B[..., c] * 255.0, 0, 255).astype(np.uint8)
        # Bilateral suppresses blotches while preserving large transitions.
        sm = cv2.bilateralFilter(ch, d=7, sigmaColor=28, sigmaSpace=20).astype(np.float64) / 255.0
        out[..., c] = sm
    # Blend to avoid over-smoothing.
    return np.clip(0.65 * B + 0.35 * out, 0.0, 1.0)


def _lsac_local_space_average_color(
    D: np.ndarray, z: np.ndarray, p: float, eps_z: float, n_iter: int
) -> np.ndarray:
    a = np.zeros_like(D, dtype=np.float64)
    D = D.astype(np.float64)
    z = z.astype(np.float64)
    H, W = D.shape[:2]
    for _ in range(n_iter):
        acc = np.zeros_like(D, dtype=np.float64)
        cnt = np.zeros((H, W, 1), dtype=np.float64)
        for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            y0, y1 = max(0, -dy), H - max(0, dy)
            x0, x1 = max(0, -dx), W - max(0, dx)
            ys, xs = slice(y0, y1), slice(x0, x1)
            yn = slice(y0 + dy, y1 + dy)
            xn = slice(x0 + dx, x1 + dx)
            zc, zn = z[ys, xs], z[yn, xn]
            ok = np.abs(zc - zn) <= eps_z
            av = a[yn, xn]
            m = ok[..., None]
            acc[ys, xs] += av * m
            cnt[ys, xs] += ok[..., None]
        a_mean = acc / np.maximum(cnt, 1e-9)
        a = D * p + a_mean * (1.0 - p)
    return a


def _estimate_beta_d_per_channel(D: np.ndarray, z: np.ndarray) -> np.ndarray:
    """
    Estimate beta_D from direct signal falloff: ln(D) ≈ const - beta_D * z.
    Robust per-channel slope fit with physical clamps.
    """
    zf = z.ravel()
    out = np.zeros(3, dtype=np.float64)
    for c in range(3):
        yf = np.log(np.clip(D[..., c].ravel(), 1e-5, 1.0))
        # robust weighted LS
        w = np.clip(np.exp(-(zf - np.median(zf)) ** 2 / (2 * (np.std(zf) + 1e-6) ** 2)), 0.15, 1.0)
        A0 = np.sum(w * zf * zf) + 1e-9
        A1 = np.sum(w * zf)
        A2 = np.sum(w)
        B0 = np.sum(w * zf * yf)
        B1 = np.sum(w * yf)
        det = A0 * A2 - A1 * A1
        if abs(det) < 1e-9:
            beta = 0.12 + 0.06 * c
        else:
            slope = (B0 * A2 - B1 * A1) / det
            beta = -slope
        out[c] = float(np.clip(beta, 0.02, 0.55))
    return out


def _depth_confidence_map(I: np.ndarray, z: np.ndarray) -> np.ndarray:
    """
    Lower confidence where depth has noisy oscillations not supported by luminance structure.
    """
    lum = np.mean(I, axis=2).astype(np.float32)
    zf = z.astype(np.float32)
    gx_l = cv2.Sobel(lum, cv2.CV_32F, 1, 0, ksize=3)
    gy_l = cv2.Sobel(lum, cv2.CV_32F, 0, 1, ksize=3)
    g_l = np.sqrt(gx_l * gx_l + gy_l * gy_l)
    gx_z = cv2.Sobel(zf, cv2.CV_32F, 1, 0, ksize=3)
    gy_z = cv2.Sobel(zf, cv2.CV_32F, 0, 1, ksize=3)
    g_z = np.sqrt(gx_z * gx_z + gy_z * gy_z)
    # mismatch => lower confidence
    mismatch = np.abs(g_z - 0.8 * g_l)
    mm = mismatch / (np.percentile(mismatch, 90) + 1e-6)
    conf = np.exp(-1.6 * mm)
    conf = cv2.GaussianBlur(conf.astype(np.float32), (0, 0), sigmaX=1.2)
    return np.clip(conf, 0.1, 1.0).astype(np.float64)


def _gray_world(J: np.ndarray) -> np.ndarray:
    flat = J.reshape(-1, 3)
    m = np.mean(flat, axis=0) + 1e-9
    avg = np.mean(m)
    out = J * (avg / m).reshape(1, 1, 3)
    return np.clip(out, 0.0, 1.0)


def _scene_underwater_score(I: np.ndarray) -> tuple[str, float]:
    b, g, r = I[..., 2], I[..., 1], I[..., 0]
    cast = float(np.mean((b + g) - r))
    sat = float(np.mean(np.max(I, axis=2) - np.min(I, axis=2)))
    haze = float(np.mean(1.0 - np.max(I, axis=2)))
    score = 0.58 * cast + 0.27 * haze + 0.15 * (0.45 - sat)
    conf = float(np.clip((score + 0.08) / 0.35, 0.0, 1.0))
    if conf < 0.28:
        return "not_underwater", conf
    if conf < 0.55:
        return "likely_underwater", conf
    return "underwater", conf


def _soft_percentile_enhance(I: np.ndarray) -> np.ndarray:
    out = I.copy()
    for c in range(3):
        lo, hi = np.percentile(out[..., c], [1.0, 99.0])
        if hi <= lo + 1e-6:
            continue
        out[..., c] = np.clip((out[..., c] - lo) / (hi - lo), 0.0, 1.0)
    # bounded channel balancing 0.7..1.3
    m = np.mean(out.reshape(-1, 3), axis=0) + 1e-9
    tgt = np.mean(m)
    s = np.clip(tgt / m, 0.7, 1.3)
    out = np.clip(out * s.reshape(1, 1, 3), 0.0, 1.0)
    return out


def _adaptive_shadow_lift(I: np.ndarray, *, target_p50: float = 0.46) -> np.ndarray:
    """
    Lift globally dark outputs without blowing highlights.
    Applies only when median luma is below target.
    """
    out = np.clip(I, 0.0, 1.0).astype(np.float64)
    lum = np.mean(out, axis=2)
    p50 = float(np.percentile(lum, 50.0))
    if p50 >= target_p50:
        return out
    deficit = float(np.clip((target_p50 - p50) / max(target_p50, 1e-6), 0.0, 1.0))
    gamma = 1.0 - 0.28 * deficit
    gamma = float(np.clip(gamma, 0.72, 1.0))
    lifted = np.power(np.clip(out, 0.0, 1.0), gamma)
    shadow = np.clip((0.42 - lum) / 0.42, 0.0, 1.0) ** 1.35
    shadow_gain = 0.12 * deficit
    lifted = np.clip(lifted + shadow[..., None] * shadow_gain, 0.0, 1.0)
    return lifted


def _auto_choose_quality(
    *,
    scene_conf: float,
    depth_conf: float,
    dyn_range: float,
    clipped_ratio: float,
    cast_score: float,
) -> str:
    severe = (dyn_range < 0.30) or (cast_score > 0.20) or (clipped_ratio > 0.08)
    if severe and depth_conf >= 0.65:
        return "quality"
    if depth_conf < 0.40:
        return "fast"
    return "balanced"


def _run_full_pipeline(
    bgr_u8: np.ndarray,
    *,
    depth_hint_m: float | None,
    quality: str,
    strength: float,
) -> tuple[np.ndarray, dict]:
    req_q = quality if quality in (*_PRESETS.keys(), "auto") else "auto"
    h0, w0 = bgr_u8.shape[:2]
    I0 = _bgr_to_rgb01(bgr_u8)
    scene_label, scene_conf = _scene_underwater_score(I0)
    if scene_label == "not_underwater":
        out = _rgb01_to_bgr_u8(_soft_percentile_enhance(I0))
        return out, {"scene": scene_label, "scene_confidence": scene_conf, "quality": "soft", "fallback": "soft_only"}

    z0, depth_conf, depth_source = _estimate_depth_map(
        I0,
        depth_hint_m=depth_hint_m,
        quality="balanced" if req_q == "auto" else req_q,
    )
    lum = np.mean(I0, axis=2)
    dyn_range = float(np.percentile(lum, 95.0) - np.percentile(lum, 5.0))
    clipped_ratio = float(np.mean((I0 < 0.01) | (I0 > 0.99)))
    cast_score = float(np.mean((I0[..., 2] + I0[..., 1]) - I0[..., 0]))
    q = (
        _auto_choose_quality(
            scene_conf=scene_conf,
            depth_conf=depth_conf,
            dyn_range=dyn_range,
            clipped_ratio=clipped_ratio,
            cast_score=cast_score,
        )
        if req_q == "auto"
        else req_q
    )
    cfg = _PRESETS[q]
    if depth_source.startswith("proxy"):
        soft = _soft_percentile_enhance(I0)
        soft = _adaptive_shadow_lift(soft)
        gate = 0.35 + 0.35 * float(np.clip(strength, 0.05, 1.0))
        out_soft = np.clip(gate * soft + (1.0 - gate) * I0, 0.0, 1.0)
        out = _rgb01_to_bgr_u8(out_soft)
        return out, {
            "scene": scene_label,
            "scene_confidence": scene_conf,
            "depth_confidence": depth_conf,
            "depth_source": depth_source,
            "depth_backend_status": get_depth_backend_status(),
            "depth_status_ui": "fallback proxy enabled",
            "quality": q,
            "quality_requested": req_q,
            "fallback": "proxy_safe_mode",
            "warning": (
                "DepthAnythingV2 checkpoint missing; proxy-safe mode used to prevent halos/blotches. "
                "Set SEATHRU_DEPTHANYTHINGV2_CHECKPOINT for full robust Sea-Thru."
            ),
        }
    max_side = int(cfg["max_side"])
    scale = min(1.0, float(max_side) / max(h0, w0))
    if scale < 1.0:
        nh, nw = int(round(h0 * scale)), int(round(w0 * scale))
        I = cv2.resize(I0.astype(np.float32), (nw, nh), interpolation=cv2.INTER_AREA).astype(np.float64)
        z = cv2.resize(z0.astype(np.float32), (nw, nh), interpolation=cv2.INTER_CUBIC).astype(np.float64)
    else:
        I = I0
        z = z0

    # Edge-aware depth stabilization: noisy depth zones are locally regularized.
    z_smooth = cv2.bilateralFilter(z.astype(np.float32), d=7, sigmaColor=0.35, sigmaSpace=14).astype(np.float64)
    conf_map = _depth_confidence_map(I, z)
    z_eff = conf_map * z + (1.0 - conf_map) * z_smooth

    low, high = cfg["bs_percentiles"]
    bins = int(max(8, min(24, int(cfg["neighborhood_iters"]) + 6)))
    z_ch, v_ch = _collect_robust_backscatter_points(
        I,
        z_eff,
        n_clusters=bins,
        low_pct=float(low),
        high_pct=float(high),
    )
    params = np.zeros((3, 3), dtype=np.float64)
    for c in range(3):
        params[c] = _fit_backscatter_one_channel(z_ch[c], v_ch[c], opt_iters=int(cfg["opt_iters"]))
    params = _enforce_backscatter_channel_consistency(params)

    B = _backscatter_image(z_eff, params)
    B = np.minimum(B, I)
    B = _spatially_stabilize_backscatter(B, np.mean(I, axis=2))
    D = np.clip(I - B, 1e-6, 1.0)

    beta_d = _estimate_beta_d_per_channel(D, z_eff)
    T = np.zeros_like(D)
    for c in range(3):
        T[..., c] = np.exp(-beta_d[c] * z_eff)
    t_floor = 0.12 if q == "fast" else (0.09 if q == "balanced" else 0.07)
    T = np.clip(T, t_floor, 1.0)
    # quality preset affects smoothing effort of transmission
    t_sigma = 0.4 + 0.05 * float(cfg["illum_iters"])
    for c in range(3):
        T[..., c] = cv2.GaussianBlur(T[..., c].astype(np.float32), (0, 0), sigmaX=t_sigma).astype(np.float64)
    T = np.clip(T, t_floor, 1.0)

    # physical reconstruction with hard safety limits
    I_minus_B = np.clip(I - B, 0.0, 1.0)
    J = I_minus_B / T
    # Limit inversion gain to avoid dirty patches and halos in uncertain zones.
    max_gain = np.array([4.0, 4.0, 4.5], dtype=np.float64).reshape(1, 1, 3)
    J = np.minimum(J, I * max_gain)
    J = np.clip(J, 0.0, 1.0)

    # gentle finishing
    J = _soft_percentile_enhance(J)
    # intervention limiter by strength/confidence
    gate = 0.45 + 0.55 * float(np.clip(strength, 0.05, 1.0))
    if scene_label == "likely_underwater":
        gate *= 0.75
    # Local confidence weighting: reduce depth-driven effect only where depth is unreliable.
    local_gate = np.clip(gate * (0.55 + 0.45 * conf_map)[..., None], 0.15, 1.0)
    J = np.clip(local_gate * J + (1.0 - local_gate) * I, 0.0, 1.0)
    J = _adaptive_shadow_lift(J)

    if scale < 1.0:
        J = cv2.resize(J.astype(np.float32), (w0, h0), interpolation=cv2.INTER_CUBIC).astype(np.float64)
    out = _rgb01_to_bgr_u8(J)
    return out, {
        "scene": scene_label,
        "scene_confidence": scene_conf,
        "depth_confidence": depth_conf,
        "depth_source": depth_source,
        "depth_backend_status": get_depth_backend_status(),
        "depth_status_ui": (
            "DepthAnythingV2 loaded"
            if get_depth_backend_status().get("loaded")
            else "fallback proxy enabled"
        ),
        "quality": q,
        "quality_requested": req_q,
        "backscatter_percentiles": [float(low), float(high)],
        "depth_bins": bins,
        "depth_confidence_mean_map": float(np.mean(conf_map)),
        "beta_D_rgb": beta_d.tolist(),
        "fallback": "none",
    }


def run_sea_thru_cvpr2019(
    bgr_u8: np.ndarray,
    *,
    depth_hint_m: float | None = None,
    quality: str = "auto",
    strength: float = 0.7,
    mode: str = "default",
) -> tuple[np.ndarray, dict]:
    """
    Fallback chain:
      full robust pipeline -> simplified robust -> soft enhancement -> original
    """
    h0, w0 = bgr_u8.shape[:2]
    if mode == "strict":
        # strict: require real depth backend (no proxy) and no fallback chain
        st = get_depth_backend_status()
        if not st.get("loaded"):
            raise RuntimeError(
                "seathru strict mode requires loaded DepthAnythingV2 (real depth backend). "
                "Current status: fallback proxy enabled."
            )
        out_s, rep_s = _run_full_pipeline(
            bgr_u8, depth_hint_m=depth_hint_m, quality=quality, strength=strength
        )
        rep_s.update(
            {
                "engine": "seathru",
                "backend": "sea_thru_cvpr2019",
                "mode": "strict",
                "paper": "Sea-Thru + robust percentile backscatter",
                "image_size": [int(w0), int(h0)],
            }
        )
        return out_s, rep_s
    try:
        out, rep = _run_full_pipeline(
            bgr_u8, depth_hint_m=depth_hint_m, quality=quality, strength=strength
        )
        rep.update(
            {
                "engine": "seathru",
                "backend": "sea_thru_cvpr2019",
                "mode": "default",
                "paper": "Sea-Thru + robust percentile backscatter",
                "image_size": [int(w0), int(h0)],
            }
        )
        return out, rep
    except Exception as e1:
        try:
            out2, rep2 = _run_full_pipeline(
                bgr_u8, depth_hint_m=depth_hint_m, quality="fast", strength=min(strength, 0.6)
            )
            rep2.update(
                {
                    "engine": "seathru",
                    "backend": "sea_thru_cvpr2019",
                    "fallback": "fast_after_error",
                    "warning": str(e1),
                    "image_size": [int(w0), int(h0)],
                }
            )
            return out2, rep2
        except Exception as e2:
            try:
                I = _bgr_to_rgb01(bgr_u8)
                out3 = _rgb01_to_bgr_u8(_soft_percentile_enhance(I))
                return out3, {
                    "engine": "seathru",
                    "backend": "sea_thru_cvpr2019",
                    "fallback": "soft_percentile_only",
                    "warning": f"{e1}; {e2}",
                    "image_size": [int(w0), int(h0)],
                }
            except Exception as e3:
                return bgr_u8, {
                    "engine": "seathru",
                    "backend": "sea_thru_cvpr2019",
                    "fallback": "original_returned",
                    "warning": f"{e1}; {e2}; {e3}",
                    "image_size": [int(w0), int(h0)],
                }


# Validate and initialize once on module import (startup path for uvicorn workers).
initialize_depth_backend()
