"""
Декодирование JPEG/PNG → классический пайплайн и/или ONNX U-Net.
Выход: JPEG bytes.
"""
from __future__ import annotations

import os
from pathlib import Path

import cv2
import numpy as np
from PIL import Image
import io

try:
    import onnxruntime as ort
except ImportError:
    ort = None


def _models_dir() -> Path:
    return Path(os.environ.get("UNDERWATER_MODELS_PATH", Path(__file__).resolve().parent / "models"))


def _decode_image(data: bytes) -> np.ndarray:
    """BGR uint8, HxWx3."""
    arr = np.frombuffer(data, dtype=np.uint8)
    img = cv2.imdecode(arr, cv2.IMREAD_UNCHANGED)
    if img is None:
        pil = Image.open(io.BytesIO(data))
        pil = pil.convert("RGBA") if pil.mode in ("RGBA", "P") else pil.convert("RGB")
        rgb = np.array(pil)
        if rgb.ndim == 2:
            img = cv2.cvtColor(rgb, cv2.COLOR_GRAY2BGR)
        elif rgb.shape[2] == 4:
            img = cv2.cvtColor(rgb, cv2.COLOR_RGBA2BGR)
        else:
            img = cv2.cvtColor(rgb, cv2.COLOR_RGB2BGR)
    return _ensure_bgr3(img)


def _ensure_bgr3(bgr: np.ndarray) -> np.ndarray:
    """OpenCV color ops ожидают 3 канала; PNG/HEIC через PIL могут дать GRAY или BGRA."""
    if bgr.ndim == 2:
        return cv2.cvtColor(bgr, cv2.COLOR_GRAY2BGR)
    c = bgr.shape[2] if bgr.ndim >= 3 else 0
    if c == 4:
        return cv2.cvtColor(bgr, cv2.COLOR_BGRA2BGR)
    if c == 1:
        return cv2.cvtColor(bgr, cv2.COLOR_GRAY2BGR)
    if c != 3:
        raise ValueError(f"Unsupported channel count: {c}")
    return bgr


def _encode_jpeg(bgr: np.ndarray, quality: int = 92) -> bytes:
    ok, buf = cv2.imencode(".jpg", bgr, [cv2.IMWRITE_JPEG_QUALITY, quality])
    if not ok:
        raise ValueError("JPEG encode failed")
    return buf.tobytes()


def _env_int(name: str, default: int) -> int:
    try:
        return int(os.environ.get(name, str(default)).strip())
    except ValueError:
        return default


def _resize_work_bgr_max_side(bgr: np.ndarray, max_side: int) -> tuple[np.ndarray, tuple[int, int]]:
    """
    Уменьшает кадр для тяжёлых пайплайнов. max_side <= 0 — без изменений.
    Возвращает (рабочий_bgr, (orig_w, orig_h)).
    """
    h0, w0 = bgr.shape[:2]
    if max_side <= 0:
        return bgr, (w0, h0)
    m = max(h0, w0)
    if m <= max_side:
        return bgr, (w0, h0)
    scale = max_side / float(m)
    nw = max(1, int(round(w0 * scale)))
    nh = max(1, int(round(h0 * scale)))
    small = cv2.resize(bgr, (nw, nh), interpolation=cv2.INTER_AREA)
    return small, (w0, h0)


def _restore_bgr_size(bgr: np.ndarray, orig_wh: tuple[int, int]) -> np.ndarray:
    w0, h0 = orig_wh
    h, w = bgr.shape[:2]
    if w == w0 and h == h0:
        return bgr
    return cv2.resize(bgr, (w0, h0), interpolation=cv2.INTER_CUBIC)


def _successive_color_correction(bgr: np.ndarray, eta: float = 3.0) -> np.ndarray:
    """
    Lee et al., Symmetry 2020 (10.3390/sym12081220):
    - First correction: Eq. (3), UWB with std-ratio.
    - Second correction: Eq. (8)-(9), adaptive normalization.
    """
    rgb = cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB).astype(np.float32) / 255.0
    g = rgb[:, :, 1]
    m = [float(rgb[:, :, c].mean()) for c in range(3)]
    s = [float(rgb[:, :, c].std()) for c in range(3)]
    eps = 1e-6

    # Eq. (3)
    im = rgb.copy()
    for c in (0, 2):  # red and blue; green is reference
        gain = s[1] / max(s[c], eps)
        im[:, :, c] = rgb[:, :, c] + gain * (m[1] - m[c]) * (1.0 - rgb[:, :, c]) * g
    im = np.clip(im, 0.0, 1.0)

    # Eq. (8)-(9)
    m2 = [float(im[:, :, c].mean()) for c in range(3)]
    s2 = [float(im[:, :, c].std()) for c in range(3)]
    smax = max(s2)
    ip = im.copy()
    for c in range(3):
        kappa = eta * smax / max(s2[c], eps)
        denom = 2.0 * kappa * max(s2[c], eps)
        ip[:, :, c] = (im[:, :, c] - m2[c] + kappa * s2[c]) / max(denom, eps)
    ip = np.clip(ip, 0.0, 1.0)

    # Step (3) in paper summary: align means to green channel.
    mg = float(ip[:, :, 1].mean())
    for c in (0, 2):
        ip[:, :, c] = np.clip(ip[:, :, c] + (mg - float(ip[:, :, c].mean())), 0.0, 1.0)
    return cv2.cvtColor((ip * 255.0).astype(np.uint8), cv2.COLOR_RGB2BGR)


