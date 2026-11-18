#!/usr/bin/env python3
"""
Builds VaultView's list of Android ports from the public Quiver Launcher catalog
(api.quiverlauncher.com). The catalog lists ports whose project supports Android, but not every
project's latest release actually has an Android build. This checks each one's newest release and
keeps only ports with something a handheld can install: an .apk, or a .zip with an .apk inside
(found by reading just the end of the zip, where its file list lives).

Usage: build_ports.py OUT.json.gz
"""
import gzip, json, sys, time, urllib.error, urllib.parse, urllib.request, zipfile, io, struct

API = "https://api.quiverlauncher.com/api/v1"
UA = "VaultView-ports/1.0 (+https://github.com/ipromiseitsokay/vaultview)"
PAUSE = 0.4

def get(url, headers=None, raw=False):
    for attempt in range(5):
        req = urllib.request.Request(url, headers={"User-Agent": UA, **(headers or {})})
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                data = r.read()
                return data if raw else json.loads(data)
        except urllib.error.HTTPError as e:
            if e.code in (429, 502, 503):
                time.sleep(int(e.headers.get("Retry-After") or 0) or 10 * (attempt + 1)); continue
            if e.code in (404, 416):
                return None
            raise
        except urllib.error.URLError:
            time.sleep(5 * (attempt + 1))
    return None

def zip_has_apk(url, size):
    """Reads the zip's central directory with two small range requests and looks for an .apk."""
    if not size:
        return None
    tail_len = min(size, 65536)
    try:
        tail = get(url, {"Range": f"bytes={size - tail_len}-{size - 1}"}, raw=True)
    except urllib.error.HTTPError:
        return None
    if not tail:
        return None
    eocd = tail.rfind(b"PK\x05\x06")
    if eocd < 0:
        return None
    cd_size, cd_offset = struct.unpack("<II", tail[eocd + 12:eocd + 20])
    if cd_offset == 0xFFFFFFFF:  # zip64: just scan what we have
        names = tail
    else:
        try:
            names = get(url, {"Range": f"bytes={cd_offset}-{cd_offset + cd_size - 1}"}, raw=True) or b""
        except urllib.error.HTTPError:
            names = tail
    i, found = 0, []
    while True:
        i = names.find(b"PK\x01\x02", i)
        if i < 0 or i + 46 > len(names): break
        n = struct.unpack("<H", names[i + 28:i + 30])[0]
        name = names[i + 46:i + 46 + n].decode("utf-8", "replace")
        if name.lower().endswith(".apk"): found.append(name)
        i += 46 + n
    return found or None

def pick(assets):
    def rank(a):
        n = a.get("filename", "").lower(); arch = (a.get("architecture") or "").lower()
        return (0 if n.endswith(".apk") else 10) + (0 if ("arm64" in n or "aarch64" in n or arch == "arm64") else
                5 if ("x86" in n or "armeabi" in n or arch.startswith("x86")) else 1)
    cands = [a for a in assets if a.get("os") == "android" and a.get("filename", "").lower().endswith((".apk", ".zip"))]
    for a in sorted(cands, key=rank):
        sha = next((c[7:] for c in (a.get("checksum"), a.get("unverifiedChecksum")) if c and c.startswith("sha256:")), None)
        out = {"url": a["url"], "filename": a["filename"], "size": a.get("size"), "sha256": sha}
        if a["filename"].lower().endswith(".apk"):
            return out
        apks = zip_has_apk(a["url"], a.get("size"))
        time.sleep(PAUSE)
        if apks:
            out["apk"] = sorted(apks, key=lambda x: ("arm64" not in x.lower(), x))[0]
            return out
    return None

def main(out):
    apps, cursor = [], None
    while True:
        q = {"os": "android", "projectType": "port", "sort": "rating", "limit": "100"}
        if cursor: q["cursor"] = cursor
        page = get(f"{API}/apps?" + urllib.parse.urlencode(q))
        apps += page["items"]
        cursor = page.get("nextCursor")
        if page.get("isDone", True) or not cursor: break
        time.sleep(PAUSE)
    print(f"{len(apps)} ports list Android", flush=True)
    kept = []
    for a in apps:
        rel = get(f"{API}/apps/{urllib.parse.quote(a['slug'])}/releases?limit=1")
        time.sleep(PAUSE)
        items = (rel or {}).get("items") or []
        asset = pick(items[0].get("assets", [])) if items else None
        if asset:
            a["android"] = asset
            a["androidVersion"] = items[0].get("version")
            kept.append(a)
        else:
            print(f"  no installable Android build: {a['slug']}", flush=True)
    print(f"{len(kept)} have an installable Android build", flush=True)
    with gzip.open(out, "wt", encoding="utf-8") as f:
        json.dump({"built": int(time.time() * 1000), "ports": kept}, f, separators=(",", ":"))

if __name__ == "__main__":
    main(sys.argv[1])
