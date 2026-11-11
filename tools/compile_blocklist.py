#!/usr/bin/env python3
"""Compile EasyList and EasyPrivacy into the host list Skim ships.

The lists are dual-licensed. This script keeps the GPL-3.0-or-later option
and writes one host per line. Cosmetic rules, popup rules and options this
browser does not apply are dropped. The raw lists are not stored in the APK.

Refresh, from the repo root, with the network:

    python3 tools/compile_blocklist.py

That downloads the pinned commit below. Pass another full sha to move the pin.
There is no in-app download: a newer list ships in a newer APK, so the app
does not contact EasyList's servers at runtime.

Attribution required by the list licence: The EasyList authors (https://easylist.to/).
"""

from __future__ import annotations

import json
import re
import sys
import urllib.request
from pathlib import Path

PIN = "df0f934719102cd2ad93cb4dddd76a557acec460"
REPO = "https://api.github.com/repos/easylist/easylist"
RAW = "https://raw.githubusercontent.com/easylist/easylist"
OUT = Path("browser/src/main/assets/shield/blocklist.txt")
CACHE = Path("/tmp/easylist-pin")

# Options Skim can honor by blocking the whole host. Anything else (a site
# exception, a rewrite, a scriptlet) drops the rule so it cannot over-block.
ALLOWED = {
    "third-party",
    "script",
    "image",
    "stylesheet",
    "xmlhttprequest",
    "xhr",
    "media",
    "object",
    "object-subrequest",
    "font",
    "ping",
    "websocket",
    "subdocument",
    "other",
    "webrtc",
    "document",
}

HOST = re.compile(
    r"^(@@)?\|\|([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+)\^(\$(.+))?$"
)

SKIP_NAME = ("hide", "popup", "dimension", "ubo")


def get(url: str) -> bytes:
    CACHE.mkdir(parents=True, exist_ok=True)
    cached = CACHE / url.replace("://", "_").replace("/", "_")
    if cached.exists():
        return cached.read_bytes()
    request = urllib.request.Request(url, headers={"User-Agent": "skim-browser-compile-blocklist"})
    with urllib.request.urlopen(request, timeout=60) as response:
        data = response.read()
    cached.write_bytes(data)
    return data


def list_files(sha: str) -> list[str]:
    paths: list[str] = []
    for folder in ("easylist", "easyprivacy"):
        listing = json.loads(get(f"{REPO}/contents/{folder}?ref={sha}"))
        for item in listing:
            name = item["name"].lower()
            if item["type"] != "file" or not name.endswith(".txt"):
                continue
            if any(part in name for part in SKIP_NAME):
                continue
            paths.append(f"{folder}/{item['name']}")
    return paths


def accept(line: str) -> tuple[str, str] | None:
    match = HOST.match(line.strip().lower())
    if not match:
        return None
    exception, host, _, options = match.groups()
    third = False
    if options:
        for part in options.split(","):
            part = part.strip()
            if not part or part.startswith("~") or "=" in part or part not in ALLOWED:
                return None
            if part == "third-party":
                third = True
    if host.startswith("www.") and "." in host[4:]:
        host = host[4:]
    if exception:
        return ("X" if third else "A", host)
    return ("T" if third else "B", host)


def parent_in(host: str, parents: set[str]) -> bool:
    labels = host.split(".")
    for index in range(1, len(labels) - 1):
        if ".".join(labels[index:]) in parents:
            return True
    return False


def compile_rules(texts: list[str]) -> dict[str, set[str]]:
    kinds: dict[str, set[str]] = {"B": set(), "T": set(), "A": set(), "X": set()}
    for text in texts:
        for line in text.splitlines():
            if not line or line.startswith("!") or line.startswith("["):
                continue
            parsed = accept(line)
            if parsed:
                kinds[parsed[0]].add(parsed[1])
    for host in kinds["A"]:
        kinds["B"].discard(host)
        kinds["T"].discard(host)
        kinds["X"].discard(host)
    kinds["T"] -= kinds["B"]
    kinds["T"] -= kinds["X"]
    kinds["B"] = {host for host in kinds["B"] if not parent_in(host, kinds["B"])}
    kinds["T"] = {host for host in kinds["T"] if not parent_in(host, kinds["B"] | kinds["T"])}
    kinds["A"] = {host for host in kinds["A"] if not parent_in(host, kinds["A"])}
    kinds["X"] = {host for host in kinds["X"] if not parent_in(host, kinds["A"] | kinds["X"])}
    return kinds


def main() -> None:
    sha = sys.argv[1] if len(sys.argv) > 1 else PIN
    if not re.fullmatch(r"[0-9a-f]{40}", sha):
        raise SystemExit("pass the full 40-character commit sha")
    commit = json.loads(get(f"{REPO}/commits/{sha}"))
    when = commit["commit"]["committer"]["date"]
    paths = list_files(sha)
    texts = [get(f"{RAW}/{sha}/{path}").decode("utf-8", "replace") for path in paths]
    kinds = compile_rules(texts)
    lines = [
        "# Skim request shield",
        "# Sources: EasyList and EasyPrivacy",
        "# License: GPL-3.0-or-later, chosen from the dual license at",
        "# https://easylist.to/pages/licence.html",
        "# Attribution: The EasyList authors (https://easylist.to/)",
        f"# Upstream: easylist/easylist {sha} {when}",
        "# Kinds: B block, T block third-party, A allow, X allow third-party",
    ]
    for kind in ("A", "B", "T", "X"):
        for host in sorted(kinds[kind]):
            lines.append(f"{kind} {host}")
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    counts = {kind: len(hosts) for kind, hosts in kinds.items()}
    print(f"wrote {OUT} ({OUT.stat().st_size} bytes) from {sha}")
    print(counts)


if __name__ == "__main__":
    main()
