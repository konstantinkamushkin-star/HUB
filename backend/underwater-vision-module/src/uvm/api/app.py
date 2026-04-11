from __future__ import annotations

import os
import tempfile

import cv2
import numpy as np
from fastapi import FastAPI, File, Query, Request, UploadFile
from fastapi.responses import JSONResponse, Response

from uvm.api.jpeg_utils import encode_jpeg_hex
from uvm.api.video_tone import (
    downscale_bgr_for_process,
    post_lift_underwater_video_bgr,
    upscale_bgr_to_original,
)
from uvm.api.nn_runtime import get_nn_service
from uvm.api.seasplat_runtime import SeaSplatRuntime
from uvm.pipeline.cursor_correction import run_cursor_underwater_correct
from uvm.pipeline.engine import UnderwaterPipeline
from uvm.pipeline.sea_thru_cvpr2019 import (
    get_depth_backend_status,
    initialize_depth_backend,
    run_sea_thru_cvpr2019,
)

app = FastAPI(title='Underwater Vision Module', version='0.1.0')
_nn = get_nn_service()
pipeline = UnderwaterPipeline()
_seasplat = SeaSplatRuntime()

_VALID_ENGINES = frozenset({'ai1', 'ai2', 'cursor', 'seathru', 'seasplat'})


@app.on_event('startup')
def startup_depth_backend() -> None:
    st = initialize_depth_backend()
    print(f"[uvm-seathru-depth] {st.get('message', 'status unavailable')}", flush=True)


def _ai_slots_backend() -> str:
    """UVM_AI_BACKEND: cursor (default, стабильный), pipeline, unet."""
    raw = (os.environ.get('UVM_AI_BACKEND') or 'cursor').strip().lower()
    if raw in ('nn', 'unet', 'neural', 'checkpoint', 'pt'):
        return 'unet'
    if raw in ('cursor', 'cursor_like', 'legacy'):
        return 'cursor'
    return 'pipeline'


def _ai_slot_service_ready(slot: str) -> bool:
    if _ai_slots_backend() == 'unet':
        return _nn.available(slot)
    return True


