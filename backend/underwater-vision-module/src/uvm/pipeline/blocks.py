from __future__ import annotations

import cv2
import numpy as np


class Preprocessor:
    def run(self, bgr: np.ndarray) -> np.ndarray:
        if max(bgr.shape[:2]) > 2048:
            s = 2048.0 / max(bgr.shape[:2])
            bgr = cv2.resize(bgr, (int(bgr.shape[1] * s), int(bgr.shape[0] * s)), interpolation=cv2.INTER_AREA)
        return bgr


class FeatureExtractor:
    def run(self, bgr: np.ndarray, depth_hint_m: float | None = None) -> dict:
        gray = cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY)
        edge = cv2.Laplacian(gray, cv2.CV_32F)
        depth_proxy = float(np.clip((np.mean(gray) / 255.0) * 30.0, 0.0, 30.0)) if depth_hint_m is None else float(depth_hint_m)
        return {'edge_var': float(edge.var()), 'depth_proxy_m': depth_proxy}


class RestorationBranch:
    def run(
        self,
        bgr: np.ndarray,
        feats: dict,
        strength: float,
        *,
        clahe_boost: float = 0.0,
        red_scale: float = 1.0,
        wb_lo: float = 0.85,
        wb_hi: float = 1.20,
    ) -> tuple[np.ndarray, dict]:
        img = bgr.astype(np.float32) / 255.0
        # mild white-balance pull (wider wb_lo/wb_hi = stronger teacher for training)
        m = img.mean(axis=(0, 1)) + 1e-6
        g = float(m.mean())
        wb = np.clip((g / m), wb_lo, wb_hi)
        img = np.clip(img * wb.reshape(1, 1, 3), 0, 1)

        # depth-aware red compensation
        d = feats['depth_proxy_m']
        red_gain = 1.0 + min(d / 20.0, 1.0) * 0.18 * strength * float(red_scale)
        img[:, :, 2] = np.clip(img[:, :, 2] * red_gain, 0, 1)

        # dehaze on luminance only
        lab = cv2.cvtColor((img * 255).astype(np.uint8), cv2.COLOR_BGR2LAB)
        l_ch, a_ch, b_ch = cv2.split(lab)
        clip_limit = float(np.clip(1.5 + clahe_boost, 1.0, 4.5))
        l_ch = cv2.createCLAHE(clipLimit=clip_limit, tileGridSize=(8, 8)).apply(l_ch)
        out = cv2.cvtColor(cv2.merge([l_ch, a_ch, b_ch]), cv2.COLOR_LAB2BGR)
        return out, {'red_gain': red_gain}


class RefineBranch:
    def run(self, bgr: np.ndarray) -> np.ndarray:
        out = cv2.fastNlMeansDenoisingColored(bgr, None, 2, 2, 5, 11)
        return out


class PostProcessor:
    def run(self, bgr: np.ndarray, strength: float, *, saturation_gain: float = 0.08) -> np.ndarray:
        hsv = cv2.cvtColor(bgr, cv2.COLOR_BGR2HSV).astype(np.float32)
        hsv[:, :, 1] = np.clip(hsv[:, :, 1] * (1.0 + float(saturation_gain) * strength), 0, 255)
        out = cv2.cvtColor(hsv.astype(np.uint8), cv2.COLOR_HSV2BGR)
        return out
