from __future__ import annotations

import asyncio
import os
import tempfile
import threading
from typing import Any

import cv2
import numpy as np
from fastapi import FastAPI, File, Query, Request, UploadFile
from fastapi.responses import JSONResponse, Response

from uvm.api.jpeg_utils import encode_jpeg_hex
from uvm.api.image_decode import decode_upload_bgr
from uvm.api.video_global_tone import (
    DEFAULT_SAMPLE_FRAMES,
    MAX_VIDEO_DURATION_SEC,
    SAMPLE_FRAMES_MAX,
    SAMPLE_FRAMES_MIN,
    assert_video_within_max_duration,
    output_size_for_max_side,
    prepare_opencv_input,
    process_video_fast_global_bech,
    probe_video_meta,
    remux_original_audio,
)
from uvm.api.video_tone import downscale_bgr_for_process
from uvm.pipeline.nikolaj_bech_color_correction import process_bgr_uint8

app = FastAPI(title='Underwater Vision Module', version='0.2.1')

_VALID_ENGINES = frozenset({'ai1', 'ai2', 'cursor', 'seathru'})
# Match iOS/Android Dive Editor working size so local and server Bech histograms agree.
_PHOTO_MAX_SIDE = 2048

# One heavy video job at a time — keeps /health responsive and avoids OOM piles.
_video_busy = False
_video_busy_lock = threading.Lock()


def _try_begin_video_job() -> bool:
    global _video_busy
    with _video_busy_lock:
        if _video_busy:
            return False
        _video_busy = True
        return True


def _end_video_job() -> None:
    global _video_busy
    with _video_busy_lock:
        _video_busy = False


def _run_bech(bgr: np.ndarray, eng: str, strength: float = 1.0) -> tuple[np.ndarray, dict]:
    out, rep = process_bgr_uint8(bgr, strength)
    rep = dict(rep)
    rep['engine'] = eng
    return out, rep


async def _process_photo_core(
    request: Request,
    eng: str,
    image: UploadFile,
    strength: float,
    *,
    route_tag: str,
) -> JSONResponse:
    del request
    if eng not in _VALID_ENGINES:
        return JSONResponse({'error': 'invalid engine', 'allowed': sorted(_VALID_ENGINES)}, status_code=400)

    print(f'[uvm] {route_tag} engine={eng!r} strength={strength}', flush=True)

    data = await image.read()
    bgr, decoder_tag = decode_upload_bgr(data)
    if bgr is None:
        return JSONResponse({'error': 'invalid image'}, status_code=400)

    report: dict = {'engine': eng, 'strength': strength, 'decoder': decoder_tag}
    try:
        work, _orig_wh = downscale_bgr_for_process(bgr, _PHOTO_MAX_SIDE)
        out, r = _run_bech(work, eng, strength)
        report.update(r)
        report['photo_max_side'] = _PHOTO_MAX_SIDE
    except Exception as e:
        return JSONResponse({'error': 'processing_failed', 'detail': str(e)}, status_code=500)

    try:
        hex_jpeg = encode_jpeg_hex(out)
    except Exception:
        return JSONResponse({'error': 'encode failed'}, status_code=500)

    return JSONResponse(
        {
            'image_jpeg_base64': hex_jpeg,
            'report': report,
        }
    )


def _process_frame_with_engine(eng: str, bgr: np.ndarray) -> tuple[np.ndarray, dict]:
    return _run_bech(bgr, eng)


def _process_video_file(
    *,
    eng: str,
    mode: str,
    in_path: str,
    out_path: str,
    max_side: int,
    sample_frames: int,
) -> dict[str, Any]:
    """CPU-bound video pipeline — must run off the asyncio event loop."""
    decode_path = prepare_opencv_input(in_path)
    if mode == 'fast':
        n_rep, fps_guess, w0, h0 = probe_video_meta(decode_path)
        fps = float(fps_guess) if fps_guess and fps_guess > 0 else 25.0
        if n_rep > 0 and n_rep / fps > MAX_VIDEO_DURATION_SEC + 1e-3:
            return {
                'error': 'video_too_long',
                'detail': f'max {MAX_VIDEO_DURATION_SEC:g}s',
                'reported_sec': round(n_rep / fps, 2),
                'status': 400,
            }

        def _bech(bgr: np.ndarray) -> np.ndarray:
            out, _rep = _process_frame_with_engine(eng, bgr)
            return out

        frames, keys = process_video_fast_global_bech(
            decode_path,
            out_path,
            fps=fps,
            fw=w0,
            fh=h0,
            max_side=max_side,
            sample_frames=sample_frames,
            downscale_bgr_for_process=downscale_bgr_for_process,
            bech_on_bgr=_bech,
        )
        if frames == 0 or not os.path.isfile(out_path):
            return {'error': 'empty video stream', 'status': 400}
        audio_ok = remux_original_audio(out_path, in_path)
        return {
            'frames': frames,
            'keys': keys,
            'backend': 'nikolaj_bech_underwater_color_correction',
            'mode': 'fast',
            'audio': bool(audio_ok),
            'status': 200,
        }

    cap = cv2.VideoCapture(decode_path)
    if not cap.isOpened():
        return {'error': 'invalid video', 'status': 400}
    fps = cap.get(cv2.CAP_PROP_FPS)
    if not fps or fps <= 0:
        fps = 25.0
    n0 = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    if n0 > 0 and n0 / float(fps) > MAX_VIDEO_DURATION_SEC + 1e-3:
        cap.release()
        return {
            'error': 'video_too_long',
            'detail': f'max {MAX_VIDEO_DURATION_SEC:g}s',
            'reported_sec': round(n0 / float(fps), 2),
            'status': 400,
        }
    w = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    h = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    if w <= 0 or h <= 0:
        cap.release()
        return {'error': 'invalid video geometry', 'status': 400}
    out_w, out_h = output_size_for_max_side(w, h, max_side)
    writer = cv2.VideoWriter(out_path, cv2.VideoWriter_fourcc(*'mp4v'), float(fps), (out_w, out_h))
    if not writer.isOpened():
        cap.release()
        return {'error': 'video writer init failed', 'status': 500}
    frames = 0
    last_report: dict = {}
    try:
        while True:
            ok, frame = cap.read()
            if not ok:
                break
            assert_video_within_max_duration(frame_index=frames, fps=float(fps))
            work, _orig_wh = downscale_bgr_for_process(frame, max_side)
            out_small, rep = _process_frame_with_engine(eng, work)
            if out_small.shape[1] != out_w or out_small.shape[0] != out_h:
                out_small = cv2.resize(out_small, (out_w, out_h), interpolation=cv2.INTER_LINEAR)
            writer.write(out_small)
            frames += 1
            last_report = rep
    finally:
        cap.release()
        writer.release()

    if frames == 0 or not os.path.isfile(out_path):
        return {'error': 'empty video stream', 'status': 400}
    audio_ok = remux_original_audio(out_path, in_path)
    return {
        'frames': frames,
        'keys': 0,
        'backend': str(last_report.get('backend', 'unknown')),
        'mode': 'full',
        'audio': bool(audio_ok),
        'status': 200,
    }


