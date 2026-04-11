from __future__ import annotations

import json
from pathlib import Path
from typing import Iterable

from .sample_record import SampleRecord


def save_manifest(records: Iterable[SampleRecord], path: str) -> None:
    p = Path(path)
    p.parent.mkdir(parents=True, exist_ok=True)
    payload = [r.model_dump() for r in records]
    p.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding='utf-8')


def load_manifest(path: str) -> list[SampleRecord]:
    payload = json.loads(Path(path).read_text(encoding='utf-8'))
    return [SampleRecord(**x) for x in payload]
