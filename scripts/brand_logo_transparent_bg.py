#!/usr/bin/env python3
"""Make DiveHub raster logos use transparency instead of white matting.

1) Flood from image edges through near-white (>= edge_white) and clear alpha.
2) Flood from edges through "outside" (dark or already transparent), then remove
   any near-white region that touches that outside (fixes bottom corner wedges
   cut off from the edge by black pixels).
3) Bottom-left / bottom-right triangles (frame vs rounded art): remove
   low-saturation light pixels (gray anti-alias), keeping saturated blues intact.
4) Bottom band: very pale blue/white fringe along the rounded bottom (high min
   channel, modest chroma); skips the flat bottom center so interior water stays.
"""

from __future__ import annotations

import argparse
import sys
from collections import deque
from pathlib import Path

import numpy as np
from PIL import Image


def _bfs_white_from_edges(
    rgb: np.ndarray, alpha: np.ndarray, thresh: int
) -> np.ndarray:
    h, w = rgb.shape[:2]
    vis = np.zeros((h, w), dtype=bool)
    q: deque[tuple[int, int]] = deque()

    def white(r: int, g: int, b: int) -> bool:
        return r >= thresh and g >= thresh and b >= thresh

    for x in range(w):
        for y in (0, h - 1):
            if white(*rgb[y, x]):
                q.append((y, x))
                vis[y, x] = True
    for y in range(h):
        for x in (0, w - 1):
            if not vis[y, x] and white(*rgb[y, x]):
                q.append((y, x))
                vis[y, x] = True

    dirs = ((1, 0), (-1, 0), (0, 1), (0, -1))
    while q:
        y, x = q.popleft()
        for dy, dx in dirs:
            ny, nx = y + dy, x + dx
            if 0 <= ny < h and 0 <= nx < w and not vis[ny, nx] and white(*rgb[ny, nx]):
                vis[ny, nx] = True
                q.append((ny, nx))
    return vis


def _bfs_outside_from_edges(
    rgb: np.ndarray, alpha: np.ndarray, dark_max: int, alpha_cut: int
) -> np.ndarray:
    h, w = rgb.shape[:2]
    vis = np.zeros((h, w), dtype=bool)
    q: deque[tuple[int, int]] = deque()

    def outside(y: int, x: int) -> bool:
        if alpha[y, x] < alpha_cut:
            return True
        r, g, b = rgb[y, x]
        return max(int(r), int(g), int(b)) <= dark_max

    for x in range(w):
        for y in (0, h - 1):
            if outside(y, x):
                q.append((y, x))
                vis[y, x] = True
    for y in range(h):
        for x in (0, w - 1):
            if not vis[y, x] and outside(y, x):
                q.append((y, x))
                vis[y, x] = True

    dirs = ((1, 0), (-1, 0), (0, 1), (0, -1))
    while q:
        y, x = q.popleft()
        for dy, dx in dirs:
            ny, nx = y + dy, x + dx
            if 0 <= ny < h and 0 <= nx < w and not vis[ny, nx] and outside(ny, nx):
                vis[ny, nx] = True
                q.append((ny, nx))
    return vis


def _bfs_light_touching_outside(
    rgb: np.ndarray,
    alpha: np.ndarray,
    outside: np.ndarray,
    light_min: int,
    flood_min: int,
) -> np.ndarray:
    """Near-white pixels connected to `outside` (4-neigh), flooding only through
    pixels with min(R,G,B) >= flood_min and alpha > 0."""
    h, w = rgb.shape[:2]
    dirs = ((1, 0), (-1, 0), (0, 1), (0, -1))

    def light_enough(y: int, x: int, lo: int) -> bool:
        if alpha[y, x] < 10:
            return False
        r, g, b = rgb[y, x]
        return min(int(r), int(g), int(b)) >= lo

    seeds = np.zeros((h, w), dtype=bool)
    for y in range(h):
        for x in range(w):
            if not light_enough(y, x, light_min):
                continue
            for dy, dx in dirs:
                ny, nx = y + dy, x + dx
                if 0 <= ny < h and 0 <= nx < w and outside[ny, nx]:
                    seeds[y, x] = True
                    break

    vis = np.zeros((h, w), dtype=bool)
    q: deque[tuple[int, int]] = deque()
    for y, x in zip(*np.where(seeds)):
        vis[y, x] = True
        q.append((y, x))

    while q:
        y, x = q.popleft()
        for dy, dx in dirs:
            ny, nx = y + dy, x + dx
            if 0 <= ny < h and 0 <= nx < w and not vis[ny, nx] and light_enough(ny, nx, flood_min):
                vis[ny, nx] = True
                q.append((ny, nx))
    return vis


