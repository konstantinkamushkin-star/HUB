"""Ленивая загрузка UNet-чекпоинтов для API (ai1 / ai2)."""
from __future__ import annotations

import os
from pathlib import Path

import cv2
import numpy as np
import torch

from uvm.api.jpeg_utils import encode_jpeg_hex
from uvm.train.infer_utils import bgr_to_model_input, tensor_to_bgr
from uvm.train.model import DepthAwareUNet

MODULE_ROOT = Path(__file__).resolve().parents[3]


def _first_existing(paths: list[Path]) -> Path | None:
    for p in paths:
        if p.is_file():
            return p
    return None


def _candidates_ai1() -> list[Path]:
    env = os.environ.get("UVM_CKPT_AI1", "").strip()
    out: list[Path] = []
    if env:
        out.append(Path(env).expanduser())
    root = MODULE_ROOT
    out.extend(
        [
            root / "checkpoints_loso_satil" / "best.pt",
            root / "checkpoints_color_v2" / "best.pt",
            root / "checkpoints_smoke" / "best.pt",
            root / "checkpoints" / "best.pt",
            # запасной вес — часто отличается от best по эпохе
            root / "checkpoints_loso_satil" / "last.pt",
            root / "checkpoints_color_v2" / "last.pt",
            root / "checkpoints_smoke" / "last.pt",
        ]
    )
    return out


def _candidates_ai2() -> list[Path]:
    env = os.environ.get("UVM_CKPT_AI2", "").strip()
    out: list[Path] = []
    if env:
        out.append(Path(env).expanduser())
    root = MODULE_ROOT
    out.extend(
        [
            root / "checkpoints_color_v2" / "best.pt",
            root / "checkpoints_loso_satil" / "best.pt",
            root / "checkpoints_color_v2" / "last.pt",
            root / "checkpoints_loso_satil" / "last.pt",
            root / "checkpoints_smoke" / "best.pt",
            root / "checkpoints_smoke" / "last.pt",
            root / "checkpoints" / "best.pt",
        ]
    )
    return out


def input_size() -> int:
    return int(os.environ.get("UVM_INPUT_SIZE", "512"))


class NNInferenceService:
    def __init__(self) -> None:
        self._device = self._pick_device()
        self._size = input_size()
        self._models: dict[str, DepthAwareUNet] = {}
        self._loaded_from: dict[str, str] = {}

    @staticmethod
    def _pick_device() -> torch.device:
        if torch.cuda.is_available():
            return torch.device("cuda")
        if getattr(torch.backends, "mps", None) and torch.backends.mps.is_available():
            return torch.device("mps")
        return torch.device("cpu")

    def available(self, slot: str) -> bool:
        return self._resolve_ckpt(slot) is not None

    def _resolve_ckpt(self, slot: str) -> Path | None:
        """ИИ2 не должен брать тот же файл, что ИИ1, если есть другой чекпоинт."""
        if slot == "ai1":
            return _first_existing(_candidates_ai1())
        if slot == "ai2":
            ai1_path = _first_existing(_candidates_ai1())
            reserved = ai1_path.resolve() if ai1_path is not None else None
            for p in _candidates_ai2():
                if not p.is_file():
                    continue
                if reserved is not None and p.resolve() == reserved:
                    continue
                return p
            # только один файл весов на машине — дублируем (в отчёте будет видно)
            return _first_existing(_candidates_ai2())
        return None

    def _get_model(self, slot: str) -> DepthAwareUNet:
        if slot in self._models:
            return self._models[slot]
        ckpt = self._resolve_ckpt(slot)
        if ckpt is None:
            raise FileNotFoundError(f"No checkpoint for {slot}")
        model = DepthAwareUNet(in_ch=4, out_ch=3).to(self._device)
        try:
            blob = torch.load(ckpt, map_location=self._device, weights_only=False)
        except TypeError:
            blob = torch.load(ckpt, map_location=self._device)
        model.load_state_dict(blob["model"])
        model.eval()
        self._models[slot] = model
        self._loaded_from[slot] = str(ckpt.resolve())
        return model

    def infer_bgr(self, bgr: np.ndarray, slot: str) -> tuple[np.ndarray, dict]:
        model = self._get_model(slot)
        x, orig_hw = bgr_to_model_input(bgr, self._size, self._device)
        with torch.no_grad():
            out = model(x)
        out_bgr = tensor_to_bgr(out, orig_hw)
        meta = {
            "engine": slot,
            "checkpoint": self._loaded_from.get(slot),
            "input_size": self._size,
            "device": str(self._device),
        }
        return out_bgr, meta


_nn_singleton: NNInferenceService | None = None


def get_nn_service() -> NNInferenceService:
    global _nn_singleton
    if _nn_singleton is None:
        _nn_singleton = NNInferenceService()
    return _nn_singleton
