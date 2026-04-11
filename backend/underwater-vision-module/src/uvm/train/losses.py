from __future__ import annotations

from dataclasses import dataclass

import torch
import torch.nn as nn
import torch.nn.functional as F


def charbonnier(x: torch.Tensor, y: torch.Tensor, eps: float = 1e-3) -> torch.Tensor:
    return torch.mean(torch.sqrt((x - y) ** 2 + eps**2))


def edge_loss(x: torch.Tensor, y: torch.Tensor) -> torch.Tensor:
    sx = x[:, :, :, 1:] - x[:, :, :, :-1]
    sy = y[:, :, :, 1:] - y[:, :, :, :-1]
    tx = x[:, :, 1:, :] - x[:, :, :-1, :]
    ty = y[:, :, 1:, :] - y[:, :, :-1, :]
    return F.l1_loss(sx, sy) + F.l1_loss(tx, ty)


def color_loss(x: torch.Tensor, y: torch.Tensor) -> torch.Tensor:
    mx = x.mean(dim=(2, 3))
    my = y.mean(dim=(2, 3))
    return F.l1_loss(mx, my)


def color_moment_loss(x: torch.Tensor, y: torch.Tensor) -> torch.Tensor:
    """Mean + std по каналам — сильнее тянет цвет и контраст к псевдо-таргету."""
    mx, sx = x.mean(dim=(2, 3)), x.std(dim=(2, 3))
    my, sy = y.mean(dim=(2, 3)), y.std(dim=(2, 3))
    return F.l1_loss(mx, my) + 0.5 * F.l1_loss(sx, sy)


def histogram_tone_loss(x: torch.Tensor, y: torch.Tensor) -> torch.Tensor:
    """Квантили по пространству [H,W] для каждого (batch, channel)."""
    B, C, _, _ = x.shape
    q = torch.tensor([0.1, 0.5, 0.9], device=x.device, dtype=x.dtype)
    xq = torch.quantile(x.view(B, C, -1), q, dim=2)
    yq = torch.quantile(y.view(B, C, -1), q, dim=2)
    return F.l1_loss(xq, yq)


def red_blue_balance_loss(x: torch.Tensor, y: torch.Tensor) -> torch.Tensor:
    """Подводные кадры: соотношение R/B к таргету (без отдельного датасета)."""
    eps = 1e-4
    rp, bp = x[:, 0], x[:, 2]
    rt, bt = y[:, 0], y[:, 2]
    rb_p = (rp / (bp + eps)).mean(dim=(1, 2))
    rb_t = (rt / (bt + eps)).mean(dim=(1, 2))
    return F.l1_loss(rb_p, rb_t)


@dataclass
class LossWeights:
    w_rec: float = 1.0
    w_edge: float = 0.28
    w_col: float = 0.35
    w_moment: float = 0.4
    w_hist: float = 0.38
    w_rb: float = 0.25
    w_perceptual: float = 0.12


def total_loss(
    pred: torch.Tensor,
    target: torch.Tensor,
    depth_weight: torch.Tensor | None = None,
    *,
    weights: LossWeights | None = None,
    perceptual: nn.Module | None = None,
) -> torch.Tensor:
    w = weights or LossWeights()
    l_rec = charbonnier(pred, target)
    l_edge = edge_loss(pred, target)
    l_col = color_loss(pred, target)
    l_mom = color_moment_loss(pred, target)
    l_hist = histogram_tone_loss(pred, target)
    l_rb = red_blue_balance_loss(pred, target)
    loss = (
        w.w_rec * l_rec
        + w.w_edge * l_edge
        + w.w_col * l_col
        + w.w_moment * l_mom
        + w.w_hist * l_hist
        + w.w_rb * l_rb
    )
    if perceptual is not None and w.w_perceptual > 0:
        loss = loss + w.w_perceptual * perceptual(pred, target)
    if depth_weight is not None:
        loss = loss * (1.0 + depth_weight.mean() * 0.2)
    return loss
