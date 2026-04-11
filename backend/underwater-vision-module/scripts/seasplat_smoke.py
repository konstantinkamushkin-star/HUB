from __future__ import annotations

import argparse
import json
import time
from pathlib import Path

import requests


def main() -> None:
    p = argparse.ArgumentParser(description="Smoke test for /v1/seasplat workflow")
    p.add_argument("--uvm", default="http://127.0.0.1:8010", help="UVM base URL")
    p.add_argument("--image", required=True, help="Path to input image")
    p.add_argument("--timeout", type=int, default=120, help="Max wait seconds for job completion")
    args = p.parse_args()

    base = args.uvm.rstrip("/")
    image_path = Path(args.image)
    if not image_path.is_file():
        raise SystemExit(f"Image not found: {image_path}")

    with image_path.open("rb") as f:
        files = [("images", (image_path.name, f.read(), "image/jpeg"))]
    poses = json.dumps([{"frame": 0, "Tcw": [[1, 0, 0, 0], [0, 1, 0, 0], [0, 0, 1, 10.0], [0, 0, 0, 1]]}])
    r = requests.post(f"{base}/v1/seasplat/scenes", files=files, params={"poses_json": poses}, timeout=60)
    r.raise_for_status()
    scene = r.json()
    scene_id = scene["scene_id"]
    print(f"scene_id={scene_id}")

    r = requests.post(f"{base}/v1/seasplat/jobs", json={"scene_id": scene_id}, timeout=30)
    r.raise_for_status()
    job = r.json()
    job_id = job["job_id"]
    print(f"job_id={job_id}")

    deadline = time.time() + args.timeout
    while True:
        r = requests.get(f"{base}/v1/seasplat/jobs/{job_id}", timeout=30)
        r.raise_for_status()
        st = r.json()
        status = st.get("status", "unknown")
        print(f"status={status} progress={st.get('progress')}")
        if status == "completed":
            break
        if status == "failed":
            raise SystemExit(f"job failed: {st.get('error')}")
        if time.time() > deadline:
            raise SystemExit("timeout waiting for SeaSplat job")
        time.sleep(1.0)

    r = requests.get(f"{base}/v1/seasplat/jobs/{job_id}/render", timeout=60)
    r.raise_for_status()
    payload = r.json()
    img_hex = payload.get("image_jpeg_base64", "")
    if not img_hex:
        raise SystemExit("render response has no image_jpeg_base64")
    print(f"ok: render hex length={len(img_hex)}")


if __name__ == "__main__":
    main()