def _bottom_corner_gray_matte(
    rgb: np.ndarray,
    alpha: np.ndarray,
    *,
    radius_frac: float = 0.17,
    mean_min: float = 168.0,
    chroma_max: int = 52,
    alpha_min: int = 200,
) -> np.ndarray:
    """Mask pixels in bottom corner isosceles triangles (apex at image corners).

    Anti-alias between black and the rounded blue panel often appears as neutral
    gray (230,230,230) or near-neutral (210,215,220). Real water edge uses
    saturated blues like (50,95,200) — large channel spread, so we keep it.
    """
    h, w = rgb.shape[:2]
    if h < 8 or w < 8:
        return np.zeros((h, w), dtype=bool)

    yy = np.arange(h, dtype=np.int32)[:, None]
    xx = np.arange(w, dtype=np.int32)[None, :]
    dy = (h - 1) - yy
    rad = max(3, int(min(h, w) * radius_frac))
    left_tri = (xx + dy) <= rad
    right_tri = ((w - 1 - xx) + dy) <= rad
    tri = left_tri | right_tri

    r = rgb[:, :, 0].astype(np.int32)
    gch = rgb[:, :, 1].astype(np.int32)
    b = rgb[:, :, 2].astype(np.int32)
    mn = np.minimum(np.minimum(r, gch), b)
    mx = np.maximum(np.maximum(r, gch), b)
    chroma = mx - mn
    mean = (r + gch + b) / 3.0

    return (
        tri
        & (alpha >= alpha_min)
        & (mean >= mean_min)
        & (chroma <= chroma_max)
    )


def _bottom_pale_fringe(
    rgb: np.ndarray,
    alpha: np.ndarray,
    *,
    row_frac: float = 0.042,
    alpha_min: int = 200,
    min_channel: int = 135,
    mean_min: float = 184.0,
    max_channel: int = 215,
    chroma_max: int = 95,
    center_exclude_frac: float = 0.09,
) -> np.ndarray:
    """Pale (almost white) blue pixels on the last rows — anti-alias vs black.

    These sit along the curved bottom-left/right (e.g. 200,215,248) and read as
    white dots; deep water (low min channel) is preserved. A horizontal band
    around the image center is excluded so the bottom middle of the art stays.
    """
    h, w = rgb.shape[:2]
    if h < 10:
        return np.zeros((h, w), dtype=bool)

    rows = max(2, min(int(round(h * row_frac)) + 2, h // 3))
    band = np.arange(h, dtype=np.int32)[:, None] >= (h - rows)
    cx = (w - 1) * 0.5
    half_ex = max(1.0, w * center_exclude_frac)
    mid = np.abs(np.arange(w, dtype=np.float32)[None, :] - cx) > half_ex

    r = rgb[:, :, 0].astype(np.int32)
    gch = rgb[:, :, 1].astype(np.int32)
    b = rgb[:, :, 2].astype(np.int32)
    mn = np.minimum(np.minimum(r, gch), b)
    mx = np.maximum(np.maximum(r, gch), b)
    chroma = mx - mn
    mean = (r + gch + b) / 3.0

    return (
        band
        & mid
        & (alpha >= alpha_min)
        & (mn >= min_channel)
        & (mean >= mean_min)
        & (mx >= max_channel)
        & (chroma <= chroma_max)
    )


def process_rgba(
    arr: np.ndarray,
    *,
    edge_white: int = 248,
    dark_max: int = 58,
    alpha_outside: int = 25,
    # Seeds must catch anti-aliased matting (e.g. 241,241,241) next to black;
    # flood stays slightly lower to include fringe without crossing real art.
    light_seed_min: int = 245,
    light_flood_min: int = 238,
    corner_pass: bool = True,
) -> np.ndarray:
    out = np.array(arr, copy=True)
    rgb = out[:, :, :3]
    alpha = out[:, :, 3]

    edge_white_mask = _bfs_white_from_edges(rgb, alpha, edge_white)
    out[edge_white_mask, 3] = 0

    if not corner_pass:
        return out

    rgb = out[:, :, :3]
    alpha = out[:, :, 3]
    outside = _bfs_outside_from_edges(rgb, alpha, dark_max, alpha_outside)
    corner_white = _bfs_light_touching_outside(
        rgb, alpha, outside, light_seed_min, light_flood_min
    )
    out[corner_white, 3] = 0

    rgb = out[:, :, :3]
    alpha = out[:, :, 3]
    gray_corner = _bottom_corner_gray_matte(rgb, alpha)
    out[gray_corner, 3] = 0

    rgb = out[:, :, :3]
    alpha = out[:, :, 3]
    pale = _bottom_pale_fringe(rgb, alpha)
    out[pale, 3] = 0
    return out


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("paths", nargs="+", type=Path)
    p.add_argument(
        "--no-corner-pass",
        action="store_true",
        help="Only remove edge-connected white (for e.g. wordmark)",
    )
    args = p.parse_args()

    for path in args.paths:
        if not path.is_file():
            print(f"missing: {path}", file=sys.stderr)
            return 1
        corner = not args.no_corner_pass
        if path.name == "logo-wordmark.png":
            corner = False
        im = Image.open(path).convert("RGBA")
        out = process_rgba(np.array(im), corner_pass=corner)
        Image.fromarray(out).save(path, format="PNG", optimize=True, compress_level=9)
        print(path)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
