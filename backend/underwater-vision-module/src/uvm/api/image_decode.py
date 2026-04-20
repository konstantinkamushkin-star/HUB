from __future__ import annotations

import cv2
import numpy as np


def decode_upload_bgr(data: bytes) -> tuple[np.ndarray | None, str]:
    """Return (bgr, decoder_tag).

    WebP decoding can differ subtly across OpenCV/libwebp builds; for WEBP inputs
    we prefer Pillow (when installed) to keep cross-platform results closer.
    """
    if len(data) >= 12 and data[:4] == b'RIFF' and data[8:12] == b'WEBP':
        try:
            from PIL import Image  # type: ignore

            im = Image.open(__import__('io').BytesIO(data))
            im = im.convert('RGB')
            rgb = np.asarray(im)
            bgr = cv2.cvtColor(rgb, cv2.COLOR_RGB2BGR)
            return bgr, 'pillow_webp'
        except Exception:
            pass

    arr = np.frombuffer(data, dtype=np.uint8)
    bgr = cv2.imdecode(arr, cv2.IMREAD_COLOR)
    return bgr, 'opencv_imdecode'
