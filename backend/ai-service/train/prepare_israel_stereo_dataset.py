#!/usr/bin/env python3
"""
Собирает пары input/target для обучения из израильского стерео-датасета
(Katzaa, Satil, Nachsholim, Michmoret): 57 пар по сайтам.

В каждой папке image_set_* ожидается один из вариантов:
  • LFT_*resizedUndistort.tif + distanceFromCamera.mat (dist_map_l), или
  • LFT_*.tif + xyzPoints.mat, или
  • только xyzPoints.mat с полями imgLeftUndistorted, xyzPointsLeft (как у Nachsholim).

Глубина для классического target: медиана dist_map_l / Z из xyz, иначе типичная глубина сайта.

Пример:
  cd backend/ai-service/train
  pip install scipy tifffile tqdm
  python prepare_israel_stereo_dataset.py \\
    --out ./data_israel \\
    --roots ~/Downloads/Satil ~/Downloads/Katzaa ~/Downloads/Nachsholim ~/Downloads/Michmoret
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

import cv2
import numpy as np

try:
    from tqdm import tqdm
except ImportError:
    tqdm = None

try:
    import scipy.io as sio
except ImportError:
    print("Установите: pip install scipy", file=sys.stderr)
    sys.exit(1)

try:
    import tifffile
except ImportError:
    print("Установите: pip install tifffile", file=sys.stderr)
    sys.exit(1)

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))
from inference import process

# Типичные глубины (м), если нет карт глубины
SITE_DEFAULT_DEPTH_M = {
    "katzaa": 12.0,
    "satil": 25.0,
    "nachsholim": 4.5,
    "michmoret": 11.0,
    "default": 10.0,
}


def _site_key(name: str) -> str:
    n = name.lower()
    if "katzaa" in n or "katz" in n:
        return "katzaa"
    if "satil" in n:
        return "satil"
    if "nachshol" in n:
        return "nachsholim"
    if "michmore" in n or "michmoret" in n:
        return "michmoret"
    return "default"


def _list_image_sets(site_root: Path) -> list[Path]:
    sets = []
    for p in sorted(site_root.iterdir()):
        if p.is_dir() and re.match(r"image_set_\d+", p.name):
            sets.append(p)
    return sets


def _find_left_tif(st: Path) -> Path | None:
    cands = sorted(st.glob("LFT_*resizedUndistort.tif"))
    if cands:
        return cands[0]
    cands = sorted(st.glob("LFT_*.tif"))
    return cands[0] if cands else None


def _find_right_tif(st: Path) -> Path | None:
    cands = sorted(st.glob("RGT_*resizedUndistort.tif"))
    if cands:
        return cands[0]
    cands = sorted(st.glob("RGT_*.tif"))
    return cands[0] if cands else None


def _safe_loadmat(path: Path):
    try:
        return sio.loadmat(str(path))
    except (OSError, ValueError) as e:
        print(f"WARN corrupt or unreadable MAT {path.name}: {e}", file=sys.stderr)
        return None


def _depth_from_distance_mat(st: Path, side: str) -> float | None:
    p = st / "distanceFromCamera.mat"
    if not p.is_file():
        return None
    m = _safe_loadmat(p)
    if m is None:
        return None
    key = "dist_map_l" if side == "left" else "dist_map_r"
    if key not in m:
        return None
    d = np.asarray(m[key], dtype=np.float64)
    valid = d[np.isfinite(d) & (d > 0.1) & (d < 200)]
    if valid.size == 0:
        return None
    return float(np.median(valid))


def _depth_from_xyz_mat(st: Path, side: str) -> float | None:
    p = st / "xyzPoints.mat"
    if not p.is_file():
        return None
    m = _safe_loadmat(p)
    if m is None:
        return None
    key = "xyzPointsLeft" if side == "left" else "xyzPointsRight"
    if key not in m:
        return None
    z = np.asarray(m[key], dtype=np.float64)[:, :, 2]
    valid = z[np.isfinite(z) & (z > 0.1) & (z < 120)]
    if valid.size == 0:
        return None
    return float(np.median(valid))


def _rgb_from_xyz_mat(st: Path, side: str) -> np.ndarray | None:
    """BGR uint8 из imgLeftUndistorted / imgRightUndistorted (float 0..1)."""
    p = st / "xyzPoints.mat"
    if not p.is_file():
        return None
    m = _safe_loadmat(p)
    if m is None:
        return None
    key = "imgLeftUndistorted" if side == "left" else "imgRightUndistorted"
    if key not in m:
        return None
    img = np.asarray(m[key], dtype=np.float64)
    if img.ndim != 3 or img.shape[2] < 3:
        return None
    rgb = np.clip(img[:, :, :3], 0.0, 1.0)
    rgb_u8 = (rgb * 255.0).round().astype(np.uint8)
    return cv2.cvtColor(rgb_u8, cv2.COLOR_RGB2BGR)


def _rgb_from_tif(path: Path, tone: str) -> np.ndarray:
    """BGR uint8."""
    a = tifffile.imread(str(path))
    if a.ndim == 2:
        a = np.stack([a, a, a], axis=-1)
    if a.shape[2] > 3:
        a = a[:, :, :3]
    if a.dtype == np.uint16:
        if tone == "linear":
            bgr = (a.astype(np.float32) / 65535.0 * 255.0).clip(0, 255).astype(np.uint8)
        else:
            bgr = np.zeros_like(a, dtype=np.uint8)
            for c in range(3):
                ch = a[:, :, c].astype(np.float32)
                lo, hi = np.percentile(ch, (1.0, 99.0))
                if hi <= lo:
                    hi = lo + 1.0
                bgr[:, :, c] = ((ch - lo) / (hi - lo) * 255.0).clip(0, 255).astype(np.uint8)
        # tifffile often RGB order
        bgr = cv2.cvtColor(bgr, cv2.COLOR_RGB2BGR)
    elif a.dtype in (np.float32, np.float64):
        x = np.clip(a[:, :, :3], 0.0, 1.0)
        u8 = (x * 255.0).round().astype(np.uint8)
        bgr = cv2.cvtColor(u8, cv2.COLOR_RGB2BGR)
    else:
        bgr = cv2.cvtColor(np.asarray(a[:, :, :3], dtype=np.uint8), cv2.COLOR_RGB2BGR)
    return bgr


def _load_pair_bgr(st: Path, side: str, tone: str) -> tuple[np.ndarray, str]:
    """Returns (bgr, source_tag)."""
    tif_path = _find_left_tif(st) if side == "left" else _find_right_tif(st)
    if tif_path is not None:
        return _rgb_from_tif(tif_path, tone=tone), f"tif:{tif_path.name}"
    mat_bgr = _rgb_from_xyz_mat(st, side)
    if mat_bgr is not None:
        return mat_bgr, "mat:imgUndistorted"
    raise FileNotFoundError(f"No TIF or xyz RGB in {st}")


def _estimate_depth_m(st: Path, site_root: Path, side: str) -> float:
    d = _depth_from_distance_mat(st, side)
    if d is not None:
        return d
    d = _depth_from_xyz_mat(st, side)
    if d is not None:
        return d
    sk = _site_key(site_root.name)
    return SITE_DEFAULT_DEPTH_M.get(sk, SITE_DEFAULT_DEPTH_M["default"])


def main():
    ap = argparse.ArgumentParser(description="Israel stereo → DiveHub train data")
    ap.add_argument(
        "--roots",
        nargs="+",
        type=Path,
        default=[
            Path.home() / "Downloads" / "Satil",
            Path.home() / "Downloads" / "Katzaa",
            Path.home() / "Downloads" / "Nachsholim",
            Path.home() / "Downloads" / "Michmoret",
        ],
        help="Корни сайтов (папки с image_set_* и stereoParams*.mat)",
    )
    ap.add_argument("--out", type=Path, default=Path("./data_israel"))
    ap.add_argument("--side", choices=("left", "right"), default="left")
    ap.add_argument("--strength", type=float, default=0.72)
    ap.add_argument("--no_targets", action="store_true", help="Только JPEG в input/, без process()")
    ap.add_argument("--max_side", type=int, default=2048, help="Длинная сторона перед JPEG (0 = без ресайза)")
    ap.add_argument("--tone", choices=("percentile", "linear"), default="percentile")
    args = ap.parse_args()

    out_in = args.out / "input"
    out_tg = args.out / "target"
    out_in.mkdir(parents=True, exist_ok=True)
    if not args.no_targets:
        out_tg.mkdir(parents=True, exist_ok=True)

    manifest: list[dict] = []
    jobs: list[tuple[Path, Path, str]] = []

    for site_root in args.roots:
        site_root = site_root.expanduser().resolve()
        if not site_root.is_dir():
            print(f"Пропуск (нет папки): {site_root}")
            continue
        site_slug = re.sub(r"[^a-zA-Z0-9]+", "_", site_root.name).strip("_").lower() or "site"
        for st in _list_image_sets(site_root):
            stem = st.name
            out_name = f"{site_slug}__{stem}__{args.side}.jpg"
            jobs.append((site_root, st, out_name))

    iterator = jobs
    if tqdm is not None:
        iterator = tqdm(jobs, desc="pairs")

    for site_root, st, out_name in iterator:
        try:
            bgr, src_tag = _load_pair_bgr(st, args.side, args.tone)
        except Exception as e:
            print(f"SKIP {st}: {e}", file=sys.stderr)
            continue

        try:
            depth_m = _estimate_depth_m(st, site_root, args.side)
        except Exception as e:
            print(f"WARN depth {st}: {e}; using site default", file=sys.stderr)
            sk = _site_key(site_root.name)
            depth_m = SITE_DEFAULT_DEPTH_M.get(sk, SITE_DEFAULT_DEPTH_M["default"])

        if args.max_side and args.max_side > 0:
            h, w = bgr.shape[:2]
            m = max(h, w)
            if m > args.max_side:
                scale = args.max_side / m
                bgr = cv2.resize(bgr, (int(w * scale), int(h * scale)), interpolation=cv2.INTER_AREA)

        ok, buf = cv2.imencode(".jpg", bgr, [cv2.IMWRITE_JPEG_QUALITY, 92])
        if not ok:
            continue
        jpeg_bytes = buf.tobytes()

        out_path_in = out_in / out_name
        out_path_in.write_bytes(jpeg_bytes)

        if not args.no_targets:
            try:
                tgt_bytes = process(
                    jpeg_bytes,
                    depth_m=depth_m,
                    strength=args.strength,
                    use_ai=False,
                )
            except Exception as e:
                print(f"process fail {out_name}: {e}", file=sys.stderr)
                continue
            (out_tg / out_name).write_bytes(tgt_bytes)

        manifest.append(
            {
                "file": out_name,
                "site_folder": site_root.name,
                "image_set": st.name,
                "rgb_source": src_tag,
                "depth_m_estimate": round(depth_m, 3),
                "side": args.side,
            }
        )

    meta_path = args.out / "manifest_israel_stereo.json"
    meta_path.write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    print(f"Готово: {len(manifest)} пар → {args.out.resolve()}")
    print(f"Манифест: {meta_path}")
    if not args.no_targets:
        print("Дальше: python train.py --data_dir ./data_israel --epochs 80 --export ../models/underwater.onnx")


if __name__ == "__main__":
    main()
