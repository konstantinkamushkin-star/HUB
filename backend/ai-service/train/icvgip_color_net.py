"""
Jain, Matta & Mitra — «Towards Realistic Underwater Dataset Generation and Color Restoration»,
ICVGIP 2022, DOI 10.1145/3571600.3571630, arXiv:2211.14821.

В статье восстановление цвета выполняет отдельная «simple but effective CNN», обученная на
доменно-адаптированных данных (image-to-image synthetic→real в оригинале не реализован здесь).

Этот модуль — компактный encoder–decoder для color restoration (вход/выход RGB 0–1, фикс. 256×256 в ONNX).
"""
from __future__ import annotations

import torch
import torch.nn as nn


class ICVGIPColorRestNet(nn.Module):
    """Лёгкий hourglass: 3× downsample, bottleneck, 3× upsample → sigmoid RGB."""

    def __init__(self, base: int = 48):
        super().__init__()
        self.encoder = nn.Sequential(
            nn.Conv2d(3, base, 3, padding=1),
            nn.ReLU(inplace=True),
            nn.Conv2d(base, base, 3, stride=2, padding=1),
            nn.BatchNorm2d(base),
            nn.ReLU(inplace=True),
            nn.Conv2d(base, base * 2, 3, stride=2, padding=1),
            nn.BatchNorm2d(base * 2),
            nn.ReLU(inplace=True),
            nn.Conv2d(base * 2, base * 4, 3, stride=2, padding=1),
            nn.BatchNorm2d(base * 4),
            nn.ReLU(inplace=True),
        )
        self.mid = nn.Sequential(
            nn.Conv2d(base * 4, base * 4, 3, padding=1),
            nn.BatchNorm2d(base * 4),
            nn.ReLU(inplace=True),
        )
        self.decoder = nn.Sequential(
            nn.ConvTranspose2d(base * 4, base * 2, 4, stride=2, padding=1),
            nn.BatchNorm2d(base * 2),
            nn.ReLU(inplace=True),
            nn.ConvTranspose2d(base * 2, base, 4, stride=2, padding=1),
            nn.BatchNorm2d(base),
            nn.ReLU(inplace=True),
            nn.ConvTranspose2d(base, base, 4, stride=2, padding=1),
            nn.BatchNorm2d(base),
            nn.ReLU(inplace=True),
            nn.Conv2d(base, 3, 3, padding=1),
            nn.Sigmoid(),
        )

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        z = self.mid(self.encoder(x))
        return self.decoder(z)
