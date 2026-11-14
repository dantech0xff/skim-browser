#!/usr/bin/env python3
"""Compile EasyList and EasyPrivacy into the lists Skim ships.

The lists are dual-licensed. This script keeps the GPL-3.0-or-later option.
Host rules become one host per line. Cosmetic `##` and `#@#` rules become the
tab-separated file the app turns into a stylesheet. Procedural (`#?#`),
scriptlet (`#$#`) and extended-selector rules are dropped. The raw lists are
not stored in the APK.

Refresh, from the repo root, with the network:

    python3 tools/compile_blocklist.py

That downloads the pinned commit below and writes both files. Pass
`--cosmetic-only` to leave the host file untouched, `--cookie-only` to write
the cookie-banner file from the EasyList Cookie general hide list, or
`--self-test` to check the cosmetic parser without the network. Pass another
full sha to move the pin.
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
COSMETIC_OUT = Path("browser/src/main/assets/shield/cosmetic.txt")
COOKIE_OUT = Path("browser/src/main/assets/shield/cookie.txt")
COOKIE_CAP = 1800
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
SKIP_COSMETIC = ("popup", "dimension", "ubo")
DOMAIN = re.compile(
    r"^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+$"
)
EXTENDED = (
    ":-abp-",
    ":xpath(",
    ":has-text(",
    ":matches-css",
    ":matches-attr(",
    ":upward(",
    ":remove(",
    ":style(",
    ":min-text-length(",
    ":watch-attr(",
    ":others(",
    ":matches-path(",
)
PAGE_WIDE = {"*", "html", "body", ":root"}


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


def list_files(sha: str, skip: tuple[str, ...] = SKIP_NAME) -> list[str]:
    paths: list[str] = []
    for folder in ("easylist", "easyprivacy"):
        listing = json.loads(get(f"{REPO}/contents/{folder}?ref={sha}"))
        for item in listing:
            name = item["name"].lower()
            if item["type"] != "file" or not name.endswith(".txt"):
                continue
            if any(part in name for part in skip):
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
    # hostOf drops a leading "www.". The list has to store that same key or the rule never matches.
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


def selector_ok(selector: str) -> bool:
    if not selector or len(selector) > 200 or selector in PAGE_WIDE:
        return False
    if any(c in selector for c in "{}\n\r\t\\<@"):
        return False
    lower = selector.lower()
    if any(token in lower for token in EXTENDED):
        return False
    if "url(" in lower or "expression(" in lower or "/*" in selector:
        return False
    return True


def norm_domain(raw: str) -> str | None:
    name = raw.strip().lower()
    if name.startswith("www.") and "." in name[4:]:
        name = name[4:]
    try:
        name = name.encode("idna").decode("ascii")
    except UnicodeError:
        return None
    if not DOMAIN.match(name):
        return None
    return name


def split_domains(raw: str) -> tuple[list[str], list[str]] | None:
    if not raw:
        return [], []
    positive: list[str] = []
    negative: list[str] = []
    for part in raw.split(","):
        token = part.strip()
        if not token:
            return None
        negated = token.startswith("~")
        domain = norm_domain(token[1:] if negated else token)
        if domain is None:
            return None
        (negative if negated else positive).append(domain)
    return positive, negative


def parse_cosmetic(line: str) -> tuple[list[str], list[str], str, bool] | None:
    stripped = line.strip()
    if not stripped or stripped[0] in "![":
        return None
    if "#?#" in stripped or "#$#" in stripped:
        return None
    for sep, exception in (("#@#", True), ("##", False)):
        at = stripped.find(sep)
        if at < 0:
            continue
        domains = split_domains(stripped[:at].strip())
        selector = stripped[at + len(sep) :].strip()
        if domains is None or not selector_ok(selector):
            return None
        return domains[0], domains[1], selector, exception
    return None


def compile_cosmetic(texts: list[str]) -> list[str]:
    seen: set[tuple] = set()
    rows: list[tuple] = []
    for text in texts:
        for line in text.splitlines():
            parsed = parse_cosmetic(line)
            if parsed is None:
                continue
            positive, negative, selector, exception = parsed
            unless = tuple(sorted(set(negative)))
            if exception:
                if not positive and not unless:
                    row = ("E", selector)
                    if row not in seen:
                        seen.add(row)
                        rows.append(row)
                    continue
                if unless:
                    continue
                for domain in sorted(set(positive)):
                    row = ("X", domain, selector)
                    if row not in seen:
                        seen.add(row)
                        rows.append(row)
                continue
            if positive:
                for domain in sorted(set(positive)):
                    row = ("H", domain, selector, unless)
                    if row not in seen:
                        seen.add(row)
                        rows.append(row)
            else:
                row = ("G", selector, unless)
                if row not in seen:
                    seen.add(row)
                    rows.append(row)
    rows.sort(key=lambda row: tuple(str(part) for part in row))

    def emit(row: tuple) -> str:
        kind = row[0]
        if kind == "E":
            return f"E\t{row[1]}"
        if kind == "G":
            extra = "".join(f"\t!{domain}" for domain in row[2])
            return f"G\t{row[1]}{extra}"
        if kind == "X":
            return f"X\t{row[1]}\t{row[2]}"
        extra = "".join(f"\t!{domain}" for domain in row[3])
        return f"H\t{row[1]}\t{row[2]}{extra}"

    return [emit(row) for row in rows]


def cosmetic_header(sha: str, when: str) -> list[str]:
    return [
        "# Skim cosmetic rules",
        "# Sources: EasyList and EasyPrivacy",
        "# License: GPL-3.0-or-later, chosen from the dual license at",
        "# https://easylist.to/pages/licence.html",
        "# Attribution: The EasyList authors (https://easylist.to/)",
        f"# Upstream: easylist/easylist {sha} {when}",
        "# Kinds: G generic hide, E generic exception, H domain hide, X domain exception",
        "# A hide may end with !domain for a host it skips. Procedural rules are not here.",
    ]


def write_cosmetic(sha: str, when: str, texts: list[str]) -> None:
    body = compile_cosmetic(texts)
    COSMETIC_OUT.parent.mkdir(parents=True, exist_ok=True)
    COSMETIC_OUT.write_text("\n".join(cosmetic_header(sha, when) + body) + "\n", encoding="utf-8")
    counts: dict[str, int] = {}
    for line in body:
        counts[line[0]] = counts.get(line[0], 0) + 1
    print(f"wrote {COSMETIC_OUT} ({COSMETIC_OUT.stat().st_size} bytes) from {sha}")
    print(counts)


def self_test() -> None:
    text = """
