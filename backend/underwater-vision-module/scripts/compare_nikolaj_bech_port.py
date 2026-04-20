#!/usr/bin/env python3
"""
Compare Python port matrix to upstream npm package (optional).

Requires: Node.js and `npm install underwater-image-color-correction` in this directory
or globally resolvable by node. If node/pkg missing, only prints Python matrix.

Usage:
  cd backend/underwater-vision-module
  export PYTHONPATH=src
  python scripts/compare_nikolaj_bech_port.py
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


def main() -> None:
    w, h = 4, 3
    flat = _fixed_rgba_flat(w, h)
    py_m, hue = get_color_filter_matrix_rgba(flat, w, h)
    print('python_hue_shift', hue)
    print('python_matrix_first5', [round(x, 8) for x in py_m[:5]])

    pix_list = flat.reshape(-1).astype(int).tolist()
    js_m = _node_matrix(pix_list, w, h)
    if js_m is None:
        print('OK (python-only); install node + npm i underwater-image-color-correction to diff.')
        return
    eps = 1e-6
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


if __name__ == '__main__':
    main()
