#!/usr/bin/env python3
from __future__ import annotations

import json
import math
import re
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
from typing import Any
from urllib.parse import urljoin, urlparse

import requests
from bs4 import BeautifulSoup
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry

BASE = "https://wada.ink"
OUT = Path("app/src/main/assets/palettes.json")
CACHE = Path(".cache/wada")
UA = "WadaRu/1.0 (offline personal color reference; factual palette indexing)"
HEX_RE = re.compile(r"\bHEX\s*#?([0-9A-Fa-f]{6})\b", re.I)
RGB_RE = re.compile(r"\bRGB\s*(\d{1,3})\s*[,/]\s*(\d{1,3})\s*[,/]\s*(\d{1,3})\b", re.I)
CMYK_RE = re.compile(r"\bCMYK\s*(\d{1,3})\s*[,/]\s*(\d{1,3})\s*[,/]\s*(\d{1,3})\s*[,/]\s*(\d{1,3})\b", re.I)
LAB_RE = re.compile(r"\bLAB\s*(-?\d+(?:\.\d+)?)\s*[,/]\s*(-?\d+(?:\.\d+)?)\s*[,/]\s*(-?\d+(?:\.\d+)?)\b", re.I)
LEADING_ID_RE = re.compile(r"^\s*(\d{1,3})\s+(.+?)\s*$")

def session() -> requests.Session:
    s = requests.Session()
    retry = Retry(
        total=5,
        connect=5,
        read=5,
        status=5,
        backoff_factor=0.8,
        status_forcelist=(429, 500, 502, 503, 504),
        allowed_methods=frozenset(["GET"]),
        respect_retry_after_header=True,
    )
    s.mount("https://", HTTPAdapter(max_retries=retry, pool_connections=16, pool_maxsize=16))
    s.headers.update({"User-Agent": UA, "Accept-Language": "en"})
    return s

S = session()

def cache_path(url: str) -> Path:
    parsed = urlparse(url)
    name = (parsed.path.strip("/") or "index").replace("/", "__")
    return CACHE / f"{name}.html"

def get_html(url: str) -> str:
    p = cache_path(url)
    if p.exists() and p.stat().st_size > 500:
        return p.read_text("utf-8")
    r = S.get(url, timeout=35)
    r.raise_for_status()
    html = r.text
    if len(html) < 500:
        raise RuntimeError(f"Suspiciously small response for {url}: {len(html)} bytes")
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(html, "utf-8")
    time.sleep(0.04)
    return html

def norm(s: str) -> str:
    return re.sub(r"[^a-z0-9]+", "", s.casefold())

def strip_id(text: str) -> tuple[str | None, str]:
    text = " ".join(text.split())
    m = LEADING_ID_RE.match(text)
    if m:
        return m.group(1).zfill(3), m.group(2).strip()
    return None, text

def palette_url(volume: int, number: int) -> str:
    return f"{BASE}/palettes/{number}" if volume == 1 else f"{BASE}/palettes/v2-{number}"

def parse_palette(volume: int, number: int) -> dict[str, Any]:
    url = palette_url(volume, number)
    soup = BeautifulSoup(get_html(url), "html.parser")
    h1 = soup.find("h1")
    if not h1:
        raise RuntimeError(f"No H1 at {url}")
    h1_text = " ".join(h1.stripped_strings)
    names = [x.strip() for x in h1_text.split("·") if x.strip()]
    if not names:
        raise RuntimeError(f"No names parsed from H1 at {url}: {h1_text!r}")

    color_links: list[dict[str, str | None]] = []
    for a in soup.find_all("a", href=True):
        href = a.get("href", "")
        if "/colors/" not in href:
            continue
        wada_id, label = strip_id(a.get_text(" ", strip=True))
        slug = href.rstrip("/").split("/")[-1]
        if not slug:
            continue
        color_links.append({"name": label, "slug": slug, "wadaId": wada_id})

    chosen = []
    used_slugs: set[str] = set()
    for target in names:
        match = next(
            (
                x for x in color_links
                if x["slug"] not in used_slugs and norm(str(x["name"])) == norm(target)
            ),
            None,
        )
        if match is None:
            # Wada.ink occasionally differs in punctuation/case between H1 and link.
            match = next(
                (
                    x for x in color_links
                    if x["slug"] not in used_slugs and (
                        norm(str(x["name"])) in norm(target)
                        or norm(target) in norm(str(x["name"]))
                    )
                ),
                None,
            )
        if match is None:
            raise RuntimeError(f"Could not match color {target!r} at {url}")
        used_slugs.add(str(match["slug"]))
        chosen.append({
            "name": target,
            "slug": match["slug"],
            "wadaId": match["wadaId"],
        })

    return {
        "id": f"v{volume}-{number:03d}",
        "volume": volume,
        "number": number,
        "sourceUrl": url,
        "colors": chosen,
    }

