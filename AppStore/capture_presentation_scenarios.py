#!/usr/bin/env python3
"""
Single-run deterministic scenario capture for iOS/Android.

Exports exactly these files into admin-web/public/presentation/:
  - scenario-search.png|webp
  - scenario-booking.png|webp
  - scenario-logbook.png|webp
  - scenario-support.png|webp
"""
from __future__ import annotations

import os
import subprocess
import sys
import time
from pathlib import Path
from typing import Dict, List
from urllib.parse import quote_plus

from PIL import Image


REPO = Path(__file__).resolve().parents[1]
OUT_DIR = REPO / "admin-web" / "public" / "presentation"
TMP_DIR = REPO / "AppStore" / ".tmp-presentation-captures"

SCENARIOS = ("search", "booking", "logbook", "support")
TARGET_SIZE = (1170, 2532)


def env(name: str, default: str) -> str:
    return os.environ.get(name, default).strip()


def run(cmd: List[str], *, with_env: Dict[str, str] | None = None, check: bool = True) -> subprocess.CompletedProcess:
    merged_env = os.environ.copy()
    if with_env:
        merged_env.update(with_env)
    return subprocess.run(cmd, check=check, env=merged_env, text=True, capture_output=True)


def ensure_tools(platform: str) -> None:
    tools = ["python3"]
    if platform == "ios":
        tools.extend(["xcrun", "xcodebuild", "sips"])
    else:
        tools.extend(["adb", "sips"])
    for tool in tools:
        if subprocess.run(["which", tool], capture_output=True).returncode != 0:
            raise RuntimeError(f"Missing required tool: {tool}")


def ensure_pillow() -> None:
    try:
        import PIL  # noqa: F401
    except Exception as exc:  # pragma: no cover
        raise RuntimeError("Pillow is required: pip install Pillow") from exc


