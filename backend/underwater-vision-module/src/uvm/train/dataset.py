from __future__ import annotations

import hashlib
from pathlib import Path

import cv2
import numpy as np
import torch
from torch.utils.data import Dataset

from uvm.data.sample_record import SampleRecord
from uvm.pipeline.blocks import FeatureExtractor, PostProcessor, RefineBranch, RestorationBranch


def _pick_input_path(r: SampleRecord) -> str | None:
    for p in (r.image_left, r.tif_file, r.image_right, r.raw_file):
        if p and Path(p).is_file():
            return p
    return None


class ManifestPhotoDataset(Dataset):
    def __init__(
        self,
        records: list[SampleRecord],
        size: int = 512,
        use_pseudo_target: bool = True,
        cache_dir: str | None = None,
        *,
        pseudo_strength: float = 0.92,
        pseudo_clahe_boost: float = 1.25,
        pseudo_red_scale: float = 1.65,
        pseudo_wb_lo: float = 0.72,
        pseudo_wb_hi: float = 1.38,
        pseudo_saturation_gain: float = 0.22,
    ) -> None:
        self.size = size
        self.use_pseudo_target = use_pseudo_target
        self.pseudo_strength = float(pseudo_strength)
        self.pseudo_clahe_boost = float(pseudo_clahe_boost)
        self.pseudo_red_scale = float(pseudo_red_scale)
        self.pseudo_wb_lo = float(pseudo_wb_lo)
        self.pseudo_wb_hi = float(pseudo_wb_hi)
        self.pseudo_saturation_gain = float(pseudo_saturation_gain)
        self._pseudo_cache_tag = (
            f"s{self.pseudo_strength:.3f}_c{self.pseudo_clahe_boost:.2f}_r{self.pseudo_red_scale:.2f}_"
            f"w{self.pseudo_wb_lo:.2f}-{self.pseudo_wb_hi:.2f}_sat{self.pseudo_saturation_gain:.2f}"
        )
        self.cache_dir = Path(cache_dir) if cache_dir else None
        if self.cache_dir:
            self.cache_dir.mkdir(parents=True, exist_ok=True)
        self.rest = RestorationBranch()
        self.refine = RefineBranch()
        self.post = PostProcessor()
        self.feats = FeatureExtractor()

        self.samples: list[tuple[str, str | None, str]] = []
        for r in records:
            inp = _pick_input_path(r)
            if not inp:
                continue
            tgt = r.target_reference if r.target_reference and Path(r.target_reference).is_file() else None
            self.samples.append((inp, tgt, r.sample_id))

    def __len__(self) -> int:
        return len(self.samples)

    def _pseudo_target(self, bgr: np.ndarray, sample_id: str) -> np.ndarray:
        st = self.pseudo_strength

        def _run_pipeline(inp: np.ndarray) -> np.ndarray:
            feats = self.feats.run(inp, depth_hint_m=None)
            x, _ = self.rest.run(
                inp,
                feats,
                st,
                clahe_boost=self.pseudo_clahe_boost,
                red_scale=self.pseudo_red_scale,
                wb_lo=self.pseudo_wb_lo,
                wb_hi=self.pseudo_wb_hi,
            )
            x = self.refine.run(x)
            return self.post.run(x, st, saturation_gain=self.pseudo_saturation_gain)

        if self.cache_dir is None:
            return _run_pipeline(bgr)

        key = hashlib.sha1(f"{sample_id}|{self._pseudo_cache_tag}".encode("utf-8")).hexdigest()[:24]
        p = self.cache_dir / f"{key}.jpg"
        if p.exists():
            x = cv2.imread(str(p), cv2.IMREAD_COLOR)
            if x is not None:
                return x
        x = _run_pipeline(bgr)
        cv2.imwrite(str(p), x, [cv2.IMWRITE_JPEG_QUALITY, 95])
        return x

    def __getitem__(self, idx: int):
        inp_path, tgt_path, sample_id = self.samples[idx]
        inp = cv2.imread(inp_path, cv2.IMREAD_COLOR)
        if inp is None:
            inp = np.zeros((self.size, self.size, 3), dtype=np.uint8)

        # depth channel: from depth map (if provided later), now proxy from luminance
        g = cv2.cvtColor(inp, cv2.COLOR_BGR2GRAY).astype(np.float32) / 255.0
        depth_proxy = cv2.GaussianBlur(1.0 - g, (11, 11), 0)

        if tgt_path:
            tgt = cv2.imread(tgt_path, cv2.IMREAD_COLOR)
            if tgt is None:
                tgt = self._pseudo_target(inp, sample_id) if self.use_pseudo_target else inp.copy()
        else:
            tgt = self._pseudo_target(inp, sample_id) if self.use_pseudo_target else inp.copy()

        inp = cv2.resize(inp, (self.size, self.size), interpolation=cv2.INTER_LINEAR)
        tgt = cv2.resize(tgt, (self.size, self.size), interpolation=cv2.INTER_LINEAR)
        depth_proxy = cv2.resize(depth_proxy, (self.size, self.size), interpolation=cv2.INTER_LINEAR)

        inp_rgb = cv2.cvtColor(inp, cv2.COLOR_BGR2RGB).astype(np.float32) / 255.0
        tgt_rgb = cv2.cvtColor(tgt, cv2.COLOR_BGR2RGB).astype(np.float32) / 255.0

        x = np.concatenate([inp_rgb, depth_proxy[..., None]], axis=2)
        x = torch.from_numpy(x).permute(2, 0, 1).float()
        y = torch.from_numpy(tgt_rgb).permute(2, 0, 1).float()
        d = torch.from_numpy(depth_proxy[None, ...]).float()
        return x, y, d

