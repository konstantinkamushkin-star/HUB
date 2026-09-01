"""Быстрый режим видео: Bech-матрица по ключевым кадрам → та же матрица на весь ролик."""
from __future__ import annotations

import os
import shutil
import subprocess
import tempfile

import cv2
import numpy as np

from uvm.pipeline.nikolaj_bech_color_correction import (
    apply_bech_matrix_bgr,
    get_color_filter_matrix_rgba,
)

MAX_VIDEO_DURATION_SEC = 60.0
SAMPLE_FRAMES_MIN = 10
SAMPLE_FRAMES_MAX = 30
DEFAULT_SAMPLE_FRAMES = 12


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


def output_size_for_max_side(fw: int, fh: int, max_side: int) -> tuple[int, int]:
    """Even width/height for encoders; long edge ≤ max_side."""
    fw = max(2, int(fw))
    fh = max(2, int(fh))
    m = max(fw, fh)
    if max_side > 0 and m > max_side:
        scale = max_side / float(m)
        fw = int(round(fw * scale))
        fh = int(round(fh * scale))
    fw -= fw % 2
    fh -= fh % 2
    return max(2, fw), max(2, fh)


def _pooled_lab_mean_std(bgr_images: list[np.ndarray]) -> tuple[np.ndarray, np.ndarray]:
    """Legacy helper (tests / offline LAB path). Prefer Bech matrix for video."""
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
    """Legacy Reinhard LAB (can explode chroma on open water). Kept for compatibility."""
    lab = cv2.cvtColor(bgr, cv2.COLOR_BGR2LAB).astype(np.float32)
    scale = (dst_std.astype(np.float32) / src_std.astype(np.float32)).reshape(1, 1, 3)
    shift = dst_mean.astype(np.float32).reshape(1, 1, 3)
    sm = src_mean.astype(np.float32).reshape(1, 1, 3)
    lab = (lab - sm) * scale + shift
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
    finally:
        cap.release()
    if n < 0:
        n = 0
    return n, fps, fw, fh


def count_frames_with_duration_guard(in_path: str, fps: float) -> int:
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


def _bech_matrix_from_bgr(bgr: np.ndarray) -> list[float]:
    h, w = bgr.shape[:2]
    rgb = bgr[..., ::-1]
    rgba = np.concatenate([rgb, np.full((h, w, 1), 255, dtype=np.uint8)], axis=-1)
    flat = rgba.reshape(-1).astype(np.uint8, copy=False)
    flt, _hue = get_color_filter_matrix_rgba(flat, w, h)
    return list(flt)


def _collect_bech_matrices(
    in_path: str,
    *,
    fps: float,
    targets: set[int],
    max_side: int,
    downscale_bgr_for_process,
    total_hint: int,
) -> tuple[list[tuple[int, list[float]]], int]:
    """Returns ([(frame_index, matrix), ...], scanned_or_hinted_total)."""
    pairs: list[tuple[int, list[float]]] = []
    ordered = sorted(t for t in targets if t >= 0)
    if not ordered:
        return pairs, 0

    cap = cv2.VideoCapture(in_path)
    if not cap.isOpened():
        raise ValueError('cannot open video for fast mode keyframes')
    try:
        use_seek = total_hint >= max(ordered) + 1 and total_hint <= 200_000
        if use_seek:
            last_ok = -1
            for idx in ordered:
                assert_video_within_max_duration(frame_index=idx, fps=fps)
                cap.set(cv2.CAP_PROP_POS_FRAMES, float(idx))
                ok, frame = cap.read()
                if not ok or frame is None:
                    use_seek = False
                    pairs.clear()
                    break
                work, _ = downscale_bgr_for_process(frame, max_side)
                pairs.append((idx, _bech_matrix_from_bgr(work)))
                last_ok = idx
            if use_seek and pairs:
                return pairs, max(total_hint, last_ok + 1)

        pairs.clear()
        need = set(ordered)
        frame_i = 0
        while need:
            ok, frame = cap.read()
            if not ok:
                break
            assert_video_within_max_duration(frame_index=frame_i, fps=fps)
            if frame_i in need:
                work, _ = downscale_bgr_for_process(frame, max_side)
                pairs.append((frame_i, _bech_matrix_from_bgr(work)))
                need.discard(frame_i)
            frame_i += 1
        scanned = frame_i
        if need and total_hint > 0:
            while True:
                ok, _ = cap.read()
                if not ok:
                    break
                assert_video_within_max_duration(frame_index=scanned, fps=fps)
                scanned += 1
        return pairs, max(scanned, total_hint)
    finally:
        cap.release()


