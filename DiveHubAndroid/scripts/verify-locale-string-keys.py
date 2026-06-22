#!/usr/bin/env python3
"""
Compare string resource keys across Android values*/strings.xml.
Ensures every name= in values/strings.xml exists in values-*/strings.xml.

Usage (from repo root):
  python3 DiveHubAndroid/scripts/verify-locale-string-keys.py

Exit code 0 if OK, 1 if any locale is missing keys.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

APP_RES = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "res"
NAME_RE = re.compile(r'<string\s+name="([^"]+)"')

def keys_in_file(p: Path) -> set[str]:
    if not p.exists():
        return set()
    return set(NAME_RE.findall(p.read_text(encoding="utf-8")))


def main() -> int:
    base = APP_RES / "values" / "strings.xml"
    if not base.exists():
        print(f"Missing base file: {base}", file=sys.stderr)
        return 1
    base_keys = keys_in_file(base)
    if not base_keys:
        print(f"No keys found in {base}", file=sys.stderr)
        return 1

    failed = False
    for values_dir in sorted(APP_RES.glob("values-*")):
        if not values_dir.is_dir():
            continue
        xml = values_dir / "strings.xml"
        other = keys_in_file(xml)
        missing = base_keys - other
        extra = other - base_keys
        if missing or extra:
            failed = True
            tag = values_dir.name
            print(f"\n=== {tag} ===")
            if missing:
                print(f"  Missing {len(missing)} key(s) vs values/strings.xml:")
                for k in sorted(missing)[:80]:
                    print(f"    - {k}")
                if len(missing) > 80:
                    print(f"    ... and {len(missing) - 80} more")
            if extra:
                print(f"  Extra {len(extra)} key(s) not in base (informational):")
                for k in sorted(extra)[:40]:
                    print(f"    + {k}")
                if len(extra) > 40:
                    print(f"    ... and {len(extra) - 40} more")

    if failed:
        print("\nLocale parity check FAILED.", file=sys.stderr)
        return 1
    print(f"OK: all {len(list(APP_RES.glob('values-*')))} values-*/strings.xml cover {len(base_keys)} keys from values/strings.xml.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
