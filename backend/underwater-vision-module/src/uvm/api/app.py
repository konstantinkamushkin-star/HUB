from __future__ import annotations

import os
import tempfile

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
    process_video_fast_global_bech,
    probe_video_meta,
)
from uvm.api.video_tone import (
    downscale_bgr_for_process,
    upscale_bgr_to_original,
)
from uvm.pipeline.nikolaj_bech_color_correction import process_bgr_uint8

app = FastAPI(title='Underwater Vision Module', version='0.2.0')

_VALID_ENGINES = frozenset({'ai1', 'ai2', 'cursor', 'seathru'})


def _run_bech(bgr: np.ndarray, eng: str) -> tuple[np.ndarray, dict]:
    out, rep = process_bgr_uint8(bgr)
    rep = dict(rep)
    rep['engine'] = eng
    return out, rep


async def _process_photo_core(
    request: Request,
    eng: str,
    image: UploadFile,
    *,
    route_tag: str,
) -> JSONResponse:
    del request
    if eng not in _VALID_ENGINES:
        return JSONResponse({'error': 'invalid engine', 'allowed': sorted(_VALID_ENGINES)}, status_code=400)

    print(f'[uvm] {route_tag} engine={eng!r}', flush=True)

    data = await image.read()
    bgr, decoder_tag = decode_upload_bgr(data)
    if bgr is None:
        return JSONResponse({'error': 'invalid image'}, status_code=400)

    report: dict = {'engine': eng, 'decoder': decoder_tag}
    try:
        out, r = _run_bech(bgr, eng)
        report.update(r)
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


@app.get('/health')
def health() -> dict:
    return {
        'status': 'ok',
        'module': 'underwater-vision-module',
        'backend': 'nikolaj_bech_underwater_color_correction',
        'engines': {e: True for e in _VALID_ENGINES},
    }


@app.post('/v1/process/photo/{engine}')
async def process_photo_by_path(
    request: Request,
    engine: str,
    image: UploadFile = File(...),
):
    eng = (engine or '').strip().lower()
    return await _process_photo_core(request, eng, image, route_tag='process_photo_by_path')


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
    payload = await video.read()
    if not payload:
        return JSONResponse({'error': 'multipart field "video" is required'}, status_code=400)

    with tempfile.TemporaryDirectory(prefix='uvm_video_') as td:
        in_path = os.path.join(td, 'in.mp4')
        out_path = os.path.join(td, 'out.mp4')
        with open(in_path, 'wb') as f:
            f.write(payload)

        if mode == 'fast':
            try:
                n_rep, fps_guess, w0, h0 = probe_video_meta(in_path)
            except ValueError as e:
                return JSONResponse({'error': 'invalid video', 'detail': str(e)}, status_code=400)
            fps = float(fps_guess) if fps_guess and fps_guess > 0 else 25.0
            if n_rep > 0 and n_rep / fps > MAX_VIDEO_DURATION_SEC + 1e-3:
                return JSONResponse(
                    {
                        'error': 'video_too_long',
                        'detail': f'max {MAX_VIDEO_DURATION_SEC:g}s',
                        'reported_sec': round(n_rep / fps, 2),
                    },
                    status_code=400,
                )
            try:

                def _bech(bgr: np.ndarray) -> np.ndarray:
                    out, _rep = _process_frame_with_engine(eng, bgr)
                    return out

                frames, keys = process_video_fast_global_bech(
                    in_path,
                    out_path,
                    fps=fps,
                    fw=w0,
                    fh=h0,
                    max_side=max_side,
                    sample_frames=sample_frames,
                    downscale_bgr_for_process=downscale_bgr_for_process,
                    bech_on_bgr=_bech,
                )
            except ValueError as e:
                return JSONResponse({'error': 'video_processing_failed', 'detail': str(e)}, status_code=400)
            except Exception as e:
                return JSONResponse({'error': 'video_processing_failed', 'detail': str(e)}, status_code=500)
            if frames == 0 or not os.path.isfile(out_path):
                return JSONResponse({'error': 'empty video stream'}, status_code=400)
            out_bytes = open(out_path, 'rb').read()
            return Response(
                content=out_bytes,
                media_type='video/mp4',
                headers={
                    'X-UVM-Engine': eng,
                    'X-UVM-Frames': str(frames),
                    'X-UVM-Backend': 'nikolaj_bech_underwater_color_correction',
                    'X-UVM-Video-Mode': 'fast',
                    'X-UVM-Fast-Keyframes': str(keys),
                },
            )

        cap = cv2.VideoCapture(in_path)
        if not cap.isOpened():
            return JSONResponse({'error': 'invalid video'}, status_code=400)
        fps = cap.get(cv2.CAP_PROP_FPS)
        if not fps or fps <= 0:
            fps = 25.0
        n0 = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
        if n0 > 0 and n0 / float(fps) > MAX_VIDEO_DURATION_SEC + 1e-3:
            cap.release()
            return JSONResponse(
                {
                    'error': 'video_too_long',
                    'detail': f'max {MAX_VIDEO_DURATION_SEC:g}s',
                    'reported_sec': round(n0 / float(fps), 2),
                },
                status_code=400,
            )
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
                try:
                    assert_video_within_max_duration(frame_index=frames, fps=float(fps))
                except ValueError as e:
                    return JSONResponse({'error': 'video_too_long', 'detail': str(e)}, status_code=400)
                work, orig_wh = downscale_bgr_for_process(frame, max_side)
                out_small, rep = _process_frame_with_engine(eng, work)
                out_frame = upscale_bgr_to_original(out_small, orig_wh)
                writer.write(out_frame)
                frames += 1
                last_report = rep
        except Exception as e:
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
                'X-UVM-Video-Mode': 'full',
            },
        )
