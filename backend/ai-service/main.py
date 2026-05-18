"""
Underwater image enhancement AI service.
POST /process: multipart form with 'image' file, optional 'depth_m', 'strength', 'use_ai'.
POST /v1/process/photo/{engine}: UVM-совместимый API для iOS
(engine=cursor — underwater-vision-module; engine=seathru — Sea-Thru CVPR 2019).
POST /v1/process/video/{engine}: UVM video; video_mode=fast (keyframes + global LAB) или full; макс. 60 с.
POST/GET /v1/seasplat/* — тот же контракт, что у full UVM (SeaSplat в DiveEditor).
"""
import inspect
import os
import sys
import tempfile
from pathlib import Path

import cv2
import numpy as np
from fastapi import FastAPI, File, Form, HTTPException, Query, UploadFile
from fastapi.responses import JSONResponse, Response

from inference import process

_BACKEND_ROOT = Path(__file__).resolve().parent.parent
_UVM_SRC = _BACKEND_ROOT / "underwater-vision-module" / "src"
_run_cursor_underwater_correct = None
_seasplat: object | None = None
if _UVM_SRC.is_dir():
    sys.path.insert(0, str(_UVM_SRC))
    try:
        from uvm.pipeline.cursor_correction import run_cursor_underwater_correct as _run_cursor_underwater_correct
    except Exception:
        _run_cursor_underwater_correct = None
    try:
        from uvm.api.seasplat_runtime import SeaSplatRuntime

        _seasplat = SeaSplatRuntime()
    except Exception:
        _seasplat = None
    try:
        from uvm.api.video_tone import (
            downscale_bgr_for_process as _downscale_bgr_for_process,
            post_lift_underwater_video_bgr as _post_lift_video_bgr,
            upscale_bgr_to_original as _upscale_bgr_to_original,
        )
        from uvm.api.video_global_tone import (
            MAX_VIDEO_DURATION_SEC as _MAX_VIDEO_DURATION_SEC,
            assert_video_within_max_duration as _assert_video_within_max_duration,
            process_video_fast_global_bech as _process_video_fast_global_bech,
            probe_video_meta as _probe_video_meta,
        )
    except Exception:
        _post_lift_video_bgr = None
        _downscale_bgr_for_process = None
        _upscale_bgr_to_original = None
        _MAX_VIDEO_DURATION_SEC = None
        _assert_video_within_max_duration = None
        _process_video_fast_global_bech = None
        _probe_video_meta = None
else:
    _post_lift_video_bgr = None
    _downscale_bgr_for_process = None
    _upscale_bgr_to_original = None
    _MAX_VIDEO_DURATION_SEC = None
    _assert_video_within_max_duration = None
    _process_video_fast_global_bech = None
    _probe_video_meta = None

_VIDEO_MAX_SEC = float(_MAX_VIDEO_DURATION_SEC) if _MAX_VIDEO_DURATION_SEC is not None else 60.0


def _video_duration_guard(frame_index: int, fps: float) -> None:
    if _assert_video_within_max_duration is not None:
        _assert_video_within_max_duration(frame_index=frame_index, fps=fps)
        return
    f = float(fps) if fps and fps > 0 else 25.0
    if (frame_index + 1) / f > _VIDEO_MAX_SEC + 1e-3:
        raise ValueError(
            f"video exceeds max duration {_VIDEO_MAX_SEC:g}s "
            f"(got ~{(frame_index + 1) / f:.2f}s at {f:.3f} fps)"
        )

try:
    from sea_thru_cvpr2019 import run_sea_thru_cvpr2019 as _run_sea_thru_cvpr2019
except Exception:
    _run_sea_thru_cvpr2019 = None


def _encode_jpeg_hex(bgr: np.ndarray, quality: int = 92) -> str:
    ok, enc = cv2.imencode(".jpg", bgr, [cv2.IMWRITE_JPEG_QUALITY, quality])
    if not ok:
        raise RuntimeError("jpeg encode failed")
    return enc.tobytes().hex()


