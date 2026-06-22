#!/usr/bin/env python3
"""
Merge missing <string> entries from values/strings.xml into each values-*/strings.xml
(copies English text from base). Idempotent for already-complete locales.

Usage:
  python3 DiveHubAndroid/scripts/sync_missing_locale_strings.py
"""
from __future__ import annotations

import xml.etree.ElementTree as ET
from pathlib import Path

APP_RES = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "res"


def load_strings_xml(path: Path) -> ET.Element:
    tree = ET.parse(path)
    return tree.getroot()


def string_map(root: ET.Element) -> dict[str, tuple[str | None, str | None]]:
    """name -> (text, tail after closing tag from first child's tail - simplified)"""
    out: dict[str, tuple[str | None, str | None]] = {}
    for el in root:
        if el.tag != "string":
            continue
        name = el.attrib.get("name")
        if not name:
            continue
        text_parts = []
        if el.text:
            text_parts.append(el.text)
        for sub in el:
            if sub.text:
                text_parts.append(sub.text)
            if sub.tail:
                text_parts.append(sub.tail)
        if el.tail:
            text_parts.append(el.tail)
        val = "".join(text_parts) if text_parts else (el.text or "")
        out[name] = (val, None)
    return out


def main() -> None:
    base_path = APP_RES / "values" / "strings.xml"
    base_root = load_strings_xml(base_path)
    base_names = {el.attrib["name"] for el in base_root if el.tag == "string" and "name" in el.attrib}
    base_by_name = {el.attrib["name"]: el for el in base_root if el.tag == "string" and "name" in el.attrib}

    for values_dir in sorted(APP_RES.glob("values-*")):
        if not values_dir.is_dir():
            continue
        loc_path = values_dir / "strings.xml"
        if not loc_path.exists():
            continue
        loc_root = load_strings_xml(loc_path)
        loc_names = {el.attrib["name"] for el in loc_root if el.tag == "string" and "name" in el.attrib}
        missing = base_names - loc_names
        if not missing:
            continue
        for name in sorted(missing):
            src = base_by_name[name]
            # deep copy
            clone = ET.Element("string", src.attrib.copy())
            clone.text = src.text
            clone.tail = "\n    "
            for child in src:
                clone.append(child)
            loc_root.append(clone)
        tree = ET.ElementTree(loc_root)
        ET.indent(tree, space="    ")
        with open(loc_path, "wb") as f:
            f.write(b'<?xml version="1.0" encoding="utf-8"?>\n')
            tree.write(f, encoding="utf-8", xml_declaration=False)
        print(f"Patched {loc_path.name} (+{len(missing)} keys)")


if __name__ == "__main__":
    main()
