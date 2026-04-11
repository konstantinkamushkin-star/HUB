from __future__ import annotations

import numpy as np

from .blocks import Preprocessor, FeatureExtractor, RestorationBranch, RefineBranch, PostProcessor


class UnderwaterPipeline:
    def __init__(self) -> None:
        self.pre = Preprocessor()
        self.fe = FeatureExtractor()
        self.rest = RestorationBranch()
        self.refine = RefineBranch()
        self.post = PostProcessor()

    def process_photo(
        self,
        bgr: np.ndarray,
        strength: float = 0.7,
        depth_hint_m: float | None = None,
        *,
        clahe_boost: float = 0.0,
        red_scale: float = 1.0,
    ):
        x = self.pre.run(bgr)
        feats = self.fe.run(x, depth_hint_m=depth_hint_m)
        x, rest_meta = self.rest.run(x, feats, strength, clahe_boost=clahe_boost, red_scale=red_scale)
        x = self.refine.run(x)
        x = self.post.run(x, strength)
        report = {
            'depth_proxy_m': feats['depth_proxy_m'],
            'edge_var': feats['edge_var'],
            'restoration': rest_meta,
            'strength': strength,
            'clahe_boost': clahe_boost,
            'red_scale': red_scale,
        }
        return x, report
