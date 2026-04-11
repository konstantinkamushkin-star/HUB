#!/usr/bin/env python3
"""
Optional scraper for divejourney.io (Egypt). The naive requests + BeautifulSoup
snippet does not work: spot lists are rendered in the browser (Next.js), not in
the initial HTML.

This script uses Playwright to open /countries/egypt and /destinations/*-egypt,
scroll, and collect /dive-spots/ links. It needs a reliable network path to
divejourney.io (timeouts are common on slow links).

For a quick, complete tabular export with coordinates and depth, the checked-in
`egypt_dive_sites.json` was generated from the public OpenDiveMap API
GET https://api.opendivemap.com/sites?country=EG (18 sites as of the export).
"""
import json
import re
from urllib.parse import urlparse

from bs4 import BeautifulSoup
from playwright.sync_api import sync_playwright

OUT = "egypt_dive_sites.json"
COUNTRY = "Egypt"

# From the Egypt country guide + common slug pattern; extend if new regions appear.
EGYPT_DESTINATION_PATHS = [
    "/destinations/dahab-egypt",
    "/destinations/hurghada-giftun-and-abu-nuhas-egypt",
    "/destinations/marsa-alam-abu-dabbab-and-elphinstone-egypt",
    "/destinations/sharm-el-sheikh-ras-mohammed-and-tiran-egypt",
]

BASE = "https://divejourney.io"


def extract_lat_lng(html: str) -> str:
    m = re.search(r"dive-map\?[^\"']*lat=([0-9.-]+)[^\"']*lng=([0-9.-]+)", html)
    if m:
        return f"{m.group(1)}, {m.group(2)}"
    m2 = re.search(r"[?&]lat=([0-9.-]+).*?[&']lng=([0-9.-]+)", html)
    if m2:
        return f"{m2.group(1)}, {m2.group(2)}"
    return ""


def slug_from_href(href: str) -> str | None:
    path = urlparse(href).path.strip("/").split("/")
    if len(path) >= 2 and path[0] == "dive-spots":
        return path[1]
    return None


def collect_links_from_page(page, url: str) -> dict[str, str]:
    """Returns slug -> best visible name from listing."""
    page.goto(url, wait_until="commit", timeout=600_000)
    # divejourney.io is often slow; waiting for full load can hang. Give the
    # bundle time to hydrate and lazy lists time to mount.
    page.wait_for_timeout(90_000)
    try:
        page.wait_for_selector('a[href*="/dive-spots/"]', timeout=120_000)
    except Exception:
        pass
    for _ in range(35):
        page.evaluate(
            """() => {
            const el = document.scrollingElement || document.body;
            if (el) window.scrollTo(0, el.scrollHeight);
        }"""
        )
        page.wait_for_timeout(500)

    soup = BeautifulSoup(page.content(), "lxml")
    out: dict[str, str] = {}
    for a in soup.select('a[href*="/dive-spots/"]'):
        href = a.get("href") or ""
        if not href.startswith("http"):
            href = BASE + href if href.startswith("/") else BASE + "/" + href
        slug = slug_from_href(href)
        if not slug or slug == "dive-spots":
            continue
        name = a.get_text(" ", strip=True) or slug.replace("-", " ").title()
        if slug not in out or len(name) > len(out[slug]):
            out[slug] = name
    return out


def enrich_spot(page, slug: str) -> tuple[str, str, str]:
    """Return (depth, description, coordinates) from spot detail page."""
    url = f"{BASE}/dive-spots/{slug}"
    page.goto(url, wait_until="commit", timeout=600_000)
    page.wait_for_timeout(25_000)
    html = page.content()
    coords = extract_lat_lng(html)
    soup = BeautifulSoup(html, "lxml")
    depth = ""
    description = ""
    h1 = soup.find("h1")
    # First substantial paragraph in main (heuristic)
    for p in soup.find_all("p"):
        t = p.get_text(" ", strip=True)
        if len(t) > 80 and "cookie" not in t.lower():
            description = t
            break
    # Depth: look for "meter" / "m " in about section
    blob = soup.get_text(" ", strip=True)
    dm = re.search(
        r"(\d+)\s*(?:to|-|–)\s*(\d+)\s*m(?:eter)?s?\b", blob, re.I
    ) or re.search(r"depth[^.]{0,40}(\d+)\s*m", blob, re.I)
    if dm:
        depth = dm.group(0)[:80]
    return depth, description, coords


def main() -> None:
    slug_to_name: dict[str, str] = {}

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        page = browser.new_page()
        page.set_default_navigation_timeout(600_000)
        page.set_default_timeout(600_000)

        dest_paths = list(dict.fromkeys(EGYPT_DESTINATION_PATHS))
        country_url = f"{BASE}/countries/egypt"
        page.goto(country_url, wait_until="commit", timeout=600_000)
        page.wait_for_timeout(45_000)
        soup = BeautifulSoup(page.content(), "lxml")
        for a in soup.select('a[href*="/destinations/"]'):
            h = a.get("href", "")
            if "egypt" in h.lower():
                path = urlparse(
                    h if h.startswith("http") else BASE + h
                ).path
                if path and path not in dest_paths:
                    dest_paths.append(path)

        seen_paths = []
        for path in dest_paths:
            if path in seen_paths:
                continue
            seen_paths.append(path)
            url = BASE + path if path.startswith("/") else f"{BASE}/{path}"
            found = collect_links_from_page(page, url)
            for s, n in found.items():
                if s not in slug_to_name or len(n) > len(slug_to_name[s]):
                    slug_to_name[s] = n

        # Enrich (sequential — slow network); set ENRICH=0 to skip
        import os

        rows: list[dict] = []
        slugs = sorted(slug_to_name.keys())
        # Per-spot pages are very slow on some networks; list-only is the default.
        do_enrich = os.environ.get("DIVEJOURNEY_ENRICH", "0").lower() in (
            "1",
            "true",
            "yes",
        )

        if do_enrich:
            for i, slug in enumerate(slugs):
                try:
                    depth, desc, coords = enrich_spot(page, slug)
                except Exception:
                    depth, desc, coords = "", "", ""
                rows.append(
                    {
                        "name": slug_to_name[slug],
                        "depth": depth,
                        "description": desc,
                        "coordinates": coords,
                        "country": COUNTRY,
                    }
                )
                if (i + 1) % 20 == 0:
                    print(f"enriched {i + 1}/{len(slugs)}", flush=True)
        else:
            for slug in slugs:
                rows.append(
                    {
                        "name": slug_to_name[slug],
                        "depth": "",
                        "description": "",
                        "coordinates": "",
                        "country": COUNTRY,
                    }
                )

        browser.close()

    # Match user's original field set; keep url as extra for traceability
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(rows, f, ensure_ascii=False, indent=2)

    print(f"Wrote {len(rows)} records to {OUT}")


if __name__ == "__main__":
    main()