def parse_color(slug: str) -> dict[str, Any]:
    url = f"{BASE}/colors/{slug}"
    soup = BeautifulSoup(get_html(url), "html.parser")
    text = " ".join(soup.stripped_strings)
    h1 = soup.find("h1")
    name = " ".join(h1.stripped_strings) if h1 else slug.replace("-", " ").title()
    wada_id, clean_name = strip_id(name)
    name = clean_name

    hx = HEX_RE.search(text)
    if not hx:
        # Fallback: look for any CSS/visible #RRGGBB near color facts.
        all_hex = re.findall(r"#([0-9A-Fa-f]{6})\b", text)
        if not all_hex:
            raise RuntimeError(f"No HEX value found at {url}")
        hex_value = "#" + all_hex[0].upper()
    else:
        hex_value = "#" + hx.group(1).upper()

    rgb = RGB_RE.search(text)
    cmyk = CMYK_RE.search(text)
    lab = LAB_RE.search(text)

    return {
        "slug": slug,
        "name": name,
        "wadaId": wada_id,
        "hex": hex_value,
        "rgb": [int(x) for x in rgb.groups()] if rgb else None,
        "cmyk": [int(x) for x in cmyk.groups()] if cmyk else None,
        "lab": [float(x) for x in lab.groups()] if lab else None,
        "sourceUrl": url,
    }

def rgb_from_hex(h: str) -> list[int]:
    h = h.lstrip("#")
    return [int(h[i:i+2], 16) for i in (0, 2, 4)]

def main() -> None:
    CACHE.mkdir(parents=True, exist_ok=True)

    targets = [(1, i) for i in range(1, 349)] + [(2, i) for i in range(1, 234)]
    palettes: dict[tuple[int, int], dict[str, Any]] = {}
    failures: list[str] = []

    with ThreadPoolExecutor(max_workers=6) as ex:
        futs = {ex.submit(parse_palette, v, n): (v, n) for v, n in targets}
        for idx, fut in enumerate(as_completed(futs), 1):
            v, n = futs[fut]
            try:
                palettes[(v, n)] = fut.result()
            except Exception as e:
                failures.append(f"v{v}-{n}: {e}")
            if idx % 50 == 0:
                print(f"palettes {idx}/{len(targets)}")

    if failures:
        raise RuntimeError("Palette parse failures:\n" + "\n".join(failures[:30]))

    slugs = sorted({str(c["slug"]) for p in palettes.values() for c in p["colors"]})
    colors: dict[str, dict[str, Any]] = {}
    failures = []
    with ThreadPoolExecutor(max_workers=6) as ex:
        futs = {ex.submit(parse_color, slug): slug for slug in slugs}
        for idx, fut in enumerate(as_completed(futs), 1):
            slug = futs[fut]
            try:
                colors[slug] = fut.result()
            except Exception as e:
                failures.append(f"{slug}: {e}")
            if idx % 50 == 0:
                print(f"colors {idx}/{len(slugs)}")

    if failures:
        raise RuntimeError("Color parse failures:\n" + "\n".join(failures[:30]))

    result = []
    for key in sorted(palettes):
        p = palettes[key]
        enriched = []
        for c in p["colors"]:
            factual = dict(colors[str(c["slug"])])
            factual["name"] = c["name"]
            if c.get("wadaId"):
                factual["wadaId"] = c["wadaId"]
            if not factual.get("rgb"):
                factual["rgb"] = rgb_from_hex(str(factual["hex"]))
            # Keep only factual fields needed in the offline app.
            factual.pop("sourceUrl", None)
            enriched.append(factual)
        p["colors"] = enriched
        p.pop("sourceUrl", None)
        result.append(p)

    assert len(result) == 581, len(result)
    assert sum(1 for p in result if p["volume"] == 1) == 348
    assert sum(1 for p in result if p["volume"] == 2) == 233
    ids = [p["id"] for p in result]
    assert len(ids) == len(set(ids))

    payload = {
        "meta": {
            "title": "Sanzo Wada — offline palette facts",
            "language": "ru",
            "vol1": 348,
            "vol2": 233,
            "total": 581,
            "note": (
                "Palette membership, historical English color names and numerical color values "
                "are indexed from public Wada.ink pages. Descriptive Russian analysis is generated "
                "locally by the app and does not reproduce Wada.ink editorial descriptions."
            ),
        },
        "palettes": result,
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(payload, ensure_ascii=False, separators=(",", ":")), "utf-8")
    print(f"Wrote {OUT}: {len(result)} palettes, {len(colors)} colors")

if __name__ == "__main__":
    main()
