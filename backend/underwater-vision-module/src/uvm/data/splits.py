from __future__ import annotations

from collections import defaultdict
from .sample_record import SampleRecord


def split_loso(records: list[SampleRecord], heldout_site: str) -> tuple[list[SampleRecord], list[SampleRecord], list[SampleRecord]]:
    train = [r for r in records if r.location_id != heldout_site]
    test = [r for r in records if r.location_id == heldout_site]
    by_scene = defaultdict(list)
    for r in train:
        by_scene[r.scene_id].append(r)
    val_scenes = set(list(by_scene.keys())[::5])
    val = [r for r in train if r.scene_id in val_scenes]
    train = [r for r in train if r.scene_id not in val_scenes]
    return train, val, test


def split_holdout_sites(records: list[SampleRecord], holdout_sites: set[str]) -> tuple[list[SampleRecord], list[SampleRecord], list[SampleRecord]]:
    test = [r for r in records if r.location_id in holdout_sites]
    rest = [r for r in records if r.location_id not in holdout_sites]
    val = rest[::6]
    train = [r for i, r in enumerate(rest) if i % 6 != 0]
    return train, val, test