def crop_resize_fill(src: Path, dst: Path, output_format: str) -> None:
    with Image.open(src) as image:
        img = image.convert("RGB")
        src_w, src_h = img.size
        tgt_w, tgt_h = TARGET_SIZE
        src_ratio = src_w / src_h
        tgt_ratio = tgt_w / tgt_h

        if src_ratio > tgt_ratio:
            new_h = src_h
            new_w = int(new_h * tgt_ratio)
        else:
            new_w = src_w
            new_h = int(new_w / tgt_ratio)

        left = max((src_w - new_w) // 2, 0)
        top = max((src_h - new_h) // 2, 0)
        img = img.crop((left, top, left + new_w, top + new_h))
        img = img.resize((tgt_w, tgt_h), Image.Resampling.LANCZOS)

        if output_format == "png":
            img.save(dst, format="PNG", optimize=True)
        else:
            img.save(dst, format="WEBP", quality=95, method=6)


def build_ios_app() -> Path:
    derived = env("IOS_DERIVED_DATA", "/tmp/DiveHubDerivedDataPresentation")
    device = env("IOS_DEVICE", "iPhone 17 Pro Max")
    project = REPO / "DiveHub.xcodeproj"
    scheme = env("IOS_SCHEME", "DiveHub")
    run(
        [
            "xcodebuild",
            "-project",
            str(project),
            "-scheme",
            scheme,
            "-destination",
            f"platform=iOS Simulator,name={device}",
            "-derivedDataPath",
            derived,
            "build",
            "-quiet",
        ]
    )
    app = Path(derived) / "Build" / "Products" / "Debug-iphonesimulator" / "DiveHub.app"
    if not app.is_dir():
        raise RuntimeError(f"iOS app not found: {app}")
    return app


def ios_capture(output_format: str) -> None:
    bundle_id = env("IOS_BUNDLE_ID", "Dive-Hub.ru")
    device = env("IOS_DEVICE", "iPhone 17 Pro Max")
    app = build_ios_app()

    subprocess.run(["xcrun", "simctl", "boot", device], check=False, capture_output=True)
    subprocess.run(["open", "-a", "Simulator"], check=False)
    run(["xcrun", "simctl", "install", "booted", str(app)])

    booking_tap = (int(env("IOS_BOOKING_TAP_X", "585")), int(env("IOS_BOOKING_TAP_Y", "2180")))
    support_tap = (int(env("IOS_SUPPORT_TAP_X", "585")), int(env("IOS_SUPPORT_TAP_Y", "2170")))

    def launch(child_env: Dict[str, str]) -> None:
        subprocess.run(["xcrun", "simctl", "terminate", "booted", bundle_id], check=False, capture_output=True)
        launch_env = {f"SIMCTL_CHILD_{k}": v for k, v in child_env.items()}
        run(["xcrun", "simctl", "launch", "booted", bundle_id], with_env=launch_env)

    def snap_raw(name: str) -> Path:
        raw = TMP_DIR / f"ios-{name}.png"
        run(["xcrun", "simctl", "io", "booted", "screenshot", str(raw)])
        return raw

    def tap(x: int, y: int) -> None:
        # Some Xcode versions do not support simulator taps via simctl.
        # In that case we keep deterministic launch state and continue.
        try:
            run(["xcrun", "simctl", "ui", "booted", "tap", str(x), str(y)])
        except subprocess.CalledProcessError:
            print(f"  ! iOS tap unsupported by local simctl, skip tap at {x},{y}")

    # search
    launch({"DH_APPSTORE_SCREENSHOTS": "1", "DH_APPSTORE_INITIAL_TAB": "0"})
    time.sleep(float(env("IOS_WAIT_SEARCH_SEC", "3.0")))
    write_asset("search", snap_raw("search"), output_format)

    # booking (deterministic tap from Explore prepared state)
    launch({"DH_APPSTORE_SCREENSHOTS": "1", "DH_APPSTORE_INITIAL_TAB": "0"})
    time.sleep(float(env("IOS_WAIT_BOOKING_PREP_SEC", "3.0")))
    tap(booking_tap[0], booking_tap[1])
    time.sleep(float(env("IOS_WAIT_BOOKING_AFTER_TAP_SEC", "2.0")))
    write_asset("booking", snap_raw("booking"), output_format)

    # logbook
    launch({"DH_APPSTORE_SCREENSHOTS": "1", "DH_APPSTORE_INITIAL_TAB": "2"})
    time.sleep(float(env("IOS_WAIT_LOGBOOK_SEC", "2.5")))
    write_asset("logbook", snap_raw("logbook"), output_format)

    # support (profile tab -> support row tap)
    launch({"DH_APPSTORE_SCREENSHOTS": "1", "DH_APPSTORE_INITIAL_TAB": "6"})
    time.sleep(float(env("IOS_WAIT_SUPPORT_PREP_SEC", "3.0")))
    tap(support_tap[0], support_tap[1])
    time.sleep(float(env("IOS_WAIT_SUPPORT_AFTER_TAP_SEC", "2.0")))
    write_asset("support", snap_raw("support"), output_format)


def android_capture(output_format: str) -> None:
    package_name = env("ANDROID_PACKAGE", "com.divehub.app")
    activity = env("ANDROID_ACTIVITY", "com.divehub.app.MainActivity")
    serial = env("ANDROID_SERIAL", "")
    app_id = f"{package_name}/{activity}"
    adb_base = ["adb"] + (["-s", serial] if serial else [])

    booking_tap = (int(env("ANDROID_BOOKING_TAP_X", "540")), int(env("ANDROID_BOOKING_TAP_Y", "2080")))
    support_tap = (int(env("ANDROID_SUPPORT_TAP_X", "540")), int(env("ANDROID_SUPPORT_TAP_Y", "2130")))

    def adb(args: List[str], *, check: bool = True) -> subprocess.CompletedProcess:
        return run(adb_base + args, check=check)

    def launch_deeplink(url: str) -> None:
        adb(["shell", "am", "force-stop", package_name], check=False)
        adb(["shell", "am", "start", "-W", "-a", "android.intent.action.VIEW", "-d", url, app_id])

    def tap(x: int, y: int) -> None:
        adb(["shell", "input", "tap", str(x), str(y)])

    def snap_raw(name: str) -> Path:
        remote = f"/sdcard/{package_name}-{name}.png"
        local = TMP_DIR / f"android-{name}.png"
        adb(["shell", "screencap", "-p", remote])
        adb(["pull", remote, str(local)])
        adb(["shell", "rm", "-f", remote], check=False)
        return local

    # search
    search_query = quote_plus(env("SCENARIO_SEARCH_QUERY", "reef"))
    launch_deeplink(f"divehub://search?q={search_query}")
    time.sleep(float(env("ANDROID_WAIT_SEARCH_SEC", "2.5")))
    write_asset("search", snap_raw("search"), output_format)

    # booking (center deep link + deterministic tap)
    center_id = env("TEST_CENTER_ID", "")
    if center_id:
        launch_deeplink(f"divehub://center/{center_id}")
    else:
        launch_deeplink("divehub://explore")
    time.sleep(float(env("ANDROID_WAIT_BOOKING_PREP_SEC", "3.0")))
    tap(*booking_tap)
    time.sleep(float(env("ANDROID_WAIT_BOOKING_AFTER_TAP_SEC", "2.0")))
    write_asset("booking", snap_raw("booking"), output_format)

    # logbook
    launch_deeplink("divehub://logbook")
    time.sleep(float(env("ANDROID_WAIT_LOGBOOK_SEC", "2.0")))
    write_asset("logbook", snap_raw("logbook"), output_format)

    # support (profile deep link + support row tap)
    launch_deeplink("divehub://profile")
    time.sleep(float(env("ANDROID_WAIT_SUPPORT_PREP_SEC", "2.5")))
    tap(*support_tap)
    time.sleep(float(env("ANDROID_WAIT_SUPPORT_AFTER_TAP_SEC", "2.0")))
    write_asset("support", snap_raw("support"), output_format)


def write_asset(scenario: str, raw_file: Path, output_format: str) -> None:
    ext = "png" if output_format == "png" else "webp"
    dst = OUT_DIR / f"scenario-{scenario}.{ext}"
    crop_resize_fill(raw_file, dst, output_format)
    print(f"  - {dst.relative_to(REPO)}")


def main() -> int:
    ensure_pillow()
    platform = env("PLATFORM", "ios").lower()
    output_format = env("OUTPUT_FORMAT", "png").lower()

    if platform not in ("ios", "android"):
        print("PLATFORM must be ios or android", file=sys.stderr)
        return 2
    if output_format not in ("png", "webp"):
        print("OUTPUT_FORMAT must be png or webp", file=sys.stderr)
        return 2

    ensure_tools(platform)
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    TMP_DIR.mkdir(parents=True, exist_ok=True)

    print(f"Running {platform} scenario capture -> {output_format}")
    print(f"Scenarios: {', '.join(SCENARIOS)}")
    try:
        if platform == "ios":
            ios_capture(output_format)
        else:
            android_capture(output_format)
    except subprocess.CalledProcessError as exc:
        print(exc.stdout or "", file=sys.stderr)
        print(exc.stderr or "", file=sys.stderr)
        return exc.returncode or 1
    except Exception as exc:
        print(str(exc), file=sys.stderr)
        return 1

    print("Done.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