app = FastAPI(title="DiveHub Underwater AI", version="1.0.0")


@app.get("/health")
def health():
    out: dict = {"status": "ok", "service": "underwater-ai"}
    if _seasplat is not None and hasattr(_seasplat, "health"):
        try:
            out["seasplat"] = _seasplat.health()  # type: ignore[union-attr]
        except Exception:
            out["seasplat"] = {"error": "health_failed"}
    return out


def _parse_bool_form(v: object) -> bool:
    if isinstance(v, bool):
        return v
    s = str(v).strip().lower()
    return s not in ("false", "0", "no", "off", "")


def _opt_float_form(v: object | None, default: float | None) -> float | None:
    if v is None:
        return default
    s = str(v).strip()
    if not s:
        return default
    try:
        return float(s)
    except ValueError:
        return default


def _call_process_compat(
    body: bytes,
    *,
    depth_m: float,
    strength: float,
    use_ai: bool,
    pipeline: str,
    gpt_preserve_blues: float | None,
    gpt_detail_boost: float | None,
    gpt_warmth_bias: float | None,
    gpt_dehaze_strength: float | None,
    gpt_red_recovery_strength: float | None,
    gpt_noise_reduction: float | None,
) -> bytes:
    """
    Старый inference.process без gpt_* ломался бы с TypeError → HTTP 500 на каждом запросе,
    если main.py уже шлёт новые поля формы. Передаём только то, что есть в сигнатуре.
    """
    sig = inspect.signature(process)
    kwargs: dict = {
        "image_bytes": body,
        "depth_m": depth_m,
        "strength": strength,
        "use_ai": use_ai,
        "pipeline": pipeline,
    }
    gpt_map = {
        "gpt_preserve_blues": gpt_preserve_blues,
        "gpt_detail_boost": gpt_detail_boost,
        "gpt_warmth_bias": gpt_warmth_bias,
        "gpt_dehaze_strength": gpt_dehaze_strength,
        "gpt_red_recovery_strength": gpt_red_recovery_strength,
        "gpt_noise_reduction": gpt_noise_reduction,
    }
    for name, val in gpt_map.items():
        if name in sig.parameters:
            kwargs[name] = val
    return process(**kwargs)


@app.post("/process")
async def process_image(
    image: UploadFile = File(...),
    depth_m: str = Form("10"),
    strength: str = Form("0.7"),
    use_ai: str = Form("true"),
    pipeline: str = Form("default"),
    gpt_preserve_blues: str = Form(""),
    gpt_detail_boost: str = Form(""),
    gpt_warmth_bias: str = Form(""),
    gpt_dehaze_strength: str = Form(""),
    gpt_red_recovery_strength: str = Form(""),
    gpt_noise_reduction: str = Form(""),
):
    # multipart всегда шлёт строки; bool Form() на части клиентов даёт 422/ошибки
    ct = (image.content_type or "").lower()
    if ct and not (ct.startswith("image/") or ct == "application/octet-stream"):
        raise HTTPException(status_code=400, detail=f"Expected an image file, got content-type: {image.content_type}")
    try:
        body = await image.read()
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Failed to read image: {e}")
    if len(body) == 0:
        raise HTTPException(status_code=400, detail="Empty image")
    try:
        dm = float(depth_m)
        st = float(strength)
    except ValueError:
        raise HTTPException(status_code=400, detail="Invalid depth_m or strength")
    try:
        out_bytes = _call_process_compat(
            body,
            depth_m=dm,
            strength=st,
            use_ai=_parse_bool_form(use_ai),
            pipeline=pipeline or "default",
            gpt_preserve_blues=_opt_float_form(gpt_preserve_blues, None),
            gpt_detail_boost=_opt_float_form(gpt_detail_boost, None),
            gpt_warmth_bias=_opt_float_form(gpt_warmth_bias, None),
            gpt_dehaze_strength=_opt_float_form(gpt_dehaze_strength, None),
            gpt_red_recovery_strength=_opt_float_form(gpt_red_recovery_strength, None),
            gpt_noise_reduction=_opt_float_form(gpt_noise_reduction, None),
        )
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
    return Response(content=out_bytes, media_type="image/jpeg")