async def _process_photo_core(
    request: Request,
    eng: str,
    image: UploadFile,
    strength: float,
    depth_hint_m: float | None,
    quality: str,
    mode: str,
    *,
    route_tag: str,
) -> JSONResponse:
    if eng not in _VALID_ENGINES:
        return JSONResponse({'error': 'invalid engine', 'allowed': list(_VALID_ENGINES)}, status_code=400)

    print(f'[uvm] {route_tag} engine={eng!r} strength={strength}', flush=True)

    data = await image.read()
    arr = np.frombuffer(data, dtype=np.uint8)
    bgr = cv2.imdecode(arr, cv2.IMREAD_COLOR)
    if bgr is None:
        return JSONResponse({'error': 'invalid image'}, status_code=400)

    report: dict = {'engine': eng, 'strength': strength}

    try:
        if eng == 'cursor':
            out, r = run_cursor_underwater_correct(bgr, strength=strength, depth_hint_m=depth_hint_m)
            report['backend'] = 'cursor_correction'
            report.update(r)
        elif eng == 'seathru':
            depth_status = get_depth_backend_status()
            use_quality = quality
            use_strength = strength
            if not depth_status.get('loaded'):
                # With proxy depth backend, use safer Sea-Thru settings to reduce green cast.
                use_quality = 'fast'
                use_strength = float(min(strength, 0.6))
            out, r = run_sea_thru_cvpr2019(
                bgr,
                depth_hint_m=depth_hint_m,
                quality=use_quality,
                strength=use_strength,
                mode=mode,
            )
            report['backend'] = 'sea_thru_cvpr2019'
            report.update(r)
            report['engine'] = 'seathru'
            if not depth_status.get('loaded'):
                report['note'] = 'seathru_proxy_depth_safe_tuning'
        elif eng == 'seasplat':
            return JSONResponse(
                {
                    'error': 'not_implemented',
                    'detail': (
                        'SeaSplat requires multi-view scene optimization with camera poses '
                        'and cannot be executed from a single-image /v1/process/photo request.'
                    ),
                    'hint': (
                        'Use a dedicated SeaSplat training/rendering pipeline endpoint '
                        'that accepts calibrated multi-view sequences.'
                    ),
                },
                status_code=501,
            )
        elif eng in ('ai1', 'ai2'):
            mode = _ai_slots_backend()
            report['ai_slots_backend'] = mode
            if mode == 'unet':
                if not _nn.available(eng):
                    return JSONResponse(
                        {
                            'error': f'checkpoint for {eng} not found',
                            'hint': 'Set UVM_CKPT_AI1 / UVM_CKPT_AI2 or place best.pt under checkpoints_*',
                        },
                        status_code=503,
                    )
                out, r = _nn.infer_bgr(bgr, eng)
                report['backend'] = 'unet'
                report.update(r)
            elif mode == 'cursor':
                out, r = run_cursor_underwater_correct(bgr, strength=strength, depth_hint_m=depth_hint_m)
                report['backend'] = 'cursor_correction'
                report.update(r)
                report['engine'] = eng
                report['note'] = 'ai_slot_uses_same_algo_as_cursor_button'
            else:
                if eng == 'ai1':
                    out, r = pipeline.process_photo(bgr, strength=strength, depth_hint_m=depth_hint_m)
                else:
                    s2 = float(min(1.0, max(0.05, strength * 1.1)))
                    out, r = pipeline.process_photo(
                        bgr,
                        strength=s2,
                        depth_hint_m=depth_hint_m,
                        clahe_boost=0.42,
                        red_scale=1.12,
                    )
                report['backend'] = 'underwater_pipeline'
                report.update(r)
                report['engine'] = eng
        else:
            return JSONResponse({'error': 'invalid engine'}, status_code=400)
    except FileNotFoundError as e:
        return JSONResponse({'error': str(e)}, status_code=503)
    except Exception as e:
        return JSONResponse({'error': 'processing_failed', 'detail': str(e)}, status_code=500)

    try:
        hex_jpeg = encode_jpeg_hex(out, quality=92)
    except Exception:
        return JSONResponse({'error': 'encode failed'}, status_code=500)

    return JSONResponse(
        {
            'image_jpeg_base64': hex_jpeg,
            'report': report,
        }
    )


def _process_frame_with_engine(
    eng: str,
    bgr: np.ndarray,
    *,
    strength: float,
    depth_hint_m: float | None,
    quality: str,
    mode: str,
) -> tuple[np.ndarray, dict]:
    report: dict = {'engine': eng, 'strength': strength}
    if eng == 'cursor':
        out, r = run_cursor_underwater_correct(bgr, strength=strength, depth_hint_m=depth_hint_m)
        report['backend'] = 'cursor_correction'
        report.update(r)
        return out, report
    if eng == 'seathru':
        depth_status = get_depth_backend_status()
        use_quality = quality
        use_strength = strength
        if not depth_status.get('loaded'):
            use_quality = 'fast'
            use_strength = float(min(strength, 0.6))
        out, r = run_sea_thru_cvpr2019(
            bgr,
            depth_hint_m=depth_hint_m,
            quality=use_quality,
            strength=use_strength,
            mode=mode,
        )
        report['backend'] = 'sea_thru_cvpr2019'
        report.update(r)
        report['engine'] = 'seathru'
        if not depth_status.get('loaded'):
            report['note'] = 'seathru_proxy_depth_safe_tuning'
        return out, report
    if eng in ('ai1', 'ai2'):
        slot_mode = _ai_slots_backend()
        report['ai_slots_backend'] = slot_mode
        if slot_mode == 'unet':
            if not _nn.available(eng):
                raise RuntimeError(f'checkpoint for {eng} not found')
            out, r = _nn.infer_bgr(bgr, eng)
            report['backend'] = 'unet'
            report.update(r)
            return out, report
        if slot_mode == 'cursor':
            out, r = run_cursor_underwater_correct(bgr, strength=strength, depth_hint_m=depth_hint_m)
            report['backend'] = 'cursor_correction'
            report.update(r)
            report['engine'] = eng
            report['note'] = 'ai_slot_uses_same_algo_as_cursor_button'
            return out, report
        if eng == 'ai1':
            out, r = pipeline.process_photo(bgr, strength=strength, depth_hint_m=depth_hint_m)
        else:
            s2 = float(min(1.0, max(0.05, strength * 1.1)))
            out, r = pipeline.process_photo(
                bgr,
                strength=s2,
                depth_hint_m=depth_hint_m,
                clahe_boost=0.42,
                red_scale=1.12,
            )
        report['backend'] = 'underwater_pipeline'
        report.update(r)
        report['engine'] = eng
        return out, report
    raise RuntimeError(f'invalid engine for video: {eng}')


