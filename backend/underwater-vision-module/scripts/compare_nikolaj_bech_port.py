#!/usr/bin/env python3
"""
Compare Python port matrix to upstream npm package (optional).

Requires: Node.js and `npm install underwater-image-color-correction` in this directory
or globally resolvable by node. If node/pkg missing, only prints Python matrix.

Usage:
  cd backend/underwater-vision-module
  export PYTHONPATH=src
  python scripts/compare_nikolaj_bech_port.py
  python scripts/compare_nikolaj_bech_port.py --readme-examples

  ``--readme-examples`` loads upstream ``example1.jpg`` … ``example3.jpg`` from
  ``node_modules/underwater-image-color-correction/`` (same assets as the GitHub README)
  and checks that the 20 matrix coefficients match npm for OpenCV-decoded RGBA.
  Requires ``opencv-python-headless`` (or ``cv2``) in the environment.
"""
from __future__ import annotations

import json
import os
import subprocess
import sys

import numpy as np

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
SRC = os.path.join(ROOT, 'src')
sys.path.insert(0, SRC)

from uvm.pipeline.nikolaj_bech_color_correction import get_color_filter_matrix_rgba  # noqa: E402


def _fixed_rgba_flat(w: int, h: int) -> np.ndarray:
    rgba = np.zeros((h * w, 4), dtype=np.uint8)
    k = 0
    for y in range(h):
        for x in range(w):
            rgba[k, 0] = (x * 37 + y * 11) % 256
            rgba[k, 1] = (x * 19 + y * 23) % 256
            rgba[k, 2] = (x * 29 + y * 7) % 256
            rgba[k, 3] = 255
            k += 1
    return rgba


def _node_matrix(pixels: list[int], width: int, height: int) -> list[float] | None:
    # Resolve from ./node_modules under ROOT (cwd for node is ROOT).
    node_js = r"""
const path = require('path');
const fs = require('fs');
const pkg = path.join(process.cwd(), 'node_modules', 'underwater-image-color-correction');
const g = require(pkg);
const j = JSON.parse(fs.readFileSync(0, 'utf8'));
const m = g(j.pixels, j.width, j.height);
console.log(JSON.stringify(m));
"""
    local_pkg = os.path.join(ROOT, 'node_modules', 'underwater-image-color-correction')
    if not os.path.isdir(local_pkg):
        print(
            'node_compare_skipped: missing',
            local_pkg,
            '— run: cd',
            ROOT,
            '&& npm install underwater-image-color-correction',
            file=sys.stderr,
        )
        return None
    payload = {'pixels': pixels, 'width': width, 'height': height}
    try:
        out = subprocess.check_output(
            ['node', '-e', node_js],
            input=json.dumps(payload).encode('utf-8'),
            cwd=ROOT,
            timeout=30,
        )
        return json.loads(out.decode('utf-8').strip())
    except (subprocess.CalledProcessError, FileNotFoundError, json.JSONDecodeError) as e:
        print('node_compare_skipped:', e, file=sys.stderr)
        return None


def _readme_example_paths() -> list[str]:
    d = os.path.join(ROOT, 'node_modules', 'underwater-image-color-correction')
    return [os.path.join(d, f'example{i}.jpg') for i in (1, 2, 3)]


def _compare_readme_examples(eps: float) -> int:
    try:
        import cv2  # type: ignore
    except ImportError:
        print(
            'readme_examples_skipped: install opencv-python-headless (cv2) to run --readme-examples',
            file=sys.stderr,
        )
        return 0

    paths = _readme_example_paths()
    missing = [p for p in paths if not os.path.isfile(p)]
    if missing:
        print(
            'readme_examples_skipped: missing',
            ', '.join(missing),
            '— npm install underwater-image-color-correction in',
            ROOT,
            file=sys.stderr,
        )
        return 0

    if _node_matrix([0, 0, 0, 255], 1, 1) is None:
        print('readme_examples_skipped: node/npm package unavailable', file=sys.stderr)
        return 0

    exit_code = 0
    for p in paths:
        bgr = cv2.imread(p)
        if bgr is None:
            print('readme_examples_fail: imread', p, file=sys.stderr)
            exit_code = 1
            continue
        h, w = bgr.shape[:2]
        rgb = bgr[..., ::-1]
        rgba = np.concatenate([rgb, np.full((h, w, 1), 255, dtype=np.uint8)], axis=-1)
        flat = rgba.reshape(-1, 4)
        py_m, hue = get_color_filter_matrix_rgba(flat, w, h)
        pix_list = flat.reshape(-1).astype(int).tolist()
        js_m = _node_matrix(pix_list, w, h)
        assert js_m is not None
        bad = sum(1 for a, b in zip(py_m, js_m) if abs(float(a) - float(b)) > eps)
        tag = os.path.basename(p)
        if bad:
            print(f'FAIL {tag} {w}x{h} hue={hue}: {bad} coeffs differ (eps={eps})')
            exit_code = 1
        else:
            print(f'OK {tag} {w}x{h} hue={hue}: matrix matches npm')
    return exit_code


def main() -> None:
    eps = 1e-6
    readme = '--readme-examples' in sys.argv[1:]

    w, h = 4, 3
    flat = _fixed_rgba_flat(w, h)
    py_m, hue = get_color_filter_matrix_rgba(flat, w, h)
    print('python_hue_shift', hue)
    print('python_matrix_first5', [round(x, 8) for x in py_m[:5]])

    pix_list = flat.reshape(-1).astype(int).tolist()
    js_m = _node_matrix(pix_list, w, h)
    if js_m is None:
        print('OK (python-only); install node + npm i underwater-image-color-correction to diff.')
        if readme:
            sys.exit(_compare_readme_examples(eps))
        return
    bad = 0
    for i, (a, b) in enumerate(zip(py_m, js_m)):
        if abs(float(a) - float(b)) > eps:
            bad += 1
            if bad <= 5:
                print(f'mismatch[{i}] py={a} js={b} diff={a - b}')
    if bad:
        print(f'FAIL: {bad} coefficients differ by > {eps}')
        sys.exit(1)
    print('OK: Python matrix matches npm package within', eps)

    if readme:
        rc = _compare_readme_examples(eps)
        if rc:
            sys.exit(rc)


if __name__ == '__main__':
    main()
