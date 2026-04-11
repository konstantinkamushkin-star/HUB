#!/usr/bin/env python3
"""
Обучение U-Net на парах input/target.
Пример:
  cd backend/ai-service/train
  pip install -r requirements-train.txt
  python train.py --data_dir ./data --epochs 80 --batch_size 8 --export ../models/underwater.onnx
"""
from __future__ import annotations

import argparse
from pathlib import Path
import sys

import torch
import torch.nn as nn
from torch.utils.data import DataLoader, random_split
from tqdm import tqdm

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT))
from dataset import PairedUnderwaterDataset
from model import UnderwaterUNet


def ssim_window(x: torch.Tensor, y: torch.Tensor, window_size: int = 7) -> torch.Tensor:
    """Упрощённый SSIM по среднему по каналам (без Gaussian), стабилизирует структуру."""
    c1, c2 = 0.01**2, 0.03**2
    pad = window_size // 2
    mu_x = torch.nn.functional.avg_pool2d(x, window_size, stride=1, padding=pad)
    mu_y = torch.nn.functional.avg_pool2d(y, window_size, stride=1, padding=pad)
    sigma_x = torch.nn.functional.avg_pool2d(x * x, window_size, stride=1, padding=pad) - mu_x**2
    sigma_y = torch.nn.functional.avg_pool2d(y * y, window_size, stride=1, padding=pad) - mu_y**2
    sigma_xy = torch.nn.functional.avg_pool2d(x * y, window_size, stride=1, padding=pad) - mu_x * mu_y
    ssim = ((2 * mu_x * mu_y + c1) * (2 * sigma_xy + c2)) / (
        (mu_x**2 + mu_y**2 + c1) * (sigma_x + sigma_y + c2).clamp(min=1e-8)
    )
    return ssim.mean()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data_dir", type=str, default="./data")
    ap.add_argument("--epochs", type=int, default=60)
    ap.add_argument("--batch_size", type=int, default=8)
    ap.add_argument("--lr", type=float, default=2e-4)
    ap.add_argument("--size", type=int, default=256, help="Train resolution (ONNX export size)")
    ap.add_argument("--val_ratio", type=float, default=0.1)
    ap.add_argument("--export", type=str, default="../models/underwater.onnx")
    ap.add_argument("--checkpoint", type=str, default="../models/underwater.pt")
    ap.add_argument("--ssim_weight", type=float, default=0.35, help="1-ssim added to loss (lower is better)")
    args = ap.parse_args()

    data_dir = Path(args.data_dir).resolve()
    ds = PairedUnderwaterDataset(data_dir, size=args.size, augment=True)
    if len(ds) < 2:
        print(
            "Нужны пары: data/input/*.jpg и data/target/*.jpg с одинаковыми именами.\n"
            "Быстрый старт: python generate_targets.py --data_dir ./data\n"
            "Лучшее качество: соберите пары «сырой подводный снимок → эталон после ретуши вручную»."
        )
        sys.exit(1)

    n_val = max(1, int(len(ds) * args.val_ratio))
    n_tr = len(ds) - n_val
    gen = torch.Generator().manual_seed(42)
    tr_ds, va_ds = random_split(ds, [n_tr, n_val], generator=gen)
    eff_bs = max(1, min(args.batch_size, len(tr_ds)))
    tr_ld = DataLoader(tr_ds, batch_size=eff_bs, shuffle=True, num_workers=0, drop_last=False)
    va_bs = max(1, min(args.batch_size, len(va_ds)))
    va_ld = DataLoader(va_ds, batch_size=va_bs, shuffle=False, num_workers=0)

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print("Device:", device, "| train pairs:", n_tr, "| val:", n_val)

    net = UnderwaterUNet(base=32).to(device)
    opt = torch.optim.AdamW(net.parameters(), lr=args.lr, weight_decay=1e-4)
    sched = torch.optim.lr_scheduler.CosineAnnealingLR(opt, T_max=args.epochs)
    l1 = nn.L1Loss()

    best_val = float("inf")
    ckpt_path = Path(args.checkpoint).resolve()
    ckpt_path.parent.mkdir(parents=True, exist_ok=True)

    for epoch in range(args.epochs):
        net.train()
        tr_loss = 0.0
        for x, y in tqdm(tr_ld, desc=f"epoch {epoch+1}/{args.epochs}"):
            x, y = x.to(device), y.to(device)
            opt.zero_grad(set_to_none=True)
            pred = net(x)
            loss_l1 = l1(pred, y)
            loss_ssim = 1.0 - ssim_window(pred, y)
            loss = loss_l1 + args.ssim_weight * loss_ssim
            loss.backward()
            torch.nn.utils.clip_grad_norm_(net.parameters(), 1.0)
            opt.step()
            tr_loss += loss.item()
        sched.step()

        net.eval()
        va_loss = 0.0
        with torch.no_grad():
            for x, y in va_ld:
                x, y = x.to(device), y.to(device)
                pred = net(x)
                va_loss += (l1(pred, y) + args.ssim_weight * (1.0 - ssim_window(pred, y))).item()
        va_loss /= max(1, len(va_ld))
        tr_loss /= max(1, len(tr_ld))
        print(f"  train {tr_loss:.4f} | val {va_loss:.4f}")

        if va_loss < best_val:
            best_val = va_loss
            torch.save({"model": net.state_dict(), "size": args.size}, ckpt_path)
            print(f"  saved checkpoint (best val) -> {ckpt_path}")

    # Export ONNX (NCHW, fixed H=W=size)
    export_path = Path(args.export).resolve()
    export_path.parent.mkdir(parents=True, exist_ok=True)
    try:
        ck = torch.load(ckpt_path, map_location="cpu", weights_only=False)
    except TypeError:
        ck = torch.load(ckpt_path, map_location="cpu")
    net.load_state_dict(ck["model"])
    net.eval()
    dummy = torch.randn(1, 3, args.size, args.size)
    torch.onnx.export(
        net,
        dummy,
        str(export_path),
        input_names=["input"],
        output_names=["output"],
        opset_version=18,
        dynamic_axes=None,
    )
    print(f"ONNX -> {export_path} (input shape [1,3,{args.size},{args.size}], float32 0..1)")


if __name__ == "__main__":
    main()
