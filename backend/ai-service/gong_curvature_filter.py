"""
Gaussian / TV / MC curvature filter update schemes from:
Yuanhao Gong's CurvatureFilter project (Python port by Tan Wei Hou).
Reference: Gong & Sbalzarini, IEEE TIP 2017 — cited as [57] in
Li et al., JMSE 2025 (10.3390/jmse13091820).

Licensed per upstream repository; used here only for GC minimization
as in the JMSE paper (Eq. 10–11, Algorithm 1 step 2).
"""
from __future__ import annotations

import numpy as np


def update_gc(inputimg: np.ndarray, rowbegin: int, colbegin: int) -> None:
    inputimg_ij = inputimg[rowbegin:-1:2, colbegin:-1:2]

    d1 = (inputimg[rowbegin - 1 : -2 : 2, colbegin:-1:2] + inputimg[rowbegin + 1 :: 2, colbegin:-1:2]) / 2.0 - inputimg[
        rowbegin:-1:2, colbegin:-1:2
    ]
    d2 = (inputimg[rowbegin:-1:2, colbegin - 1 : -2 : 2] + inputimg[rowbegin:-1:2, colbegin + 1 :: 2]) / 2.0 - inputimg[
        rowbegin:-1:2, colbegin:-1:2
    ]
    d3 = (inputimg[rowbegin - 1 : -2 : 2, colbegin - 1 : -2 : 2] + inputimg[rowbegin + 1 :: 2, colbegin + 1 :: 2]) / 2.0 - inputimg[
        rowbegin:-1:2, colbegin:-1:2
    ]
    d4 = (inputimg[rowbegin - 1 : -2 : 2, colbegin + 1 :: 2] + inputimg[rowbegin + 1 :: 2, colbegin - 1 : -2 : 2]) / 2.0 - inputimg[
        rowbegin:-1:2, colbegin:-1:2
    ]

    d5 = (
        inputimg[rowbegin - 1 : -2 : 2, colbegin:-1:2]
        + inputimg[rowbegin:-1:2, colbegin - 1 : -2 : 2]
        + inputimg[rowbegin - 1 : -2 : 2, colbegin - 1 : -2 : 2]
    ) / 3.0 - inputimg[rowbegin:-1:2, colbegin:-1:2]
    d6 = (
        inputimg[rowbegin - 1 : -2 : 2, colbegin:-1:2]
        + inputimg[rowbegin:-1:2, colbegin + 1 :: 2]
        + inputimg[rowbegin - 1 : -2 : 2, colbegin + 1 :: 2]
    ) / 3.0 - inputimg[rowbegin:-1:2, colbegin:-1:2]
    d7 = (
        inputimg[rowbegin:-1:2, colbegin - 1 : -2 : 2]
        + inputimg[rowbegin + 1 :: 2, colbegin:-1:2]
        + inputimg[rowbegin + 1 :: 2, colbegin - 1 : -2 : 2]
    ) / 3.0 - inputimg[rowbegin:-1:2, colbegin:-1:2]
    d8 = (
        inputimg[rowbegin:-1:2, colbegin + 1 :: 2]
        + inputimg[rowbegin + 1 :: 2, colbegin:-1:2]
        + inputimg[rowbegin + 1 :: 2, colbegin + 1 :: 2]
    ) / 3.0 - inputimg[rowbegin:-1:2, colbegin:-1:2]

    d = d1 * (np.abs(d1) <= np.abs(d2)) + d2 * (np.abs(d2) < np.abs(d1))
    d = d * (np.abs(d) <= np.abs(d3)) + d3 * (np.abs(d3) < np.abs(d))
    d = d * (np.abs(d) <= np.abs(d4)) + d4 * (np.abs(d4) < np.abs(d))
    d = d * (np.abs(d) <= np.abs(d5)) + d5 * (np.abs(d5) < np.abs(d))
    d = d * (np.abs(d) <= np.abs(d6)) + d6 * (np.abs(d6) < np.abs(d))
    d = d * (np.abs(d) <= np.abs(d7)) + d7 * (np.abs(d7) < np.abs(d))
    d = d * (np.abs(d) <= np.abs(d8)) + d8 * (np.abs(d8) < np.abs(d))

    inputimg_ij[...] += d


def gc_filter_gray(img: np.ndarray, total_iter: int = 10, dtype: type = np.float32) -> np.ndarray:
    """
    Apply Gaussian-curvature filter (Gong & Sbalzarini) per channel.
    Same sweep pattern as upstream cf_interface.cf_filter(..., 'gc').
    """
    filtered = np.copy(img.astype(dtype, copy=False))
    for _ in range(total_iter):
        update_gc(filtered, 1, 1)
        update_gc(filtered, 2, 2)
        update_gc(filtered, 1, 2)
        update_gc(filtered, 2, 1)
    return filtered
