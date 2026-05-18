"""Быстрый режим видео: несколько ключевых кадров через Bech → общий LAB-трансфер на весь ролик."""
from __future__ import annotations

import cv2
import numpy as np

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


def _pooled_lab_mean_std(bgr_images: list[np.ndarray]) -> tuple[np.ndarray, np.ndarray]:
    sum_c = np.zeros(3, dtype=np.float64)
    sum_c2 = np.zeros(3, dtype=np.float64)
    n_pix = 0
    for bgr in bgr_images:
        if bgr is None or bgr.size == 0:
            continue
        lab = cv2.cvtColor(bgr, cv2.COLOR_BGR2LAB)
        flat = lab.reshape(-1, 3).astype(np.float64)
        sum_c += flat.sum(axis=0)
        sum_c2 += np.square(flat).sum(axis=0)
        n_pix += flat.shape[0]
    if n_pix == 0:
        z = np.zeros(3, dtype=np.float64)
        return z, np.ones(3, dtype=np.float64)
    mean = sum_c / n_pix
    var = sum_c2 / n_pix - mean * mean
    std = np.sqrt(np.maximum(var, 0.0))
    std = np.maximum(std, 1e-3)
    return mean, std


def apply_lab_transfer_bgr(
    bgr: np.ndarray,
    src_mean: np.ndarray,
    src_std: np.ndarray,
    dst_mean: np.ndarray,
    dst_std: np.ndarray,
) -> np.ndarray:
    lab = cv2.cvtColor(bgr, cv2.COLOR_BGR2LAB).astype(np.float32)
    sm = src_mean.astype(np.float32)
    ss = src_std.astype(np.float32)
    dm = dst_mean.astype(np.float32)
    ds = dst_std.astype(np.float32)
    for c in range(3):
        ch = lab[:, :, c]
        lab[:, :, c] = (ch - sm[c]) / ss[c] * ds[c] + dm[c]
    lab = np.clip(lab, 0, 255).astype(np.uint8)
    return cv2.cvtColor(lab, cv2.COLOR_LAB2BGR)


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
    Два прохода: (1) ключевые кадры work-resolution → Bech → пуловые LAB-статистики;
    (2) весь ролик: LAB-трансфер на полном разрешении.
    Возвращает (число записанных кадров, число ключевых кадров).
    """
    k_req = clamp_sample_frames(sample_frames)
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
    samples_out: list[np.ndarray] = []
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
                out_w = bech_on_bgr(work)
                if out_w.shape[:2] != work.shape[:2]:
                    out_w = cv2.resize(out_w, (work.shape[1], work.shape[0]), interpolation=cv2.INTER_LINEAR)
                samples_in.append(work)
                samples_out.append(out_w)
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
        samples_out.clear()
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
                    out_w = bech_on_bgr(work)
                    if out_w.shape[:2] != work.shape[:2]:
                        out_w = cv2.resize(out_w, (work.shape[1], work.shape[0]), interpolation=cv2.INTER_LINEAR)
                    samples_in.append(work)
                    samples_out.append(out_w)
                frame_i += 1
        finally:
            cap.release()
        if frame_i <= 0 or not samples_in:
            raise ValueError('fast mode: no keyframes collected after metadata correction')

    src_mean, src_std = _pooled_lab_mean_std(samples_in)
    dst_mean, dst_std = _pooled_lab_mean_std(samples_out)

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
            out_frame = apply_lab_transfer_bgr(frame, src_mean, src_std, dst_mean, dst_std)
            writer.write(out_frame)
            written += 1
    finally:
        cap.release()
        writer.release()

    if written <= 0:
        raise ValueError('fast mode: no frames written')
    return written, len(samples_in)
