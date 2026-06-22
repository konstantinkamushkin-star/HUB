#!/usr/bin/env python3
"""Copy iPhone marketing PNGs into DiveHubAndroid Play Store folder."""
from __future__ import annotations

import shutil
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
SRC = REPO / "AppStore" / "play-upload-android-phone"
DST = REPO / "DiveHubAndroid" / "PlayStore" / "phone-screenshots-ru"
ICON_SRC = REPO / "AppStore" / "AppIcon-1024-for-App-Store-Connect.png"
ICON_DST = REPO / "DiveHubAndroid" / "PlayStore" / "icon-1024.png"


def main() -> None:
    if not SRC.is_dir():
        alt = REPO / "AppStore" / "iphone-6_5-marketing-ru"
        if alt.is_dir():
            print(f"Syncing from {alt} via sync_play_upload_from_iphone.py …")
            import subprocess
            subprocess.check_call(["python3", str(REPO / "AppStore" / "sync_play_upload_from_iphone.py")])
        else:
            raise SystemExit(f"Missing {SRC} — run AppStore/generate_appstore_screenshots.py first")
    DST.mkdir(parents=True, exist_ok=True)
    count = 0
    for png in sorted(SRC.glob("*.png")):
        shutil.copy2(png, DST / png.name)
        count += 1
    if ICON_SRC.is_file():
        shutil.copy2(ICON_SRC, ICON_DST)
        print(f"Icon → {ICON_DST}")
    print(f"Copied {count} screenshots → {DST}")


if __name__ == "__main__":
    main()