def _interp_matrix(
    frame_i: int,
    key_idx: list[int],
    key_mat: np.ndarray,
) -> list[float]:
    """Linear blend of neighboring keyframe Bech matrices."""
    if len(key_idx) == 1:
        return list(key_mat[0])
    if frame_i <= key_idx[0]:
        return list(key_mat[0])
    if frame_i >= key_idx[-1]:
        return list(key_mat[-1])
    # key_idx is sorted
    j = int(np.searchsorted(key_idx, frame_i, side='right') - 1)
    j = max(0, min(j, len(key_idx) - 2))
    i0, i1 = key_idx[j], key_idx[j + 1]
    if i1 <= i0:
        return list(key_mat[j])
    t = (frame_i - i0) / float(i1 - i0)
    blended = (1.0 - t) * key_mat[j] + t * key_mat[j + 1]
    return list(blended)


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
    strength: float = 1.0,
) -> tuple[int, int]:
    """
    Keyframes → Bech matrix each → temporal lerp of matrices on every frame (≤ max_side).

    One global matrix (or Reinhard LAB) blows up on open-water / bright-sand clips;
    per-frame Bech looks right but is slow — interpolated matrices track lighting.
    ``bech_on_bgr`` kept for API compatibility with callers.
    """
    del bech_on_bgr
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

    out_w, out_h = output_size_for_max_side(fw, fh, max_side)

    idx_list = sample_frame_indices(total, k_req)
    targets = set(idx_list)
    pairs, frame_i = _collect_bech_matrices(
        in_path,
        fps=fps,
        targets=targets,
        max_side=max_side,
        downscale_bgr_for_process=downscale_bgr_for_process,
        total_hint=total,
    )
    actual_total = frame_i if frame_i > 0 else total
    if (actual_total != total) or (not pairs):
        total = actual_total
        idx_list = sample_frame_indices(total, k_req)
        targets = set(idx_list)
        pairs, frame_i = _collect_bech_matrices(
            in_path,
            fps=fps,
            targets=targets,
            max_side=max_side,
            downscale_bgr_for_process=downscale_bgr_for_process,
            total_hint=total,
        )
        if not pairs:
            raise ValueError('fast mode: no Bech matrices after metadata correction')

    pairs.sort(key=lambda p: p[0])
    key_idx = [p[0] for p in pairs]
    key_mat = np.asarray([p[1] for p in pairs], dtype=np.float64)
    s = float(min(1.0, max(0.0, strength)))

    cap = cv2.VideoCapture(in_path)
    if not cap.isOpened():
        raise ValueError('cannot open video for fast mode pass 2')
    writer = cv2.VideoWriter(out_path, cv2.VideoWriter_fourcc(*'mp4v'), float(fps), (out_w, out_h))
    if not writer.isOpened():
        cap.release()
        raise ValueError('video writer init failed')
    written = 0
    try:
        while True:
            ok, frame = cap.read()
            if not ok:
                break
            if frame.shape[1] != out_w or frame.shape[0] != out_h:
                frame = cv2.resize(frame, (out_w, out_h), interpolation=cv2.INTER_AREA)
            flt = _interp_matrix(written, key_idx, key_mat)
            out_frame = apply_bech_matrix_bgr(frame, flt, s)
            writer.write(out_frame)
            written += 1
    finally:
        cap.release()
        writer.release()

    if written <= 0:
        raise ValueError('fast mode: no frames written')
    return written, len(pairs)


_HEVC_CODECS = frozenset({'hevc', 'h265', 'hev1', 'hvc1'})
_H264_CODECS = frozenset({'h264', 'avc1', 'avc3'})

# iOS Photos / share sheet: H.264 yuv420p + AAC, moov at start. MPEG-4 Part 2
# (OpenCV `mp4v`) is rejected ("could not process") and often shared as a silent GIF.
_H264_ENCODE_ARGS = [
    '-c:v',
    'libx264',
    '-preset',
    'veryfast',
    '-crf',
    '20',
    '-pix_fmt',
    'yuv420p',
    '-profile:v',
    'high',
    '-level',
    '4.1',
    '-tag:v',
    'avc1',
    '-movflags',
    '+faststart',
]


def _ffprobe_csv(path: str, *, streams: str, entries: str) -> str:
    try:
        proc = subprocess.run(
            [
                'ffprobe',
                '-v',
                'error',
                '-select_streams',
                streams,
                '-show_entries',
                entries,
                '-of',
                'csv=p=0',
                path,
            ],
            capture_output=True,
            text=True,
            timeout=30,
            check=False,
        )
    except (FileNotFoundError, subprocess.TimeoutExpired, OSError):
        return ''
    if proc.returncode != 0:
        return ''
    return (proc.stdout or '').strip()


def source_has_audio(path: str) -> bool:
    """True if ffprobe finds at least one audio stream."""
    return bool(_ffprobe_csv(path, streams='a:0', entries='stream=codec_type'))


def probe_video_codec(path: str) -> str:
    """Lowercase codec name of the first video stream, or empty."""
    raw = _ffprobe_csv(path, streams='v:0', entries='stream=codec_name')
    if not raw:
        return ''
    return raw.splitlines()[0].strip().lower()