! comment
[Adblock Plus 2.0]
##.adsbox
example.com##.banner
example.com#@#.adsbox
~news.example##.popup
a.com,b.com##.sponsor
example.com#$#abort-on-property-read
example.com#?#div:has(> .ad)
##div:-abp-has(.ad)
##body
##.slot > .ad
"""
    lines = set(compile_cosmetic([text]))
    expected = {
        "G\t.adsbox",
        "H\texample.com\t.banner",
        "X\texample.com\t.adsbox",
        "G\t.popup\t!news.example",
        "H\ta.com\t.sponsor",
        "H\tb.com\t.sponsor",
        "G\t.slot > .ad",
    }
    missing = expected - lines
    extra = lines - expected
    if missing or extra:
        raise SystemExit(f"self-test failed\nmissing {sorted(missing)}\nextra {sorted(extra)}")
    print("cosmetic self-test ok")


COOKIE_NEEDLE = re.compile(
    r"onetrust|cookiebot|cybot|didomi|usercentrics|quantcast|osano|complianz|cmplz|"
    r"cookie-law|cookieconsent|cookie-consent|cookie-banner|cookie-notice|cookie-popup|"
    r"cc-window|cc-banner|cc-revoke|sp_message|qc-cmp|cky-|cookieyes|iubenda|termly|"
    r"tarteaucitron|trustarc|truste|sourcepoint|fundingchoices|evidon|sirdata|ppms_cm|"
    r"gdpr-banner|gdpr-cookie|consent-banner|consent-modal|eu-cookie|hs-eu-cookie|"
    r"cli-modal|cli-bar|cli-popup|wt-cli",
    re.IGNORECASE,
)
COOKIE_CLASS = re.compile(r"cookie|consent|gdpr", re.IGNORECASE)
# Roots the general list names only in part, or not at all. Hiding the root is what
# takes the banner down, and the scroll unlock looks for the same selectors.
COOKIE_ROOTS = (
    "#onetrust-consent-sdk",
    "#onetrust-banner-sdk",
    "#CybotCookiebotDialog",
    "#CybotCookiebotDialogBodyUnderlay",
    "#didomi-host",
    "#usercentrics-root",
    "#sp_message_container",
    "#qc-cmp2-container",
    "#tarteaucitronRoot",
    ".osano-cm-window",
    ".fc-consent-root",
    ".cc-window",
    "#cookie-law-info-bar",
    "#hs-eu-cookie-confirmation",
    ".cky-consent-container",
    "#cmplz-cookiebanner-container",
)


def cookie_rank(selector: str) -> tuple:
    if selector in COOKIE_ROOTS:
        return (0, len(selector), selector)
    if COOKIE_NEEDLE.search(selector):
        return (1, len(selector), selector)
    if selector.startswith(".") and COOKIE_CLASS.search(selector):
        return (2, len(selector), selector)
    return (3, len(selector), selector)


def keep_cookie(rows: list[str]) -> list[str]:
    generic = [row for row in rows if row.startswith("G\t")]
    chosen: list[str] = []
    seen: set[str] = set()

    def add(selector: str) -> None:
        if selector in seen or not selector_ok(selector):
            return
        if len(chosen) >= COOKIE_CAP:
            return
        seen.add(selector)
        chosen.append(f"G\t{selector}")

    for selector in COOKIE_ROOTS:
        add(selector)
    ranked = sorted((row.split("\t", 1)[1] for row in generic), key=cookie_rank)
    for selector in ranked:
        if cookie_rank(selector)[0] == 3 and not (
            selector.startswith("#") and COOKIE_CLASS.search(selector) and " " not in selector and len(selector) <= 40
        ):
            continue
        if cookie_rank(selector)[0] == 2 and (" " in selector or ">" in selector or len(selector) > 60):
            continue
        add(selector)
    return chosen


def cookie_header(sha: str, when: str) -> list[str]:
    return [
        "# Skim cookie-banner hides",
        "# Sources: EasyList Cookie general hide, plus the banner roots named below",
        "# License: GPL-3.0-or-later, chosen from the dual license at",
        "# https://easylist.to/pages/licence.html",
        "# Attribution: The EasyList authors (https://easylist.to/)",
        f"# Upstream: easylist/easylist {sha} {when} easylist_cookie/easylist_cookie_general_hide.txt",
        "# Kinds: G generic hide. Procedural rules are not here.",
        f"# Cap: {COOKIE_CAP} selectors, banner roots and cookie or consent names first.",
    ]


def write_cookie(sha: str, when: str) -> None:
    general = get(f"{RAW}/{sha}/easylist_cookie/easylist_cookie_general_hide.txt").decode("utf-8", "replace")
    body = keep_cookie(compile_cosmetic([general]))
    COOKIE_OUT.parent.mkdir(parents=True, exist_ok=True)
    COOKIE_OUT.write_text("\n".join(cookie_header(sha, when) + body) + "\n", encoding="utf-8")
    print(f"wrote {COOKIE_OUT} ({COOKIE_OUT.stat().st_size} bytes, {len(body)} rules) from {sha}")


def main() -> None:
    flags = {"--cosmetic-only", "--cookie-only"}
    args = [arg for arg in sys.argv[1:] if arg not in flags]
    cosmetic_only = "--cosmetic-only" in sys.argv[1:]
    cookie_only = "--cookie-only" in sys.argv[1:]
    if args[:1] == ["--self-test"]:
        self_test()
        return
    sha = args[0] if args else PIN
    if not re.fullmatch(r"[0-9a-f]{40}", sha):
        raise SystemExit("pass the full 40-character commit sha")
    commit = json.loads(get(f"{REPO}/commits/{sha}"))
    when = commit["commit"]["committer"]["date"]
    if cookie_only:
        write_cookie(sha, when)
        return
    if not cosmetic_only:
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
    cosmetic_paths = list_files(sha, SKIP_COSMETIC)
    cosmetic_texts = [get(f"{RAW}/{sha}/{path}").decode("utf-8", "replace") for path in cosmetic_paths]
    write_cosmetic(sha, when, cosmetic_texts)


if __name__ == "__main__":
    main()
