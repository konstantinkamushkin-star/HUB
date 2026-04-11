from __future__ import annotations

import json
import os
import subprocess
import threading
import time
import uuid
from pathlib import Path

import cv2
import numpy as np

from uvm.api.jpeg_utils import encode_jpeg_hex


class SeaSplatRuntime:
    """Scene/job orchestrator for SeaSplat-style multi-view workflow."""

    def __init__(self) -> None:
        root = Path(os.environ.get("UVM_SEASPLAT_WORKDIR", "/tmp/uvm-seasplat")).expanduser()
        root.mkdir(parents=True, exist_ok=True)
        self._root = root
        self._runner = (os.environ.get("UVM_SEASPLAT_RUNNER", "") or "").strip()
        self._runner_timeout_s = int(os.environ.get("UVM_SEASPLAT_TIMEOUT_S", "900"))
        self._lock = threading.Lock()
        self._scenes: dict[str, dict] = {}
        self._jobs: dict[str, dict] = {}

    def health(self) -> dict:
        runner_configured = bool(self._runner)
        runner_exists = False
        if runner_configured:
            try:
                runner_exists = Path(self._runner).expanduser().exists()
            except Exception:
                runner_exists = False
        return {
            "workdir": str(self._root),
            "runner_configured": runner_configured,
            "runner_exists": runner_exists,
            "mode": "external_runner" if runner_configured and runner_exists else "fallback",
            "timeout_s": self._runner_timeout_s,
        }

    def upload_scene(self, frames: list[np.ndarray], frame_names: list[str], poses_json: str | None) -> dict:
        if not frames:
            raise ValueError("at least one frame is required")
        scene_id = str(uuid.uuid4())
        scene_dir = self._root / "scenes" / scene_id
        frames_dir = scene_dir / "frames"
        frames_dir.mkdir(parents=True, exist_ok=True)

        frame_meta: list[dict] = []
        for i, (bgr, name) in enumerate(zip(frames, frame_names)):
            out_name = f"{i:04d}_{Path(name).name or 'frame.jpg'}"
            out_path = frames_dir / out_name
            ok = cv2.imwrite(str(out_path), bgr)
            if not ok:
                raise RuntimeError(f"failed to persist frame {i}")
            frame_meta.append({"index": i, "filename": out_name, "h": int(bgr.shape[0]), "w": int(bgr.shape[1])})

        poses_path = scene_dir / "poses.json"
        if poses_json:
            poses_path.write_text(poses_json, encoding="utf-8")
        else:
            poses_path.write_text("[]", encoding="utf-8")

        with self._lock:
            self._scenes[scene_id] = {
                "scene_id": scene_id,
                "scene_dir": str(scene_dir),
                "created_at": int(time.time() * 1000),
                "status": "uploaded",
                "frame_meta": frame_meta,
            }
        return {"scene_id": scene_id, "frame_count": len(frame_meta), "status": "uploaded"}

    def get_scene(self, scene_id: str) -> dict | None:
        with self._lock:
            return self._scenes.get(scene_id)

    def create_job(self, scene_id: str) -> dict:
        with self._lock:
            scene = self._scenes.get(scene_id)
            if scene is None:
                raise KeyError("scene_not_found")
            job_id = str(uuid.uuid4())
            self._jobs[job_id] = {
                "job_id": job_id,
                "scene_id": scene_id,
                "created_at": int(time.time() * 1000),
                "status": "queued",
                "progress": 0.0,
                "result_jpeg_hex": None,
                "report": {},
                "error": None,
            }
        t = threading.Thread(target=self._run_job_thread, args=(job_id,), daemon=True)
        t.start()
        return {"job_id": job_id, "scene_id": scene_id, "status": "queued"}

    def get_job(self, job_id: str) -> dict | None:
        with self._lock:
            return self._jobs.get(job_id)

    def job_render(self, job_id: str) -> dict:
        with self._lock:
            j = self._jobs.get(job_id)
            if j is None:
                raise KeyError("job_not_found")
            if j["status"] != "completed":
                raise RuntimeError("job_not_ready")
            return {"image_jpeg_base64": j["result_jpeg_hex"], "report": j["report"]}

    def _run_job_thread(self, job_id: str) -> None:
        with self._lock:
            j = self._jobs.get(job_id)
            if j is None:
                return
            j["status"] = "running"
            j["progress"] = 0.1
            scene = self._scenes.get(j["scene_id"])
        if scene is None:
            self._finish_failed(job_id, "scene_not_found")
            return

        scene_dir = Path(scene["scene_dir"])
        frames_dir = scene_dir / "frames"
        render_path = scene_dir / "render.jpg"
        report_path = scene_dir / "report.json"

        # If external runner is configured, delegate full SeaSplat optimization to it.
        if self._runner:
            try:
                cmd = [self._runner, "--scene", str(scene_dir), "--frames", str(frames_dir), "--output", str(render_path)]
                proc = subprocess.run(
                    cmd,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE,
                    text=True,
                    timeout=self._runner_timeout_s,
                    check=False,
                )
                if proc.returncode != 0:
                    msg = f"runner_failed rc={proc.returncode}: {proc.stderr.strip()[:500]}"
                    self._finish_failed(job_id, msg)
                    return
            except subprocess.TimeoutExpired:
                self._finish_failed(job_id, "runner_timeout")
                return
            except Exception as e:  # pragma: no cover
                self._finish_failed(job_id, f"runner_exception: {e}")
                return

            if not render_path.is_file():
                self._finish_failed(job_id, "runner_no_output_render")
                return
            out = cv2.imread(str(render_path), cv2.IMREAD_COLOR)
            if out is None:
                self._finish_failed(job_id, "runner_output_not_image")
                return
            rep = {
                "backend": "external_seasplat_runner",
                "scene_dir": str(scene_dir),
            }
            if report_path.is_file():
                try:
                    rep["runner_report"] = json.loads(report_path.read_text(encoding="utf-8"))
                except Exception:
                    pass
            out_hex = encode_jpeg_hex(out, quality=92)
            self._finish_ok(job_id, out_hex, rep)
            return

        # Fallback path: distinct SeaSplat-style multi-frame fusion (not Sea-Thru path).
        frame_paths = sorted(frames_dir.glob("*.jpg"))
        if not frame_paths:
            self._finish_failed(job_id, "no_frames")
            return
        frames: list[np.ndarray] = []
        for p in frame_paths:
            bgr = cv2.imread(str(p), cv2.IMREAD_COLOR)
            if bgr is None:
                continue
            frames.append(bgr)
        if not frames:
            self._finish_failed(job_id, "invalid_frames")
            return
        out, inner = self._fallback_seasplat_like(frames)
        out_hex = encode_jpeg_hex(out, quality=92)
        self._finish_ok(
            job_id,
            out_hex,
            {
                "backend": "seasplat_contract_fallback",
                "note": "Set UVM_SEASPLAT_RUNNER to use a real SeaSplat backend binary/script.",
                "inner_report": inner,
            },
        )

    def _fallback_seasplat_like(self, frames: list[np.ndarray]) -> tuple[np.ndarray, dict]:
        base_h, base_w = frames[0].shape[:2]
        stack: list[np.ndarray] = []
        for fr in frames:
            if fr.shape[:2] != (base_h, base_w):
                fr = cv2.resize(fr, (base_w, base_h), interpolation=cv2.INTER_AREA)
            stack.append(fr.astype(np.float32) / 255.0)

        # Multi-view robust fusion proxy: per-pixel median across frames.
        vol = np.stack(stack, axis=0)
        fused = np.median(vol, axis=0)

        # Gray-world white balance proxy.
        ch_mean = np.mean(fused, axis=(0, 1))
        target = float(np.mean(ch_mean))
        gains = target / np.maximum(ch_mean, 1e-6)
        fused = np.clip(fused * gains.reshape(1, 1, 3), 0.0, 1.0)

        # Underwater channel compensation: gently recover red and reduce green cast.
        b_mean, g_mean, r_mean = [float(x) for x in np.mean(fused, axis=(0, 1))]
        rg_ratio = r_mean / max(g_mean, 1e-6)
        red_gain = float(np.clip(1.0 + 0.55 * (0.92 - rg_ratio), 1.00, 1.38))
        green_gain = float(np.clip(1.0 - 0.22 * max(0.0, 1.0 - rg_ratio), 0.84, 1.00))
        blue_gain = float(np.clip(1.0 + 0.10 * (0.80 - r_mean / max(b_mean, 1e-6)), 0.92, 1.08))
        fused[:, :, 2] *= red_gain
        fused[:, :, 1] *= green_gain
        fused[:, :, 0] *= blue_gain
        fused = np.clip(fused, 0.0, 1.0)

        # Stable tonal path for underwater scenes without contour artifacts.
        bgr_u8 = np.clip(fused * 255.0, 0, 255).astype(np.uint8)
        lab = cv2.cvtColor(bgr_u8, cv2.COLOR_BGR2LAB)
        l, a, b = cv2.split(lab)
        l_f = l.astype(np.float32) / 255.0

        # Adaptive gamma by scene brightness (dark scene -> gamma < 1, bright -> > 1).
        mean_luma = float(np.mean(l_f))
        gamma = float(np.clip(0.92 - 0.45 * (mean_luma - 0.40), 0.78, 1.04))
        l_gamma = np.power(np.clip(l_f, 0.0, 1.0), gamma)

        # Exposure normalization to robust target luma.
        target_luma = 0.46
        gain = float(np.clip(target_luma / max(float(np.mean(l_gamma)), 1e-6), 0.92, 1.45))
        l_tone = np.clip(l_gamma * gain, 0.0, 1.0)
        l_tone_u8 = np.clip(l_tone * 255.0, 0, 255).astype(np.uint8)

        # Local contrast with conservative CLAHE.
        clahe = cv2.createCLAHE(clipLimit=1.45, tileGridSize=(10, 10))
        l_out = clahe.apply(l_tone_u8)
        lab2 = cv2.merge([l_out, a, b])
        out = cv2.cvtColor(lab2, cv2.COLOR_LAB2BGR)

        # Mild saturation boost for underwater scenes.
        hsv = cv2.cvtColor(out, cv2.COLOR_BGR2HSV).astype(np.float32)
        hsv[:, :, 1] = np.clip(hsv[:, :, 1] * 1.04, 0, 255)
        out = cv2.cvtColor(hsv.astype(np.uint8), cv2.COLOR_HSV2BGR)

        # Final percentile color balancing for neutral highlights.
        out_f = out.astype(np.float32) / 255.0
        p95 = np.percentile(out_f, 95, axis=(0, 1))
        p95_target = float(np.mean(p95))
        bal = p95_target / np.maximum(p95, 1e-6)
        bal = np.clip(bal, 0.85, 1.15)
        out_f *= bal.reshape(1, 1, 3)
        out = np.clip(out_f * 255.0, 0, 255).astype(np.uint8)

        # Mild denoise and unsharp mask (avoids "engraving" look).
        out = cv2.bilateralFilter(out, d=7, sigmaColor=20, sigmaSpace=9)
        blur = cv2.GaussianBlur(out, (0, 0), 1.2)
        out = cv2.addWeighted(out, 1.12, blur, -0.12, 0)

        mean_out = float(np.mean(out) / 255.0)

        report = {
            "frames_used": len(frames),
            "fusion": "median",
            "white_balance": "gray_world",
            "underwater_channel_compensation": {
                "red_gain": red_gain,
                "green_gain": green_gain,
                "blue_gain": blue_gain,
            },
            "dehaze_proxy": "luminance_unsharp",
            "adaptive_gamma": gamma,
            "target_luma": target_luma,
            "mean_luma_before_gain": mean_luma,
            "exposure_gain": gain,
            "mean_out": mean_out,
            "local_contrast": "clahe_lab",
            "detail_enhance": "bilateral_plus_unsharp",
        }
        return out, report

    def _finish_ok(self, job_id: str, out_hex: str, report: dict) -> None:
        with self._lock:
            j = self._jobs.get(job_id)
            if j is None:
                return
            j["status"] = "completed"
            j["progress"] = 1.0
            j["result_jpeg_hex"] = out_hex
            j["report"] = report
            j["error"] = None
            s = self._scenes.get(j["scene_id"])
            if s is not None:
                s["status"] = "processed"

    def _finish_failed(self, job_id: str, error: str) -> None:
        with self._lock:
            j = self._jobs.get(job_id)
            if j is None:
                return
            j["status"] = "failed"
            j["progress"] = 1.0
            j["error"] = error
