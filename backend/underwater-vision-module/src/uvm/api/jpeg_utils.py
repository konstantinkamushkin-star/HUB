"""JPEG helpers for API responses (OpenCV imencode)."""

from __future__ import annotations

import cv2
import numpy as np


def encode_jpeg_hex(bgr: np.ndarray, quality: int = 95) -> str:
    ok, enc = cv2.imencode(".jpg", bgr, [cv2.IMWRITE_JPEG_QUALITY, quality])
    if not ok:
        raise RuntimeError("jpeg encode failed")
    return enc.tobytes().hex()