@app.get('/health')
def health() -> dict:
    with _video_busy_lock:
        busy = _video_busy
    return {
        'status': 'ok',
        'module': 'underwater-vision-module',
        'backend': 'nikolaj_bech_underwater_color_correction',
        'engines': {e: True for e in _VALID_ENGINES},
        'video_busy': busy,
    }


@app.post('/v1/process/photo/{engine}')
async def process_photo_by_path(
    request: Request,
    engine: str,
    image: UploadFile = File(...),
    strength: float = Query(1.0, ge=0.0, le=1.0),
):
    eng = (engine or '').strip().lower()
    return await _process_photo_core(
        request, eng, image, strength, route_tag='process_photo_by_path'
    )


@app.post('/v1/process/video')
async def process_video_stub():
    return {
        'status': 'not_implemented',
        'message': 'Use POST /v1/process/video/{engine} with engine in ai1|ai2|cursor|seathru.',
    }


@app.post('/v1/process/video/{engine}')
async def process_video_by_path(
    engine: str,
    video: UploadFile = File(...),
    max_side: int = Query(
        1280,
        ge=480,
        le=3840,
        description='Long edge for per-frame processing (performance)',
    ),
    video_mode: str = Query(
        'fast',
        description='fast: Bech on keyframes + global LAB tone; full: Bech every frame',
    ),
    sample_frames: int = Query(
        DEFAULT_SAMPLE_FRAMES,
        ge=SAMPLE_FRAMES_MIN,
        le=SAMPLE_FRAMES_MAX,
        description='Keyframes for fast mode (evenly spaced)',
    ),
):
    eng = (engine or '').strip().lower()
    if eng not in _VALID_ENGINES:
        return JSONResponse({'error': 'invalid engine for video', 'allowed': sorted(_VALID_ENGINES)}, status_code=400)
    mode = (video_mode or 'fast').strip().lower()
    if mode not in ('fast', 'full'):
        return JSONResponse(
            {
                'error': 'invalid video_mode',
                'allowed': ['fast', 'full'],
            },
            status_code=400,
        )
    if not _try_begin_video_job():
        return JSONResponse(
            {
                'error': 'video_busy',
                'detail': 'Another video is processing; retry shortly.',
            },
            status_code=503,
            headers={'Retry-After': '15'},
        )

    try:
        payload = await video.read()
        if not payload:
            return JSONResponse({'error': 'multipart field "video" is required'}, status_code=400)

        with tempfile.TemporaryDirectory(prefix='uvm_video_') as td:
            in_path = os.path.join(td, 'in.mp4')
            out_path = os.path.join(td, 'out.mp4')
            with open(in_path, 'wb') as f:
                f.write(payload)

            print(
                f'[uvm] process_video engine={eng!r} mode={mode} max_side={max_side} '
                f'bytes={len(payload)} sample_frames={sample_frames}',
                flush=True,
            )
            try:
                result = await asyncio.to_thread(
                    _process_video_file,
                    eng=eng,
                    mode=mode,
                    in_path=in_path,
                    out_path=out_path,
                    max_side=max_side,
                    sample_frames=sample_frames,
                )
            except ValueError as e:
                return JSONResponse({'error': 'video_processing_failed', 'detail': str(e)}, status_code=400)
            except Exception as e:
                return JSONResponse({'error': 'video_processing_failed', 'detail': str(e)}, status_code=500)

            status = int(result.get('status', 500))
            if status != 200:
                body = {k: v for k, v in result.items() if k != 'status'}
                return JSONResponse(body, status_code=status)

            out_bytes = open(out_path, 'rb').read()
            headers = {
                'X-UVM-Engine': eng,
                'X-UVM-Frames': str(result.get('frames', 0)),
                'X-UVM-Backend': str(result.get('backend', 'unknown')),
                'X-UVM-Video-Mode': str(result.get('mode', mode)),
                'X-UVM-Audio': '1' if result.get('audio') else '0',
            }
            if mode == 'fast':
                headers['X-UVM-Fast-Keyframes'] = str(result.get('keys', 0))
            print(
                f'[uvm] process_video done engine={eng!r} frames={result.get("frames")} '
                f'audio={result.get("audio")} out_bytes={len(out_bytes)}',
                flush=True,
            )
            return Response(content=out_bytes, media_type='video/mp4', headers=headers)
    finally:
        _end_video_job()
