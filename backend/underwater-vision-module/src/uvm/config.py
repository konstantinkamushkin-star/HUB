from __future__ import annotations

from pydantic import BaseModel, Field


class DataConfig(BaseModel):
    manifest_path: str = "./configs/samples_manifest.json"
    split_strategy: str = "loso"  # loso | holdout-sites
    heldout_site: str | None = None
    input_size: int = 512
    batch_size: int = 4
    num_workers: int = 2


class TrainConfig(BaseModel):
    epochs: int = 60
    lr: float = 2e-4
    weight_decay: float = 1e-4
    amp: bool = True
    gradient_clip: float = 1.0
    checkpoint_dir: str = "./checkpoints"


class ApiConfig(BaseModel):
    max_image_side: int = 2048
    allow_raw: bool = True
    enable_video: bool = True


class ModuleConfig(BaseModel):
    module_name: str = "underwater-vision-module"
    data: DataConfig = Field(default_factory=DataConfig)
    train: TrainConfig = Field(default_factory=TrainConfig)
    api: ApiConfig = Field(default_factory=ApiConfig)