@app.post("/v1/process/photo/{engine}")
async def process_photo_uvm_compat(
    engine: str,
    image: UploadFile = File(...),
    strength: float = Query(0.7, ge=0.05, le=1.0),
    depth_hint_m: float | None = Query(None),
    quality: str = Query("auto"),
    mode: str = Query("default"),
):
    """
    Совместимость с клиентом DiveHub (NetworkService.processPhotoUnderwaterVisionModule).
    На том же порту, что и Nest AI (8010), чтобы не требовать отдельный UVM только для кнопки Cursor.
    """
    eng = (engine or "").strip().lower()
    if eng not in ("cursor", "seathru"):
        raise HTTPException(
            status_code=400,
            detail="ai-service supports engines 'cursor' and 'seathru' here; run full underwater-vision-module for ai1/ai2.",
        )
    ct = (image.content_type or "").lower()
    if ct and not (ct.startswith("image/") or ct == "application/octet-stream"):
        raise HTTPException(status_code=400, detail=f"Expected image, got {image.content_type}")
    try:
        raw = await image.read()
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Failed to read image: {e}")
    if not raw:
        raise HTTPException(status_code=400, detail="Empty image")
    arr = np.frombuffer(raw, dtype=np.uint8)
    bgr = cv2.imdecode(arr, cv2.IMREAD_COLOR)
    if bgr is None:
        raise HTTPException(status_code=400, detail="invalid image")
    try:
        if eng == "cursor":
            if _run_cursor_underwater_correct is None:
                raise HTTPException(
                    status_code=503,
                    detail="Cursor pipeline unavailable: underwater-vision-module sources not found next to ai-service.",
                )
            out_u8, report = _run_cursor_underwater_correct(bgr, strength=strength, depth_hint_m=depth_hint_m)
            hex_jpeg = _encode_jpeg_hex(out_u8, quality=92)
            report = dict(report)
            report.setdefault("backend", "cursor_correction")
            report.setdefault("engine", "cursor")
        else:
            if _run_sea_thru_cvpr2019 is None:
                raise HTTPException(status_code=503, detail="Sea-Thru module failed to load.")
            out_u8, report = _run_sea_thru_cvpr2019(
                bgr,
                depth_hint_m=depth_hint_m,
                quality=quality,
                strength=strength,
                mode=mode,
            )
            hex_jpeg = _encode_jpeg_hex(out_u8, quality=92)
            report = dict(report)
            report.setdefault("engine", "seathru")
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
    return JSONResponse({"image_jpeg_base64": hex_jpeg, "report": report})


