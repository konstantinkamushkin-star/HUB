#!/usr/bin/env python3
"""Sanity-check H.264+AAC finalize used for iOS Photos / share sheet."""
from __future__ import annotations

import os
import shutil
import subprocess
import sys
import tempfile

import cv2
import numpy as np

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'src'))
from uvm.api.video_global_tone import (  # noqa: E402
    prepare_opencv_input,
    probe_video_codec,
    remux_original_audio,
    source_has_audio,
)


def _run(args: list[str]) -> None:
    proc = subprocess.run(args, capture_output=True, text=True, timeout=60, check=False)
    if proc.returncode != 0:
        raise RuntimeError(f'{args[0]} failed: {(proc.stderr or "")[-400:]}')


def main() -> int:
    if shutil.which('ffmpeg') is None or shutil.which('ffprobe') is None:
        print('SKIP: ffmpeg/ffprobe not on PATH')
        return 0

    with tempfile.TemporaryDirectory(prefix='uvm_ios_test_') as td:
        src = os.path.join(td, 'src.mp4')
        processed = os.path.join(td, 'processed.mp4')

        _run(
            [
                'ffmpeg',
                '-y',
                '-f',
                'lavfi',
                '-i',
                'color=c=blue:s=320x240:d=1:r=10',
                '-f',
                'lavfi',
                '-i',
                'sine=frequency=440:duration=1',
                '-c:v',
                'libx264',
                '-pix_fmt',
                'yuv420p',
                '-c:a',
                'aac',
                '-shortest',
                src,
            ]
        )

        writer = cv2.VideoWriter(processed, cv2.VideoWriter_fourcc(*'mp4v'), 10.0, (320, 240))
        if not writer.isOpened():
            raise RuntimeError('OpenCV mp4v writer failed')
        frame = np.zeros((240, 320, 3), dtype=np.uint8)
        frame[:, :] = (0, 180, 80)
        for _ in range(10):
            writer.write(frame)
        writer.release()

        before = probe_video_codec(processed)
        print(f'processed codec before finalize: {before!r}')
        if before not in ('mpeg4', 'mp4v', ''):
            print(f'WARN: expected mpeg4/mp4v, got {before!r}')

        ok = remux_original_audio(processed, src)
        after = probe_video_codec(processed)
        has_a = source_has_audio(processed)
        print(f'finalize audio={ok} codec={after!r} has_audio={has_a}')
        if after not in ('h264', 'avc1'):
            raise SystemExit(f'FAIL: expected h264, got {after!r}')
        if not ok or not has_a:
            raise SystemExit('FAIL: expected AAC audio on output')

        hevc = os.path.join(td, 'hevc.mp4')
        encoders = subprocess.run(
            ['ffmpeg', '-hide_banner', '-encoders'],
            capture_output=True,
            text=True,
            timeout=20,
            check=False,
        ).stdout
        if 'libx265' in (encoders or ''):
            _run(
                [
                    'ffmpeg',
                    '-y',
                    '-f',
                    'lavfi',
                    '-i',
                    'color=c=red:s=320x240:d=0.5:r=10',
                    '-c:v',
                    'libx265',
                    '-pix_fmt',
                    'yuv420p',
                    '-an',
                    '-tag:v',
                    'hvc1',
                    hevc,
                ]
            )
            print(f'hevc source codec: {probe_video_codec(hevc)!r}')
            decoded = prepare_opencv_input(hevc)
            print(f'prepare_opencv_input -> {probe_video_codec(decoded)!r} path={decoded}')
            if probe_video_codec(decoded) not in ('h264', 'avc1'):
                raise SystemExit('FAIL: HEVC was not transcoded to h264 for OpenCV')
        else:
            print('SKIP HEVC: libx265 not available')

    print('OK')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
