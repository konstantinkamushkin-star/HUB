#!/usr/bin/env python3
"""
Генерация эталонов для обучения: прогон всех фото из data/input
через классический пайплайн и сохранение в data/target.
Запуск: python generate_targets.py [--data_dir ./data]
"""
import argparse
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from inference import process


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--data_dir", type=str, default="./data")
    p.add_argument("--depth", type=float, default=10.0)
    p.add_argument("--strength", type=float, default=0.7)
    args = p.parse_args()

    data_dir = Path(args.data_dir)
    input_dir = data_dir / "input"
    target_dir = data_dir / "target"
    if not input_dir.exists():
        print(f"Создайте папку {input_dir} и положите туда подводные фото.")
        return
    target_dir.mkdir(parents=True, exist_ok=True)

    exts = (".jpg", ".jpeg", ".png", ".bmp")
    for path in sorted(input_dir.iterdir()):
        if path.suffix.lower() not in exts:
            continue
        raw = path.read_bytes()
        try:
            out = process(raw, depth_m=args.depth, strength=args.strength, use_ai=False)
        except Exception as e:
            print(f"Ошибка {path.name}: {e}")
            continue
        (target_dir / path.name).write_bytes(out)
        print(f"  {path.name} -> target/")
    print("Готово. Запустите: python train.py --data_dir", args.data_dir)


if __name__ == "__main__":
    main()