def _process_video_frame_bgr(
    eng: str,
    frame_bgr: np.ndarray,
    *,
    strength: float,
    depth_hint_m: float | None,
    quality: str,
    mode: str,
) -> np.ndarray:
    """Один кадр BGR → BGR (размер как у входного кадра)."""
    dm = float(depth_hint_m) if depth_hint_m is not None else 10.0
    h, w = frame_bgr.shape[:2]
    if eng in ("ai1", "ai2"):
        ok, enc = cv2.imencode(".jpg", frame_bgr, [cv2.IMWRITE_JPEG_QUALITY, 92])
        if not ok:
            raise ValueError("jpeg encode failed")
        st = float(strength)
        if eng == "ai2":
            st = float(min(1.0, max(0.05, st * 1.1)))
        out_jpeg = _call_process_compat(
            enc.tobytes(),
            depth_m=dm,
            strength=st,
            use_ai=True,
            pipeline="default",
            gpt_preserve_blues=None,
            gpt_detail_boost=None,
            gpt_warmth_bias=None,
            gpt_dehaze_strength=None,
            gpt_red_recovery_strength=None,
            gpt_noise_reduction=None,
        )
        dec = cv2.imdecode(np.frombuffer(out_jpeg, dtype=np.uint8), cv2.IMREAD_COLOR)
        if dec is None:
            raise ValueError("jpeg decode failed")
        if dec.shape[0] != h or dec.shape[1] != w:
            dec = cv2.resize(dec, (w, h), interpolation=cv2.INTER_LINEAR)
        return dec
    if eng == "cursor":
        if _run_cursor_underwater_correct is None:
            raise HTTPException(
                status_code=503,
                detail="Cursor pipeline unavailable: underwater-vision-module sources not found next to ai-service.",
            )
        out_u8, _ = _run_cursor_underwater_correct(frame_bgr, strength=strength, depth_hint_m=depth_hint_m)
        return out_u8
    if eng == "seathru":
        if _run_sea_thru_cvpr2019 is None:
            raise HTTPException(status_code=503, detail="Sea-Thru module failed to load.")
        out_u8, _ = _run_sea_thru_cvpr2019(
            frame_bgr,
            depth_hint_m=depth_hint_m,
            quality=quality,
            strength=strength,
            mode=mode,
        )
        return out_u8
    raise HTTPException(
        status_code=400,
        detail="invalid engine for video (ai-service: ai1, ai2, cursor, seathru)",
    )