@app.get('/health')
def health() -> dict:
    ss = _seasplat.health()
    return {
        'status': 'ok',
        'module': 'underwater-vision-module',
        'ai_slots_backend': _ai_slots_backend(),
        'seathru_depth': get_depth_backend_status(),
        'seasplat': ss,
        'engines': {
            'ai1': _ai_slot_service_ready('ai1'),
            'ai2': _ai_slot_service_ready('ai2'),
            'cursor': True,
            'seathru': True,
            'seasplat': True,
        },
    }


@app.post('/v1/process/photo/{engine}')
async def process_photo_by_path(
    request: Request,
    engine: str,
    image: UploadFile = File(...),
    strength: float = Query(0.7, ge=0.05, le=1.0),
    depth_hint_m: float | None = Query(None, description='optional depth in meters'),
    quality: str = Query('auto'),
    mode: str = Query('default'),
):
    """engine в path — надёжно с multipart (query-параметр engine на части клиентов терялся)."""
    eng = (engine or '').strip().lower()
    return await _process_photo_core(
        request, eng, image, strength, depth_hint_m, quality, mode, route_tag='process_photo_by_path'
    )


@app.post('/v1/process/video')
async def process_video_stub():
    return {
        'status': 'not_implemented',
        'message': 'Temporal module scaffold ready. Add optical flow + recurrent/transformer block.',
    }


@app.post('/v1/process/video/{engine}')
async def process_video_by_path(
    engine: str,
    video: UploadFile = File(...),
    strength: float = Query(0.7, ge=0.05, le=1.0),
    depth_hint_m: float | None = Query(None, description='optional depth in meters'),
    quality: str = Query('auto'),
    mode: str = Query('default'),
    luma_boost: float = Query(
        1.0,
        ge=0.0,
        le=2.0,
        description='Пост-осветление экспорта: 0=выкл., 1=по умолчанию (рекомендуется для MP4)',
    ),
    max_side: int = Query(
        1280,
        ge=480,
        le=3840,
        description='Длинная сторона кадра для обработки (меньше — быстрее; выход всё равно в исходном разрешении)',
    ),
):
    eng = (engine or '').strip().lower()
    if eng not in _VALID_ENGINES or eng == 'seasplat':
        return JSONResponse({'error': 'invalid engine for video', 'allowed': ['ai1', 'ai2', 'cursor', 'seathru']}, status_code=400)
    payload = await video.read()
    if not payload:
        return JSONResponse({'error': 'multipart field "video" is required'}, status_code=400)

    with tempfile.TemporaryDirectory(prefix='uvm_video_') as td:
        in_path = os.path.join(td, 'in.mp4')
        out_path = os.path.join(td, 'out.mp4')
        with open(in_path, 'wb') as f:
            f.write(payload)

        cap = cv2.VideoCapture(in_path)
        if not cap.isOpened():
            return JSONResponse({'error': 'invalid video'}, status_code=400)
        fps = cap.get(cv2.CAP_PROP_FPS)
        if not fps or fps <= 0:
            fps = 25.0
        w = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
        h = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
        if w <= 0 or h <= 0:
            cap.release()
            return JSONResponse({'error': 'invalid video geometry'}, status_code=400)
        writer = cv2.VideoWriter(out_path, cv2.VideoWriter_fourcc(*'mp4v'), float(fps), (w, h))
        if not writer.isOpened():
            cap.release()
            return JSONResponse({'error': 'video writer init failed'}, status_code=500)
        frames = 0
        last_report: dict = {}
        try:
            while True:
                ok, frame = cap.read()
                if not ok:
                    break
                work, orig_wh = downscale_bgr_for_process(frame, max_side)
                out_small, rep = _process_frame_with_engine(
                    eng,
                    work,
                    strength=strength,
                    depth_hint_m=depth_hint_m,
                    quality=quality,
                    mode=mode,
                )
                if luma_boost > 0:
                    out_small = post_lift_underwater_video_bgr(out_small, engine=eng, amount=luma_boost)
                out_frame = upscale_bgr_to_original(out_small, orig_wh)
                writer.write(out_frame)
                frames += 1
                last_report = rep
        except Exception as e:
            cap.release()
            writer.release()
            return JSONResponse({'error': 'video_processing_failed', 'detail': str(e)}, status_code=500)
        finally:
            cap.release()
            writer.release()

        if frames == 0 or not os.path.isfile(out_path):
            return JSONResponse({'error': 'empty video stream'}, status_code=400)
        out_bytes = open(out_path, 'rb').read()
        return Response(
            content=out_bytes,
            media_type='video/mp4',
            headers={
                'X-UVM-Engine': eng,
                'X-UVM-Frames': str(frames),
                'X-UVM-Backend': str(last_report.get('backend', 'unknown')),
            },
        )


