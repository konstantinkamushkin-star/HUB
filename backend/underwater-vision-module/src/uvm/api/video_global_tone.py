"""Быстрый режим видео: матрица Bech по ключевым кадрам → та же матрица на весь ролик."""
from __future__ import annotations

import cv2
import numpy as np

from uvm.pipeline.nikolaj_bech_color_correction import (
    apply_bech_matrix_bgr,
    get_color_filter_matrix_rgba,
)

MAX_VIDEO_DURATION_SEC = 60.0
SAMPLE_FRAMES_MIN = 10
SAMPLE_FRAMES_MAX = 30
DEFAULT_SAMPLE_FRAMES = 20


def clamp_sample_frames(n: int) -> int:
    return max(SAMPLE_FRAMES_MIN, min(SAMPLE_FRAMES_MAX, int(n)))


def sample_frame_indices(total_frames: int, k: int) -> list[int]:
    if total_frames <= 0:
        return []
    k = max(1, min(int(k), total_frames))
    if k == 1:
        return [0]
    raw = np.linspace(0, total_frames - 1, num=k, dtype=np.float64)
    return sorted({int(round(x)) for x in raw})


def _bech_matrix_from_bgr_samples(samples: list[np.ndarray]) -> list[float]:
    flats: list[np.ndarray] = []
    for bgr in samples:
        if bgr is None or bgr.size == 0:
            continue
        rgb = np.asarray(bgr, dtype=np.uint8)[..., ::-1]
        h, w = rgb.shape[:2]
        rgba = np.concatenate([rgb, np.full((h, w, 1), 255, dtype=np.uint8)], axis=-1)
        flats.append(rgba.reshape(-1, 4))
    if not flats:
        raise ValueError('fast mode: no keyframes collected')
    flat = np.concatenate(flats, axis=0)
    n = int(flat.shape[0])
    flt, _hue = get_color_filter_matrix_rgba(flat, n, 1)
    return flt


def assert_video_within_max_duration(*, frame_index: int, fps: float) -> None:
    """0-based индекс последнего прочитанного кадра; длина (frame_index+1)/fps."""
    if fps <= 0:
        fps = 25.0
    if (frame_index + 1) / float(fps) > MAX_VIDEO_DURATION_SEC + 1e-3:
        raise ValueError(
            f'video exceeds max duration {MAX_VIDEO_DURATION_SEC:g}s '
            f'(got ~{(frame_index + 1) / fps:.2f}s at {fps:.3f} fps)',
        )


def probe_video_meta(in_path: str) -> tuple[int, float, int, int]:
    """
    Число кадров (если известно из контейнера), иначе 0;
    fps, ширина, высота. При frame_count==0 вызывающий должен посчитать кадры отдельным проходом.
    """
    cap = cv2.VideoCapture(in_path)
    if not cap.isOpened():
        raise ValueError('invalid video')
    try:
        fps = float(cap.get(cv2.CAP_PROP_FPS)) or 0.0
        if fps <= 0:
            fps = 25.0
        fw = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
        fh = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
        n = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
        if n < 0:
            n = 0
        return n, fps, fw, fh
    finally:
        cap.release()


def count_frames_with_duration_guard(in_path: str, fps: float) -> int:
    """Подсчёт кадров с ранним выходом при длине > MAX_VIDEO_DURATION_SEC."""
    cap = cv2.VideoCapture(in_path)
    if not cap.isOpened():
        raise ValueError('invalid video')
    try:
        c = 0
        while True:
            ok, _ = cap.read()
            if not ok:
                break
            assert_video_within_max_duration(frame_index=c, fps=fps)
            c += 1
        return c
    finally:
        cap.release()


def process_video_fast_global_bech(
    in_path: str,
    out_path: str,
    *,
    fps: float,
    fw: int,
    fh: int,
    max_side: int,
    sample_frames: int,
    downscale_bgr_for_process,
    bech_on_bgr,
) -> tuple[int, int]:
    """
    Два прохода: (1) ключевые кадры → одна матрица Bech;
    (2) весь ролик: та же матрица + складка пурпура, как у фото.
    Возвращает (число записанных кадров, число ключевых кадров).
    """
    k_req = clamp_sample_frames(sample_frames)
    _ = bech_on_bgr
    fps = float(fps) if fps and fps > 0 else 25.0
    reported, fps2, fw2, fh2 = probe_video_meta(in_path)
    fps = fps2 if (fps2 and fps2 > 0) else fps
    if fw <= 0:
        fw = fw2
    if fh <= 0:
        fh = fh2
    if fw <= 0 or fh <= 0:
        raise ValueError('invalid video geometry')

    if reported > 0:
        if reported / fps > MAX_VIDEO_DURATION_SEC + 1e-3:
            raise ValueError(
                f'video exceeds max duration {MAX_VIDEO_DURATION_SEC:g}s '
                f'(reported ~{reported / fps:.2f}s)',
            )
        total = reported
    else:
        total = count_frames_with_duration_guard(in_path, fps)
        if total <= 0:
            raise ValueError('empty video stream')

    idx_list = sample_frame_indices(total, k_req)
    targets = set(idx_list)

    samples_in: list[np.ndarray] = []
    cap = cv2.VideoCapture(in_path)
    if not cap.isOpened():
        raise ValueError('cannot open video for fast mode pass 1')
    try:
        frame_i = 0
        while True:
            ok, frame = cap.read()
            if not ok:
                break
            assert_video_within_max_duration(frame_index=frame_i, fps=fps)
            if frame_i in targets:
                work, _ = downscale_bgr_for_process(frame, max_side)
                samples_in.append(work)
            frame_i += 1
    finally:
        cap.release()

    actual_total = frame_i
    if actual_total <= 0:
        raise ValueError('empty video stream')
    targets_ok = bool(targets) and max(targets) < actual_total and len(samples_in) >= min(
        k_req,
        actual_total,
        len(targets),
    )
    need_resample = (actual_total != total) or (not targets_ok)

    if need_resample:
        total = actual_total
        idx_list = sample_frame_indices(total, k_req)
        targets = set(idx_list)
        samples_in.clear()
        cap = cv2.VideoCapture(in_path)
        if not cap.isOpened():
            raise ValueError('cannot reopen video for fast mode keyframes')
        try:
            frame_i = 0
            while True:
                ok, frame = cap.read()
                if not ok:
                    break
                assert_video_within_max_duration(frame_index=frame_i, fps=fps)
                if frame_i in targets:
                    work, _ = downscale_bgr_for_process(frame, max_side)
                    samples_in.append(work)
                frame_i += 1
        finally:
            cap.release()
        if frame_i <= 0 or not samples_in:
            raise ValueError('fast mode: no keyframes collected after metadata correction')

    flt = _bech_matrix_from_bgr_samples(samples_in)

    cap = cv2.VideoCapture(in_path)
    if not cap.isOpened():
        raise ValueError('cannot open video for fast mode pass 2')
    writer = cv2.VideoWriter(out_path, cv2.VideoWriter_fourcc(*'mp4v'), float(fps), (fw, fh))
    if not writer.isOpened():
        cap.release()
        raise ValueError('video writer init failed')
    written = 0
    try:
        while True:
            ok, frame = cap.read()
            if not ok:
                break
            out_frame = apply_bech_matrix_bgr(frame, flt)
            writer.write(out_frame)
            written += 1
    finally:
        cap.release()
        writer.release()

    if written <= 0:
        raise ValueError('fast mode: no frames written')
    return written, len(samples_in)