@app.post("/v1/process/video/{engine}")
async def process_video_uvm_compat(
    engine: str,
    video: UploadFile = File(...),
    strength: float = Query(0.7, ge=0.05, le=1.0),
    depth_hint_m: float | None = Query(None),
    quality: str = Query("auto"),
    mode: str = Query("default"),
    luma_boost: float = Query(1.0, ge=0.0, le=2.0),
    max_side: int = Query(1280, ge=480, le=3840),
    video_mode: str = Query(
        "fast",
        description="fast: Bech keyframes + global LAB; full: Bech every frame",
    ),
    sample_frames: int = Query(
        20,
        ge=10,
        le=30,
        description="Keyframe count for fast mode (evenly spaced)",
    ),
):
    """
    Совместимость с DiveHub (NetworkService.processVideoUnderwaterVisionModule):
    multipart поле `video`, ответ — тело MP4. Ролик до 60 с (оба режима).
    """
    eng = (engine or "").strip().lower()
    if eng not in ("ai1", "ai2", "cursor", "seathru"):
        raise HTTPException(
            status_code=400,
            detail="invalid engine for video",
        )
    vmode = (video_mode or "fast").strip().lower()
    if vmode not in ("fast", "full"):
        raise HTTPException(status_code=400, detail='invalid video_mode (use "fast" or "full")')
    payload = await video.read()
    if not payload:
        raise HTTPException(status_code=400, detail='multipart field "video" is required')
    if (
        vmode == "fast"
        and (
            _process_video_fast_global_bech is None
            or _downscale_bgr_for_process is None
            or _probe_video_meta is None
        )
    ):
        raise HTTPException(
            status_code=503,
            detail="fast video mode unavailable: uvm video_global_tone or video_tone not importable",
        )

    with tempfile.TemporaryDirectory(prefix="ai_svc_video_") as td:
        in_path = os.path.join(td, "in.mp4")
        out_path = os.path.join(td, "out.mp4")
        with open(in_path, "wb") as f:
            f.write(payload)

        if vmode == "fast":
            try:
                n_rep, fps_guess, w0, h0 = _probe_video_meta(in_path)
            except ValueError:
                raise HTTPException(status_code=400, detail="invalid video")
            fps = float(fps_guess) if fps_guess and fps_guess > 0 else 25.0
            if n_rep > 0 and n_rep / fps > _VIDEO_MAX_SEC + 1e-3:
                raise HTTPException(
                    status_code=400,
                    detail=f"video_too_long: max {_VIDEO_MAX_SEC:g}s",
                )
            try:

                def _bech(work: np.ndarray) -> np.ndarray:
                    return _process_video_frame_bgr(
                        eng,
                        work,
                        strength=strength,
                        depth_hint_m=depth_hint_m,
                        quality=quality,
                        mode=mode,
                    )

                frames, keys = _process_video_fast_global_bech(
                    in_path,
                    out_path,
                    fps=fps,
                    fw=w0,
                    fh=h0,
                    max_side=max_side,
                    sample_frames=sample_frames,
                    downscale_bgr_for_process=_downscale_bgr_for_process,
                    bech_on_bgr=_bech,
                )
            except ValueError as e:
                raise HTTPException(status_code=400, detail=str(e))
            except HTTPException:
                raise
            except Exception as e:
                raise HTTPException(status_code=500, detail=f"video_processing_failed: {e}") from e
            if frames == 0 or not os.path.isfile(out_path):
                raise HTTPException(status_code=400, detail="empty video stream")
            out_bytes = open(out_path, "rb").read()
            return Response(
                content=out_bytes,
                media_type="video/mp4",
                headers={
                    "X-UVM-Engine": eng,
                    "X-UVM-Frames": str(frames),
                    "X-UVM-Backend": "ai-service",
                    "X-UVM-Video-Mode": "fast",
                    "X-UVM-Fast-Keyframes": str(keys),
                },
            )

        cap = cv2.VideoCapture(in_path)
        if not cap.isOpened():
            raise HTTPException(status_code=400, detail="invalid video")
        fps = cap.get(cv2.CAP_PROP_FPS)
        if not fps or fps <= 0:
            fps = 25.0
        n0 = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
        if n0 > 0 and n0 / float(fps) > _VIDEO_MAX_SEC + 1e-3:
            cap.release()
            raise HTTPException(
                status_code=400,
                detail=f"video_too_long: max {_VIDEO_MAX_SEC:g}s",
            )
        fw = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
        fh = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
        if fw <= 0 or fh <= 0:
            cap.release()
            raise HTTPException(status_code=400, detail="invalid video geometry")
        writer = cv2.VideoWriter(out_path, cv2.VideoWriter_fourcc(*"mp4v"), float(fps), (fw, fh))
        if not writer.isOpened():
            cap.release()
            raise HTTPException(status_code=500, detail="video writer init failed")
        frames = 0
        try:
            while True:
                ok, frame = cap.read()
                if not ok:
                    break
                try:
                    _video_duration_guard(frame_index=frames, fps=float(fps))
                except ValueError as e:
                    raise HTTPException(status_code=400, detail=str(e)) from e
                try:
                    if _downscale_bgr_for_process is not None:
                        work, orig_wh = _downscale_bgr_for_process(frame, max_side)
                    else:
                        work, orig_wh = frame, (frame.shape[1], frame.shape[0])
                    out_small = _process_video_frame_bgr(
                        eng,
                        work,
                        strength=strength,
                        depth_hint_m=depth_hint_m,
                        quality=quality,
                        mode=mode,
                    )
                except HTTPException:
                    raise
                except Exception as e:
                    raise HTTPException(status_code=500, detail=f"video_processing_failed: {e}") from e
                if luma_boost > 0 and _post_lift_video_bgr is not None:
                    out_small = _post_lift_video_bgr(out_small, engine=eng, amount=luma_boost)
                if _upscale_bgr_to_original is not None:
                    out_frame = _upscale_bgr_to_original(out_small, orig_wh)
                else:
                    out_frame = out_small
                writer.write(out_frame)
                frames += 1
        finally:
            cap.release()
            writer.release()

        if frames == 0 or not os.path.isfile(out_path):
            raise HTTPException(status_code=400, detail="empty video stream")
        out_bytes = open(out_path, "rb").read()

    return Response(
        content=out_bytes,
        media_type="video/mp4",
        headers={
            "X-UVM-Engine": eng,
            "X-UVM-Frames": str(frames),
            "X-UVM-Backend": "ai-service",
            "X-UVM-Video-Mode": "full",
        },
    )