@app.post('/v1/seasplat/scenes')
async def seasplat_upload_scene(
    images: list[UploadFile] = File(...),
    poses_json: str | None = Query(None, description='optional JSON list of camera poses'),
):
    frame_names: list[str] = []
    frame_bgr: list[np.ndarray] = []
    for i, f in enumerate(images):
        raw = await f.read()
        arr = np.frombuffer(raw, dtype=np.uint8)
        bgr = cv2.imdecode(arr, cv2.IMREAD_COLOR)
        if bgr is None:
            return JSONResponse({'error': f'invalid image at index {i}'}, status_code=400)
        frame_bgr.append(bgr)
        frame_names.append(f.filename or f'frame_{i:04d}.jpg')
    if not frame_bgr:
        return JSONResponse({'error': 'at least one image is required'}, status_code=400)
    try:
        return _seasplat.upload_scene(frame_bgr, frame_names, poses_json)
    except Exception as e:
        return JSONResponse({'error': 'scene_upload_failed', 'detail': str(e)}, status_code=500)


@app.get('/v1/seasplat/scenes/{scene_id}')
def seasplat_scene_status(scene_id: str):
    s = _seasplat.get_scene(scene_id)
    if s is None:
        return JSONResponse({'error': 'scene_not_found'}, status_code=404)
    return {
        'scene_id': scene_id,
        'status': s['status'],
        'frame_count': len(s.get('frame_meta', [])),
        'created_at': s['created_at'],
    }


@app.post('/v1/seasplat/jobs')
def seasplat_run_job(payload: dict):
    scene_id = str(payload.get('scene_id') or '').strip()
    if not scene_id:
        return JSONResponse({'error': 'scene_id is required'}, status_code=400)
    try:
        j = _seasplat.create_job(scene_id)
    except KeyError:
        return JSONResponse({'error': 'scene_not_found'}, status_code=404)
    except Exception as e:
        return JSONResponse({'error': 'job_create_failed', 'detail': str(e)}, status_code=500)
    return {'job_id': j['job_id'], 'status': j['status'], 'scene_id': scene_id}


@app.get('/v1/seasplat/jobs/{job_id}')
def seasplat_job_status(job_id: str):
    j = _seasplat.get_job(job_id)
    if j is None:
        return JSONResponse({'error': 'job_not_found'}, status_code=404)
    return {
        'job_id': job_id,
        'scene_id': j['scene_id'],
        'status': j['status'],
        'progress': j['progress'],
        'error': j.get('error'),
    }


@app.get('/v1/seasplat/jobs/{job_id}/render')
def seasplat_job_render(job_id: str):
    j = _seasplat.get_job(job_id)
    if j is None:
        return JSONResponse({'error': 'job_not_found'}, status_code=404)
    if j.get('status') == 'failed':
        return JSONResponse({'error': 'job_failed', 'detail': j.get('error') or 'unknown'}, status_code=500)
    if j['status'] != 'completed':
        return JSONResponse({'error': 'job_not_ready', 'status': j['status']}, status_code=409)
    try:
        return _seasplat.job_render(job_id)
    except Exception as e:
        return JSONResponse({'error': 'render_failed', 'detail': str(e)}, status_code=500)