def _estimate_backscatter_and_t(
    bgr_corr: np.ndarray,
    depth_m: float,
) -> tuple[np.ndarray, np.ndarray, float]:
    """
    Superpixel DCP is approximated by patch-DCP for runtime.
    Follows paper logic: dark channel, A estimation, adaptive omega = mean(A).
    """
    im = bgr_corr.astype(np.float32) / 255.0
    # dark channel over channels then local patch min
    dark = np.min(im, axis=2)
    patch = 15
    kernel = cv2.getStructuringElement(cv2.MORPH_RECT, (patch, patch))
    dark_patch = cv2.erode(dark, kernel)

    # Backscatter light A from top 0.1% bright dark pixels
    h, w = dark_patch.shape
    n = max(1, int(h * w * 0.001))
    idx = np.argpartition(dark_patch.reshape(-1), -n)[-n:]
    pix = im.reshape(-1, 3)[idx]
    A = np.mean(pix, axis=0).astype(np.float32)
    A = np.clip(A, 0.05, 0.98)

    # Eq. (14): adaptive omega by mean backscatter.
    omega = float(np.clip(A.mean(), 0.55, 0.95))
    # Slightly increase haze weight for deeper scenes.
    d = float(np.clip(depth_m, 0.0, 60.0))
    omega = float(np.clip(omega + 0.10 * (d / 60.0), 0.55, 0.98))

    A3 = A.reshape(1, 1, 3)
    norm = im / np.maximum(A3, 1e-6)
    dark_norm = cv2.erode(np.min(norm, axis=2), kernel)
    t = 1.0 - omega * dark_norm
    return A, t.astype(np.float32), omega


def _recover_with_adaptive_dcp(
    bgr_corr: np.ndarray,
    depth_m: float,
) -> np.ndarray:
    """
    Eq. (15): J = (I - A) / max(t, t0) + A.
    """
    im = bgr_corr.astype(np.float32) / 255.0
    A, t, _ = _estimate_backscatter_and_t(bgr_corr, depth_m=depth_m)
    t0 = 0.10
    t3 = np.maximum(t[..., None], t0)
    A3 = A.reshape(1, 1, 3)
    rec = (im - A3) / t3 + A3
    rec = np.clip(rec, 0.0, 1.0)
    return (rec * 255.0).astype(np.uint8)


def _classical_enhance(bgr: np.ndarray, depth_m: float, strength: float) -> np.ndarray:
    """
    Color correction by Lee et al. Symmetry 2020 logic:
    successive color correction + adaptive DCP restoration.
    """
    s = float(np.clip(strength, 0.0, 1.0))
    corr = _successive_color_correction(bgr, eta=3.0)
    rec = _recover_with_adaptive_dcp(corr, depth_m=depth_m)
    # Keep user-controllable intensity of effect.
    out = (bgr.astype(np.float32) * (1.0 - s) + rec.astype(np.float32) * s).astype(np.uint8)
    return out


def _select_onnx_model_path() -> Path | None:
    """
    Приоритет в auto: underwater_berman.onnx → icvgip_color.onnx → underwater.onnx.
    icvgip_color: Jain et al. ICVGIP'22 / arXiv:2211.14821 (encoder–decoder color CNN), train_icvgip_color.py.
    Переопределение: UNDERWATER_ONNX=путь к одному .onnx.
    """
    if os.environ.get("UNDERWATER_DISABLE_ONNX", "").strip().lower() in ("1", "true", "yes", "on"):
        return None
    md = _models_dir()
    env_one = os.environ.get("UNDERWATER_ONNX", "").strip()
    if env_one:
        p = Path(env_one)
        if not p.is_file():
            p = md / env_one
        return p if p.is_file() else None
    pref = os.environ.get("UNDERWATER_ONNX_PREFERENCE", "auto").lower()
    b = md / "underwater_berman.onnx"
    c = md / "icvgip_color.onnx"
    u = md / "underwater.onnx"
    if pref == "berman":
        return b if b.is_file() else None
    if pref == "icvgip":
        return c if c.is_file() else None
    if pref == "unet":
        return u if u.is_file() else None
    if b.is_file():
        return b
    if c.is_file():
        return c
    if u.is_file():
        return u
    return None