# --- SeaSplat (ICRA-style contract): same paths as underwater-vision-module uvm.api.app ---

if _seasplat is not None:
    _ss = _seasplat  # closure for handlers

    @app.post("/v1/seasplat/scenes")
    async def seasplat_upload_scene(
        images: list[UploadFile] = File(...),
        poses_json: str | None = Query(None, description="optional JSON list of camera poses"),
    ):
        frame_names: list[str] = []
        frame_bgr: list[np.ndarray] = []
        for i, f in enumerate(images):
            raw = await f.read()
            arr = np.frombuffer(raw, dtype=np.uint8)
            bgr = cv2.imdecode(arr, cv2.IMREAD_COLOR)
            if bgr is None:
                return JSONResponse({"error": f"invalid image at index {i}"}, status_code=400)
            frame_bgr.append(bgr)
            frame_names.append(f.filename or f"frame_{i:04d}.jpg")
        if not frame_bgr:
            return JSONResponse({"error": "at least one image is required"}, status_code=400)
        try:
            return _ss.upload_scene(frame_bgr, frame_names, poses_json)  # type: ignore[union-attr]
        except Exception as e:
            return JSONResponse({"error": "scene_upload_failed", "detail": str(e)}, status_code=500)

    @app.get("/v1/seasplat/scenes/{scene_id}")
    def seasplat_scene_status(scene_id: str):
        s = _ss.get_scene(scene_id)  # type: ignore[union-attr]
        if s is None:
            return JSONResponse({"error": "scene_not_found"}, status_code=404)
        return {
            "scene_id": scene_id,
            "status": s["status"],
            "frame_count": len(s.get("frame_meta", [])),
            "created_at": s["created_at"],
        }

    @app.post("/v1/seasplat/jobs")
    def seasplat_run_job(payload: dict):
        scene_id = str(payload.get("scene_id") or "").strip()
        if not scene_id:
            return JSONResponse({"error": "scene_id is required"}, status_code=400)
        try:
            j = _ss.create_job(scene_id)  # type: ignore[union-attr]
        except KeyError:
            return JSONResponse({"error": "scene_not_found"}, status_code=404)
        except Exception as e:
            return JSONResponse({"error": "job_create_failed", "detail": str(e)}, status_code=500)
        return {"job_id": j["job_id"], "status": j["status"], "scene_id": scene_id}

    @app.get("/v1/seasplat/jobs/{job_id}")
    def seasplat_job_status(job_id: str):
        j = _ss.get_job(job_id)  # type: ignore[union-attr]
        if j is None:
            return JSONResponse({"error": "job_not_found"}, status_code=404)
        return {
            "job_id": job_id,
            "scene_id": j["scene_id"],
            "status": j["status"],
            "progress": j["progress"],
            "error": j.get("error"),
        }

    @app.get("/v1/seasplat/jobs/{job_id}/render")
    def seasplat_job_render(job_id: str):
        j = _ss.get_job(job_id)  # type: ignore[union-attr]
        if j is None:
            return JSONResponse({"error": "job_not_found"}, status_code=404)
        if j.get("status") == "failed":
            return JSONResponse(
                {"error": "job_failed", "detail": j.get("error") or "unknown"},
                status_code=500,
            )
        if j["status"] != "completed":
            return JSONResponse({"error": "job_not_ready", "status": j["status"]}, status_code=409)
        try:
            return _ss.job_render(job_id)  # type: ignore[union-attr]
        except Exception as e:
            return JSONResponse({"error": "render_failed", "detail": str(e)}, status_code=500)
