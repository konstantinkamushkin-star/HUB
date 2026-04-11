#!/usr/bin/env python3
"""
Обучение BermanHazeLinesNet по цветовым табличкам — без папки target/.

Лосс: на выходе сети в каждой размеченной области средний RGB должен совпасть
с эталоном reference_rgb из JSON. Одного кадра достаточно, если на нём
несколько патчей/табличек — все идут в суммарный лосс.

Структура данных: см. chart_dataset.py (images/ + labels/*.json).

Пример:
  python train_berman_chart.py --data_dir ./data_charts --epochs 80
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

import torch
import torch.nn as nn
from torch.utils.data import DataLoader, random_split

try:
    from tqdm import tqdm
except ImportError:
    tqdm = None

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT))
from berman_haze_net import BermanHazeLinesNet
from chart_dataset import ChartUnderwaterDataset


def chart_loss(pred: torch.Tensor, boxes: torch.Tensor, refs: torch.Tensor) -> torch.Tensor:
    """
    pred: [1, 3, H, W]
    boxes: [N, 4] x0,y0,x1,y1
    refs: [N, 3]
    """
    l1 = nn.L1Loss(reduction="mean")
    means = []
    for i in range(boxes.shape[0]):
        x0, y0, x1, y1 = boxes[i].tolist()
        roi = pred[:, :, y0:y1, x0:x1]
        if roi.numel() == 0:
            continue
        m = roi.mean(dim=(2, 3)).squeeze(0)
        means.append(m)
    if not means:
        return pred.new_tensor(0.0)
    pred_mean = torch.stack(means, dim=0)
    return l1(pred_mean, refs)


def main():
    ap = argparse.ArgumentParser(description="Train Berman net on color charts (no image pairs)")
    ap.add_argument("--data_dir", type=Path, default=Path("./data_charts"))
    ap.add_argument("--epochs", type=int, default=100)
    ap.add_argument("--lr", type=float, default=2e-4)
    ap.add_argument("--size", type=int, default=256)
    ap.add_argument("--val_ratio", type=float, default=0.1)
    ap.add_argument("--identity_weight", type=float, default=0.08, help="L1(pred, input) weak regularizer")
    ap.add_argument("--export", type=Path, default=Path("../models/underwater_berman.onnx"))
    ap.add_argument("--checkpoint", type=Path, default=Path("../models/underwater_berman.pt"))
    args = ap.parse_args()

    data_dir = args.data_dir.resolve()
    ds = ChartUnderwaterDataset(data_dir, size=args.size, augment=True)
    if len(ds) < 2:
        print(
            "Нужны: data_dir/images/*.jpg и data_dir/labels/<имя>.json с полем patches.\n"
            "См. chart_dataset.py — пары кадров не требуются."
        )
        sys.exit(1)

    n_val = max(1, int(len(ds) * args.val_ratio))
    n_tr = len(ds) - n_val
    gen = torch.Generator().manual_seed(44)
    tr_ds, va_ds = random_split(ds, [n_tr, n_val], generator=gen)

    def collate(batch):
        return batch[0]

    tr_ld = DataLoader(tr_ds, batch_size=1, shuffle=True, num_workers=0, collate_fn=collate)
    va_ld = DataLoader(va_ds, batch_size=1, shuffle=False, num_workers=0, collate_fn=collate)

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print("Device:", device, "| chart supervision | train:", n_tr, "val:", n_val)

    net = BermanHazeLinesNet().to(device)
    opt = torch.optim.AdamW(net.parameters(), lr=args.lr, weight_decay=1e-4)
    sched = torch.optim.lr_scheduler.CosineAnnealingLR(opt, T_max=args.epochs)
    l1 = nn.L1Loss()

    best_val = float("inf")
    args.checkpoint.parent.mkdir(parents=True, exist_ok=True)

    for epoch in range(args.epochs):
        net.train()
        tr_loss = 0.0
        it = tr_ld
        if tqdm is not None:
            it = tqdm(tr_ld, desc=f"epoch {epoch+1}/{args.epochs}")
        for sample in it:
            x, boxes, refs = sample
            x = x.unsqueeze(0).to(device)
            boxes = boxes.to(device)
            refs = refs.to(device)
            opt.zero_grad(set_to_none=True)
            pred = net(x)
            c = chart_loss(pred, boxes, refs)
            ident = l1(pred, x)
            loss = c + args.identity_weight * ident
            loss.backward()
            torch.nn.utils.clip_grad_norm_(net.parameters(), 1.0)
            opt.step()
            tr_loss += loss.item()
        sched.step()

        net.eval()
        va_loss = 0.0
        with torch.no_grad():
            for sample in va_ld:
                x, boxes, refs = sample
                x = x.unsqueeze(0).to(device)
                boxes = boxes.to(device)
                refs = refs.to(device)
                pred = net(x)
                c = chart_loss(pred, boxes, refs)
                ident = l1(pred, x)
                va_loss += (c + args.identity_weight * ident).item()
        va_loss /= max(1, len(va_ld))
        tr_loss /= max(1, len(tr_ld))
        print(f"  train {tr_loss:.4f} | val {va_loss:.4f}")

        if va_loss < best_val:
            best_val = va_loss
            torch.save({"model": net.state_dict(), "size": args.size}, args.checkpoint)
            print(f"  saved -> {args.checkpoint}")

    try:
        try:
            ck = torch.load(args.checkpoint, map_location="cpu", weights_only=False)
        except TypeError:
            ck = torch.load(args.checkpoint, map_location="cpu")
        net.load_state_dict(ck["model"])
        net.eval()
        dummy = torch.randn(1, 3, args.size, args.size)
        args.export.parent.mkdir(parents=True, exist_ok=True)
        torch.onnx.export(
            net,
            dummy,
            str(args.export),
            input_names=["input"],
            output_names=["output"],
            opset_version=18,
            dynamic_axes=None,
        )
        print("Exported ONNX ->", args.export)
    except Exception as e:
        print("ONNX export skipped:", e)


if __name__ == "__main__":
    main()
