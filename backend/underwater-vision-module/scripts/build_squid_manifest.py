#!/usr/bin/env python3
from __future__ import annotations

import argparse
from pathlib import Path

from uvm.data.sample_record import SampleRecord
from uvm.data.manifest import save_manifest


def infer_location(path: Path) -> str:
    parts = [p.lower() for p in path.parts]
    for k in ('satil', 'katzaa', 'nachsholim', 'michmoret'):
        if k in parts:
            return k.capitalize()
    return 'Unknown'


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument('--roots', nargs='+', required=True)
    ap.add_argument('--output', required=True)
    args = ap.parse_args()

    exts = ('.tif', '.tiff', '.jpg', '.jpeg', '.png', '.nef', '.dng')
    records: list[SampleRecord] = []

    for root in args.roots:
        r = Path(root).expanduser().resolve()
        if not r.exists():
            continue
        for p in r.rglob('*'):
            if not p.is_file() or p.suffix.lower() not in exts:
                continue
            name = p.name.lower()
            if not ('resizedundistort' in name or name.endswith('.dng') or name.endswith('.nef')):
                continue
            location = infer_location(p)
            scene = p.parent.name
            sid = f'{location}_{scene}_{p.stem}'
            rec = SampleRecord(
                sample_id=sid,
                scene_id=scene,
                location_id=location,
                image_left=str(p) if 'lft_' in name else None,
                image_right=str(p) if 'rgt_' in name else None,
                tif_file=str(p) if p.suffix.lower() in ('.tif', '.tiff') else None,
                raw_file=str(p) if p.suffix.lower() in ('.nef', '.dng') else None,
                calibration_data=str(r / 'stereoParams0.5.mat') if (r / 'stereoParams0.5.mat').exists() else None,
                notes='auto-generated from folder scan',
            )
            records.append(rec)

    # merge left/right within same scene stem prefix when possible (simple heuristic)
    by_key = {}
    for rec in records:
        key = (rec.location_id, rec.scene_id, rec.sample_id.replace('LFT_', '').replace('RGT_', ''))
        if key not in by_key:
            by_key[key] = rec
        else:
            base = by_key[key]
            if not base.image_left and rec.image_left:
                base.image_left = rec.image_left
            if not base.image_right and rec.image_right:
                base.image_right = rec.image_right
            if not base.tif_file and rec.tif_file:
                base.tif_file = rec.tif_file
            if not base.raw_file and rec.raw_file:
                base.raw_file = rec.raw_file

    out = list(by_key.values())
    save_manifest(out, args.output)
    print(f'Saved records: {len(out)} -> {args.output}')


if __name__ == '__main__':
    main()