def _run_onnx(bgr: np.ndarray, session: ort.InferenceSession) -> np.ndarray:
    """Ожидается один вход float32 [1,3,H,W] или [1,H,W,3], значения 0–1."""
    inp = session.get_inputs()[0]
    name = inp.name
    shape = inp.shape
    h0, w0 = bgr.shape[:2]

    def is_nchw(sh):
        return len(sh) == 4 and sh[1] == 3

    nchw = is_nchw(shape)
    if len(shape) == 4 and shape[2] not in (None, "H") and isinstance(shape[2], int):
        th, tw = int(shape[2]), int(shape[3])
    elif len(shape) == 4 and shape[1] == 3 and isinstance(shape[2], int):
        th, tw = int(shape[2]), int(shape[3])
    else:
        th = tw = 256

    rgb = cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB).astype(np.float32) / 255.0
    resized = cv2.resize(rgb, (tw, th), interpolation=cv2.INTER_AREA)
    if nchw:
        tensor = np.transpose(resized, (2, 0, 1))[np.newaxis, ...].astype(np.float32)
    else:
        tensor = resized[np.newaxis, ...].astype(np.float32)

    out = session.run(None, {name: tensor})[0]
    if out.ndim == 4 and out.shape[1] == 3:
        out_hwc = np.transpose(out[0], (1, 2, 0))
    else:
        out_hwc = out[0]
    out_hwc = np.clip(out_hwc, 0, 1)
    out_rgb = cv2.resize(out_hwc, (w0, h0), interpolation=cv2.INTER_CUBIC)
    out_bgr = (out_rgb * 255.0).clip(0, 255).astype(np.uint8)
    out_bgr = cv2.cvtColor(out_bgr, cv2.COLOR_RGB2BGR)
    return out_bgr


def process(
    image_bytes: bytes,
    depth_m: float = 10.0,
    strength: float = 0.7,
    use_ai: bool = True,
    pipeline: str = "default",
    gpt_preserve_blues: float | None = None,
    gpt_detail_boost: float | None = None,
    gpt_warmth_bias: float | None = None,
    gpt_dehaze_strength: float | None = None,
    gpt_red_recovery_strength: float | None = None,
    gpt_noise_reduction: float | None = None,
) -> bytes:
    if not image_bytes or len(image_bytes) < 32:
        raise ValueError("Image too small or empty")
    bgr = _decode_image(image_bytes)
    pl = (pipeline or "default").strip().lower()
    if pl in ("jmse1820", "jmse_1820", "li2025", "article2"):
        from jmse_li_2025 import enhance_jmse_li_2025

        # JMSE на 2K+ CPU: десятки секунд; 1024 — компромисс скорость/качество (см. UNDERWATER_JMSE_MAX_SIDE).
        jmse_max = _env_int("UNDERWATER_JMSE_MAX_SIDE", 1024)
        work, orig_wh = _resize_work_bgr_max_side(bgr, jmse_max)
        out_bgr = enhance_jmse_li_2025(work)
        out_bgr = _restore_bgr_size(out_bgr, orig_wh)
        return _encode_jpeg(out_bgr)
    if pl in ("article3", "scirep2026", "s41598", "lepcha2026"):
        from scirep_2026_article3 import enhance_scirep_article3

        a3_max = _env_int("UNDERWATER_ARTICLE3_MAX_SIDE", 1536)
        work, orig_wh = _resize_work_bgr_max_side(bgr, a3_max)
        out_bgr = enhance_scirep_article3(work)
        out_bgr = _restore_bgr_size(out_bgr, orig_wh)
        return _encode_jpeg(out_bgr)
    if pl in ("gpt", "gpt_restore", "underwater_gpt"):
        from gpt_underwater_pipeline import underwater_color_restore

        st = float(np.clip(strength, 0.0, 1.0))
        kw: dict = {"strength": st}
        if gpt_preserve_blues is not None:
            kw["preserve_blues"] = float(gpt_preserve_blues)
        if gpt_detail_boost is not None:
            kw["detail_boost"] = float(gpt_detail_boost)
        if gpt_warmth_bias is not None:
            kw["warmth_bias"] = float(gpt_warmth_bias)
        if gpt_dehaze_strength is not None:
            kw["dehaze_strength"] = float(gpt_dehaze_strength)
        if gpt_red_recovery_strength is not None:
            kw["red_recovery_strength"] = float(gpt_red_recovery_strength)
        if gpt_noise_reduction is not None:
            kw["noise_reduction"] = float(gpt_noise_reduction)
        gpt_max = _env_int("UNDERWATER_GPT_MAX_SIDE", 1536)
        work, orig_wh = _resize_work_bgr_max_side(bgr, gpt_max)
        out_bgr = underwater_color_restore(work, **kw)
        out_bgr = _restore_bgr_size(out_bgr, orig_wh)
        return _encode_jpeg(out_bgr)

    classical = _classical_enhance(bgr, depth_m, strength)

    onnx_path = _select_onnx_model_path()
    if use_ai and ort is not None and onnx_path is not None:
        try:
            session = ort.InferenceSession(
                str(onnx_path),
                providers=["CPUExecutionProvider"],
            )
            ai_bgr = _run_onnx(bgr, session)
            # Результат сети смешиваем с классикой и оригиналом — стабильнее цвет
            blend_ai = float(np.clip(strength, 0.15, 1.0))
            merged = (classical.astype(np.float32) * (1.0 - blend_ai) + ai_bgr.astype(np.float32) * blend_ai)
            merged = np.clip(merged, 0, 255).astype(np.uint8)
            return _encode_jpeg(merged)
        except Exception:
            pass

    return _encode_jpeg(classical)
