"""VGG-based perceptual loss for underwater restoration (no extra dataset required)."""
from __future__ import annotations

import torch
import torch.nn as nn
import torch.nn.functional as F

try:
    from torchvision import models
    from torchvision.models import VGG16_Weights
except ImportError:  # pragma: no cover
    models = None  # type: ignore[misc, assignment]
    VGG16_Weights = object()  # type: ignore[misc, assignment]


class VGGPerceptualLoss(nn.Module):
    """
    Первые блоки VGG16 — текстура и локальный контраст без лишней семантики.
    Вход: RGB [0, 1], shape [B, 3, H, W].
    """

    def __init__(self, feature_depth: int = 16) -> None:
        super().__init__()
        if models is None:
            raise RuntimeError("torchvision required for perceptual loss")
        try:
            vgg = models.vgg16(weights=VGG16_Weights.IMAGENET1K_V1)  # type: ignore[attr-defined]
        except (TypeError, AttributeError):
            vgg = models.vgg16(pretrained=True)  # type: ignore[call-arg]
        self.slice = nn.Sequential(*list(vgg.features.children())[:feature_depth]).eval()
        for p in self.slice.parameters():
            p.requires_grad = False
        self.register_buffer("mean", torch.tensor([0.485, 0.456, 0.406]).view(1, 3, 1, 1))
        self.register_buffer("std", torch.tensor([0.229, 0.224, 0.225]).view(1, 3, 1, 1))

    def _norm(self, x: torch.Tensor) -> torch.Tensor:
        m = self.mean.to(dtype=x.dtype, device=x.device)
        s = self.std.to(dtype=x.dtype, device=x.device)
        return (x - m) / s

    def forward(self, pred: torch.Tensor, target: torch.Tensor) -> torch.Tensor:
        # pred/target in [0,1]; уменьшаем разрешение для скорости и памяти
        if pred.shape[-1] > 384:
            pred = F.interpolate(pred, scale_factor=0.5, mode="bilinear", align_corners=False)
            target = F.interpolate(target, scale_factor=0.5, mode="bilinear", align_corners=False)
        fp = self.slice(self._norm(pred))
        ft = self.slice(self._norm(target))
        return F.l1_loss(fp, ft)
