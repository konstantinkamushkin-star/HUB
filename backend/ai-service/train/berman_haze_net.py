"""
Нейросеть в духе Berman et al., TPAMI 2020 (SQUID / haze-lines):
CNN предсказывает глобальные параметры сцены (фоновый свет A, ω для оценки передачи),
восстановление — явная физическая формула однократного рассеяния + лёгкий CNN-refine.

Это не полный порт MATLAB haze-lines, а обучаемый аналог «оценка глобальных
параметров воды + аналитическое восстановление», совместимый с end-to-end обучением.
Вход/выход: RGB float32 [B, 3, H, W], диапазон 0..1.
"""
from __future__ import annotations

import torch
import torch.nn as nn
import torch.nn.functional as F


def single_scatter_restore(
    I: torch.Tensor,
    A: torch.Tensor,
    omega: torch.Tensor,
    t_min: float = 0.05,
) -> torch.Tensor:
    """
    I = J * t + A * (1 - t)  =>  J = (I - A * (1 - t)) / t
    Оценка карты передачи t по аналогии с dark channel: t = 1 - ω * min_c(I_c / A_c).
    I, A: [B,3,H,W] и [B,3,1,1]; omega: [B,1,1,1].
    """
    eps = 1e-4
    A = A.clamp(eps, 1.0 - eps)
    norm = I / A
    dark = norm.min(dim=1, keepdim=True).values
    t = 1.0 - omega * dark
    t = t.clamp(t_min, 1.0)
    J = (I - A * (1.0 - t)) / t.clamp_min(t_min)
    return J.clamp(0.0, 1.0)


class BermanHazeLinesNet(nn.Module):
    """
    Лёгкий encoder → глобальные A (3), ω (1); затем single_scatter_restore + мелкий refine.
    """

    def __init__(self, base: int = 48, refine_channels: int = 24):
        super().__init__()
        self.stem = nn.Sequential(
            nn.Conv2d(3, base, 3, padding=1),
            nn.ReLU(inplace=True),
            nn.Conv2d(base, base, 3, stride=2, padding=1),
            nn.ReLU(inplace=True),
            nn.Conv2d(base, base * 2, 3, stride=2, padding=1),
            nn.ReLU(inplace=True),
            nn.Conv2d(base * 2, base * 2, 3, stride=2, padding=1),
            nn.ReLU(inplace=True),
            nn.Conv2d(base * 2, base * 4, 3, stride=2, padding=1),
            nn.ReLU(inplace=True),
        )
        hid = base * 4
        self.gap = nn.AdaptiveAvgPool2d(1)
        self.fc_A = nn.Linear(hid, 3)
        self.fc_omega = nn.Linear(hid, 1)
        # Доп. мягкая цветовая коррекция (как «остаточный» слой после физики)
        self.refine = nn.Sequential(
            nn.Conv2d(3, refine_channels, 3, padding=1),
            nn.ReLU(inplace=True),
            nn.Conv2d(refine_channels, 3, 3, padding=1),
        )

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        feat = self.stem(x)
        g = self.gap(feat).flatten(1)
        A = torch.sigmoid(self.fc_A(g)).view(-1, 3, 1, 1) * 0.82 + 0.09
        omega = torch.sigmoid(self.fc_omega(g)).view(-1, 1, 1, 1) * 0.88 + 0.06
        J = single_scatter_restore(x, A, omega)
        delta = torch.tanh(self.refine(J))
        out = (J + 0.12 * delta).clamp(0.0, 1.0)
        return out