def prepare_opencv_input(in_path: str) -> str:
    """
    iPhone HEVC (especially 10-bit / rotated) is unreliable in OpenCV VideoCapture.
    Transcode those sources to H.264 yuv420p with rotation baked in. Returns the
    path OpenCV should read; audio is still taken from the original upload.
    """
    if not os.path.isfile(in_path):
        return in_path
    codec = probe_video_codec(in_path)
    if codec in _H264_CODECS:
        return in_path
    if codec not in _HEVC_CODECS and codec:
        # mpeg4 / vp9 / etc. — OpenCV usually reads these; leave as-is.
        if codec not in ('mpeg4', 'mjpeg'):
            print(f'[uvm] prepare_opencv_input: codec={codec!r} (no pre-transcode)', flush=True)
        return in_path
    if shutil.which('ffmpeg') is None:
        print('[uvm] prepare_opencv_input: ffmpeg not on PATH', flush=True)
        return in_path

    sibling = in_path + '.opencv.mp4'
    args = [
        'ffmpeg',
        '-y',
        '-i',
        in_path,
        '-an',
        '-c:v',
        'libx264',
        '-preset',
        'ultrafast',
        '-crf',
        '18',
        '-pix_fmt',
        'yuv420p',
        '-tag:v',
        'avc1',
        sibling,
    ]
    try:
        proc = subprocess.run(
            args,
            capture_output=True,
            text=True,
            timeout=180,
            check=False,
        )
        if proc.returncode == 0 and os.path.isfile(sibling) and os.path.getsize(sibling) > 0:
            print(
                f'[uvm] prepare_opencv_input: {codec or "unknown"} -> h264 for OpenCV',
                flush=True,
            )
            return sibling
        err_tail = (proc.stderr or '')[-400:].strip()
        print(
            f'[uvm] prepare_opencv_input failed rc={proc.returncode} {err_tail}',
            flush=True,
        )
    except (subprocess.TimeoutExpired, OSError) as e:
        print(f'[uvm] prepare_opencv_input error: {e}', flush=True)
    if os.path.isfile(sibling):
        try:
            os.remove(sibling)
        except OSError:
            pass
    return in_path


def remux_original_audio(video_only_path: str, source_path: str) -> bool:
    """
    OpenCV VideoWriter emits MPEG-4 Part 2 (`mp4v`). iOS Photos rejects that
    container/codec and the share sheet often turns it into a silent GIF.

    Re-encode processed video to H.264 (yuv420p, +faststart) and attach AAC
    from the original clip in-place. Returns True if audio was attached.
    """
    if not os.path.isfile(video_only_path):
        return False
    if shutil.which('ffmpeg') is None:
        print('[uvm] remux_original_audio: ffmpeg not on PATH', flush=True)
        return False

    has_audio = os.path.isfile(source_path) and source_has_audio(source_path)

    # Always re-encode audio to AAC. Stream-copy can leave PCM / ADTS from
    # iPhone HEVC MOV files, which Photos and the share sheet reject.
    attempts: list[list[str]] = []
    if has_audio:
        attempts.append(
            [
                'ffmpeg',
                '-y',
                '-i',
                video_only_path,
                '-i',
                source_path,
                '-map',
                '0:v:0',
                '-map',
                '1:a:0',
                *_H264_ENCODE_ARGS,
                '-c:a',
                'aac',
                '-b:a',
                '192k',
                '-ar',
                '48000',
                '-ac',
                '2',
                '-shortest',
            ]
        )
    # Video-only H.264 is always last: iOS Photos rejects OpenCV `mp4v` even
    # when the source had no audio (or audio mux failed).
    attempts.append(
        [
            'ffmpeg',
            '-y',
            '-i',
            video_only_path,
            '-map',
            '0:v:0',
            *_H264_ENCODE_ARGS,
            '-an',
        ]
    )

    for args in attempts:
        fd, tmp_path = tempfile.mkstemp(suffix='.mp4', prefix='uvm_ios_')
        os.close(fd)
        try:
            proc = subprocess.run(
                args + [tmp_path],
                capture_output=True,
                text=True,
                timeout=300,
                check=False,
            )
            if proc.returncode == 0 and os.path.isfile(tmp_path) and os.path.getsize(tmp_path) > 0:
                codec = probe_video_codec(tmp_path)
                if codec not in _H264_CODECS:
                    print(
                        f'[uvm] remux_original_audio: unexpected codec {codec!r}',
                        flush=True,
                    )
                    continue
                want_audio = '-map' in args and '1:a:0' in args
                if want_audio and not source_has_audio(tmp_path):
                    continue
                os.replace(tmp_path, video_only_path)
                audio_ok = source_has_audio(video_only_path)
                print(
                    f'[uvm] remux_original_audio: h264 audio={int(audio_ok)}',
                    flush=True,
                )
                return audio_ok
            err_tail = (proc.stderr or '')[-400:].strip()
            print(
                f'[uvm] remux_original_audio attempt failed rc={proc.returncode} {err_tail}',
                flush=True,
            )
        except (subprocess.TimeoutExpired, OSError) as e:
            print(f'[uvm] remux_original_audio error: {e}', flush=True)
        finally:
            if os.path.isfile(tmp_path):
                try:
                    os.remove(tmp_path)
                except OSError:
                    pass
    return False
