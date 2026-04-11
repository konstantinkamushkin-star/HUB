from __future__ import annotations

from pydantic import BaseModel


class SampleRecord(BaseModel):
    sample_id: str
    scene_id: str
    location_id: str
    water_type: str | None = None
    depth_range: str | None = None

    image_left: str | None = None
    image_right: str | None = None
    raw_file: str | None = None
    tif_file: str | None = None
    depth_map: str | None = None
    calibration_data: str | None = None
    camera_metadata: dict | None = None

    target_reference: str | None = None
    quality_flags: list[str] = []
    notes: str | None = None
