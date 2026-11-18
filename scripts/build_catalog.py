#!/usr/bin/env python3
"""
Builds VaultView's game catalog: every game's name, regions, version, rating and media type for
each system on vimm.net, plus each system's Top 10. The app ships this file so lists and search
are instant and offline. Fetches slowly (one page at a time, with pauses) to be gentle with the site.

Usage: build_catalog.py OUT.json.gz [SYSTEM ...]
"""
import gzip, html, json, re, sys, time, urllib.error, urllib.request

SYSTEMS = ["NES", "SNES", "N64", "GameCube", "Wii", "WiiWare", "WiiU", "GB", "VB", "GBC", "GBA", "DS", "3DS",
           "PS1", "PS2", "PS3", "PSP", "SMS", "Genesis", "SegaCD", "32X", "Saturn", "Dreamcast", "GG",
           "Xbox", "Xbox360", "X360-D", "Atari2600", "Atari5200", "Atari7800", "Jaguar", "JaguarCD", "Lynx",
           "TG16", "TGCD", "CDi"]
SECTIONS = ["#"] + [chr(c) for c in range(ord("A"), ord("Z") + 1)]
UA = "VaultView-catalog/1.0 (+https://github.com/ipromiseitsokay/vaultview)"
PAUSE = 2.0  # seconds between requests

def fetch(url):
    for attempt in range(6):
        req = urllib.request.Request(url, headers={"User-Agent": UA})
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                return r.read().decode("utf-8", "replace")
        except urllib.error.HTTPError as e:
            if e.code in (429, 503):
                wait = int(e.headers.get("Retry-After") or 0) or 15 * (attempt + 1)
                print(f"  busy ({e.code}), waiting {wait}s", flush=True)
                time.sleep(wait)
                continue
            if e.code == 404:
                return ""
            raise
        except urllib.error.URLError:
            time.sleep(10 * (attempt + 1))
    raise RuntimeError(f"gave up on {url}")

def text(s):
    return html.unescape(re.sub(r"<[^>]+>", "", s)).strip()

def parse_rows(page):
    games = []
    for row in re.findall(r"<tr>(.*?)</tr>", page, re.S):
        link = re.search(r'<a href="/vault/(\d+)"[^>]*>(.*?)</a>', row, re.S)
        if not link:
            continue
        cells = re.findall(r"<td[^>]*>(.*?)</td>", row, re.S)
        title_i = next((i for i, c in enumerate(cells) if f'/vault/{link.group(1)}"' in c), 0)
        version = text(cells[title_i + 2]) if len(cells) > title_i + 2 else ""
        rating = re.search(r'p=rating[^>]*>([^<]*)<', row)
        rating = rating.group(1).strip() if rating else ""
        try:
            float(rating)
        except ValueError:
            rating = ""
        media = re.search(r'class="media-icon" title="([^"]+)"', row)
        games.append([
            int(link.group(1)),
            text(link.group(2)),
            re.findall(r'class="flag" title="([^"]+)"', row),
            version,
            rating,
            media.group(1) if media else "",
            [t for t in re.findall(r'<b [^>]*title="([^"]+)"', cells[title_i] if cells else "")],
        ])
    return games

def parse_top(page):
    body = re.search(r'<tbody id="topTen"(.*?)</tbody>', page, re.S)
    return [int(i) for i in re.findall(r'<a href="(\d+)"', body.group(1))] if body else []

def main():
    out = sys.argv[1]
    systems = sys.argv[2:] or SYSTEMS
    catalog = {"built": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()), "systems": {}}
    for code in systems:
        top = parse_top(fetch(f"https://vimm.net/vault/{code}"))
        time.sleep(PAUSE)
        seen, games = set(), []
        for s in SECTIONS:
            url = (f"https://vimm.net/vault/?p=list&system={code}&section=number" if s == "#"
                   else f"https://vimm.net/vault/{code}/{s}")
            for g in parse_rows(fetch(url)):
                if g[0] not in seen:
                    seen.add(g[0]); games.append(g)
            time.sleep(PAUSE)
        catalog["systems"][code] = {"top": top, "games": games}
        print(f"{code}: {len(games)} games, top {len(top)}", flush=True)
    data = json.dumps(catalog, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    with gzip.open(out, "wb", compresslevel=9) as f:
        f.write(data)
    total = sum(len(v["games"]) for v in catalog["systems"].values())
    print(f"wrote {out}: {total} games, {len(data) // 1024} KB raw", flush=True)

if __name__ == "__main__":
    main()
