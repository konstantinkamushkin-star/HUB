#!/usr/bin/env python3
"""Sanity check for Nikolaj Bech color matrix port (see README)."""
from __future__ import annotations

import math
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'src'))

import numpy as np

from uvm.pipeline.nikolaj_bech_color_correction import get_color_filter_matrix_rgba


def main() -> None:
    # Small fixed RGBA buffer (deterministic)
    h, w = 2, 3
    rgba = np.zeros((h * w, 4), dtype=np.uint8)
    rgba[:, 0] = [10, 20, 30, 100, 110, 120]
    rgba[:, 1] = [40, 50, 60, 130, 140, 150]
    rgba[:, 2] = [70, 80, 90, 160, 170, 180]
    rgba[:, 3] = 255
    m, hue = get_color_filter_matrix_rgba(rgba, w, h)
    assert len(m) == 20
    assert all(math.isfinite(x) for x in m)
    assert isinstance(hue, int)
    print('matrix_ok', 'hue_shift', hue, 'first5', [round(x, 6) for x in m[:5]])


if __name__ == '__main__':
    main()
