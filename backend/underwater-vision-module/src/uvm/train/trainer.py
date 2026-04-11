from __future__ import annotations

from dataclasses import dataclass, replace
from pathlib import Path

import torch
from torch.utils.data import DataLoader
from tqdm import tqdm

from .dataset import ManifestPhotoDataset
from .losses import LossWeights, total_loss
from .model import DepthAwareUNet


@dataclass
class TrainSummary:
    epochs: int
    train_size: int
    val_size: int
    test_size: int
    best_val_loss: float
    checkpoint_path: str


def _evaluate(model, loader, device, perceptual, loss_weights: LossWeights):
    model.eval()
    s = 0.0
    n = 0
    with torch.no_grad():
        for x, y, d in loader:
            x, y, d = x.to(device), y.to(device), d.to(device)
            p = model(x)
            loss = total_loss(p, y, depth_weight=d, weights=loss_weights, perceptual=perceptual)
            s += float(loss.item())
            n += 1
    return s / max(n, 1)


def train_real(
    train_records,
    val_records,
    test_records,
    epochs: int = 60,
    batch_size: int = 4,
    input_size: int = 512,
    lr: float = 2e-4,
    weight_decay: float = 1e-4,
    num_workers: int = 2,
    checkpoint_dir: str = "./checkpoints",
    export_onnx: str | None = None,
    device: str | None = None,
    *,
    pseudo_strength: float = 0.92,
    pseudo_clahe_boost: float = 1.25,
    pseudo_red_scale: float = 1.65,
    pseudo_wb_lo: float = 0.72,
    pseudo_wb_hi: float = 1.38,
    pseudo_saturation_gain: float = 0.22,
    use_perceptual: bool = True,
    lambda_perceptual: float = 0.12,
    loss_weights: LossWeights | None = None,
) -> TrainSummary:
    if device:
        dev = torch.device(device)
    elif torch.cuda.is_available():
        dev = torch.device("cuda")
    elif torch.backends.mps.is_available():
        dev = torch.device("mps")
    else:
        dev = torch.device("cpu")

    ckpt_dir = Path(checkpoint_dir)
    ckpt_dir.mkdir(parents=True, exist_ok=True)

    ds_kw = dict(
        size=input_size,
        use_pseudo_target=True,
        pseudo_strength=pseudo_strength,
        pseudo_clahe_boost=pseudo_clahe_boost,
        pseudo_red_scale=pseudo_red_scale,
        pseudo_wb_lo=pseudo_wb_lo,
        pseudo_wb_hi=pseudo_wb_hi,
        pseudo_saturation_gain=pseudo_saturation_gain,
    )
    ds_train = ManifestPhotoDataset(train_records, cache_dir=str(ckpt_dir / "pseudo_train"), **ds_kw)
    ds_val = ManifestPhotoDataset(val_records, cache_dir=str(ckpt_dir / "pseudo_val"), **ds_kw)
    dl_train = DataLoader(ds_train, batch_size=batch_size, shuffle=True, num_workers=num_workers)
    dl_val = DataLoader(ds_val, batch_size=batch_size, shuffle=False, num_workers=num_workers)

    perceptual = None
    if use_perceptual and lambda_perceptual > 0:
        try:
            from uvm.train.perceptual_loss import VGGPerceptualLoss

            perceptual = VGGPerceptualLoss().to(dev)
        except Exception as e:
            print(f"[train] Perceptual loss disabled ({e}). Install torchvision + network for VGG weights, or use --no_perceptual.")

    lw_base = loss_weights or LossWeights()
    lw = replace(lw_base, w_perceptual=lambda_perceptual if perceptual is not None else 0.0)

    model = DepthAwareUNet(in_ch=4, out_ch=3).to(dev)
    opt = torch.optim.AdamW(model.parameters(), lr=lr, weight_decay=weight_decay)
    sched = torch.optim.lr_scheduler.CosineAnnealingLR(opt, T_max=max(epochs, 1))

    best_val = float("inf")
    best_path = ckpt_dir / "best.pt"
    last_path = ckpt_dir / "last.pt"

    for ep in tqdm(range(epochs), desc="Training", unit="epoch"):
        model.train()
        run = 0.0
        nb = 0
        pbar = tqdm(dl_train, desc=f"  epoch {ep+1}/{epochs}", leave=False, unit="batch")
        for x, y, d in pbar:
            x, y, d = x.to(dev), y.to(dev), d.to(dev)
            opt.zero_grad(set_to_none=True)
            pred = model(x)
            loss = total_loss(pred, y, depth_weight=d, weights=lw, perceptual=perceptual)
            loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
            opt.step()
            run += float(loss.item())
            nb += 1
            pbar.set_postfix(loss=f"{loss.item():.4f}")
        sched.step()

        tr = run / max(nb, 1)
        vl = _evaluate(model, dl_val, dev, perceptual, lw) if len(ds_val) > 0 else tr
        tqdm.write(f"epoch {ep+1}/{epochs}: train={tr:.4f} val={vl:.4f}")

        torch.save({"model": model.state_dict(), "epoch": ep + 1, "val_loss": vl}, last_path)
        if vl < best_val:
            best_val = vl
            torch.save({"model": model.state_dict(), "epoch": ep + 1, "val_loss": vl}, best_path)

    if export_onnx:
        model.eval()
        dummy = torch.zeros(1, 4, input_size, input_size, device=dev)
        export_path = Path(export_onnx)
        export_path.parent.mkdir(parents=True, exist_ok=True)
        try:
            torch.onnx.export(
                model,
                dummy,
                str(export_path),
                input_names=["input"],
                output_names=["output"],
                dynamic_axes={"input": {0: "batch", 2: "H", 3: "W"}, "output": {0: "batch", 2: "H", 3: "W"}},
                opset_version=18,
                dynamo=False,
            )
        except Exception as e:
            print(f"ONNX export skipped: {e}")

    return TrainSummary(
        epochs=epochs,
        train_size=len(ds_train),
        val_size=len(ds_val),
        test_size=len(test_records),
        best_val_loss=float(best_val),
        checkpoint_path=str(best_path),
    )
