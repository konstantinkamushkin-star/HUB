#!/usr/bin/env python3
from __future__ import annotations

import argparse

from uvm.data.manifest import load_manifest
from uvm.data.splits import split_loso, split_holdout_sites
from uvm.train.trainer import train_real


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument('--manifest', required=True)
    ap.add_argument('--split_strategy', default='loso', choices=['loso', 'holdout-sites'])
    ap.add_argument('--heldout_site', default='Satil')
    ap.add_argument('--holdout_sites', nargs='*', default=['Satil'])
    ap.add_argument('--epochs', type=int, default=60)
    ap.add_argument('--batch_size', type=int, default=4)
    ap.add_argument('--input_size', type=int, default=512)
    ap.add_argument('--lr', type=float, default=2e-4)
    ap.add_argument('--weight_decay', type=float, default=1e-4)
    ap.add_argument('--num_workers', type=int, default=2)
    ap.add_argument('--checkpoint_dir', default='./checkpoints')
    ap.add_argument('--export_onnx', default='./checkpoints/model.onnx')
    ap.add_argument('--device', default=None, help='cpu|cuda|mps (optional)')
    ap.add_argument('--dry_run', action='store_true')
    # Stronger pseudo-teacher (same raw images — агрессивнее цвет/контраст как цель обучения)
    ap.add_argument('--pseudo_strength', type=float, default=0.92)
    ap.add_argument('--pseudo_clahe_boost', type=float, default=1.25, help='added to CLAHE clip limit in teacher')
    ap.add_argument('--pseudo_red_scale', type=float, default=1.65, help='red channel gain multiplier in teacher')
    ap.add_argument('--pseudo_wb_lo', type=float, default=0.72)
    ap.add_argument('--pseudo_wb_hi', type=float, default=1.38)
    ap.add_argument('--pseudo_saturation_gain', type=float, default=0.22, help='HSV saturation coeff in teacher')
    ap.add_argument('--lambda_perceptual', type=float, default=0.12, help='VGG perceptual term (0 to disable weight)')
    ap.add_argument('--no_perceptual', action='store_true', help='skip VGG perceptual loss entirely')
    args = ap.parse_args()

    records = load_manifest(args.manifest)
    if args.split_strategy == 'loso':
        train, val, test = split_loso(records, args.heldout_site)
    else:
        train, val, test = split_holdout_sites(records, set(args.holdout_sites))

    print(f'Records: total={len(records)} train={len(train)} val={len(val)} test={len(test)}')

    if args.dry_run:
        return

    summary = train_real(
        train_records=train,
        val_records=val,
        test_records=test,
        epochs=args.epochs,
        batch_size=args.batch_size,
        input_size=args.input_size,
        lr=args.lr,
        weight_decay=args.weight_decay,
        num_workers=args.num_workers,
        checkpoint_dir=args.checkpoint_dir,
        export_onnx=args.export_onnx,
        device=args.device,
        pseudo_strength=args.pseudo_strength,
        pseudo_clahe_boost=args.pseudo_clahe_boost,
        pseudo_red_scale=args.pseudo_red_scale,
        pseudo_wb_lo=args.pseudo_wb_lo,
        pseudo_wb_hi=args.pseudo_wb_hi,
        pseudo_saturation_gain=args.pseudo_saturation_gain,
        use_perceptual=not args.no_perceptual,
        lambda_perceptual=0.0 if args.no_perceptual else args.lambda_perceptual,
    )
    print(summary)


if __name__ == '__main__':
    main()
