#!/usr/bin/env python3
"""Fail when a play APK, its mapping, or its runtime classpath contains YouTube extractor code.

NewPipe and Rhino are allowed only in the full flavor. The play flavor is the
Google Play build. R8 renames classes, so the mapping file is part of the check.
A debug APK still contains the original names, and the classpath names the jars
even when R8 later deletes unused classes.
"""

from __future__ import annotations

import argparse
import sys
import zipfile
from pathlib import Path

APK_MARKERS = (
    "org/schabi/newpipe",
    "org.schabi.newpipe",
    "YoutubeVideoExtractor",
    "org/mozilla/javascript",
    "org.mozilla.javascript",
)

CLASSPATH_MARKERS = (
    "newpipe",
    "rhino",
    "extractor-youtube",
    "org/schabi",
)


def hits_in_text(text: str, markers: tuple[str, ...]) -> list[str]:
    return [marker for marker in markers if marker in text]


def classpath_hits(path: str) -> list[str]:
    lowered = path.lower()
    return [marker for marker in CLASSPATH_MARKERS if marker in lowered]


def scan_apk(path: Path) -> list[str]:
    found: list[str] = []
    with zipfile.ZipFile(path) as archive:
        for info in archive.infolist():
            if info.is_dir():
                continue
            payload = archive.read(info)
            # ISO-8859-1 keeps every byte, so a dex constant pool is searchable.
            text = payload.decode("iso-8859-1")
            for marker in hits_in_text(text, APK_MARKERS):
                found.append(f"{path.name}!{info.filename} contains {marker}")
    return found


def self_test() -> None:
    assert hits_in_text("org.schabi.newpipe.NewPipe", APK_MARKERS) == ["org.schabi.newpipe"]
    assert hits_in_text("youtube.com in a blocklist", APK_MARKERS) == []
    assert classpath_hits("/cache/NewPipeExtractor-v0.26.5.jar") == ["newpipe"]
    assert classpath_hits("/cache/okhttp-4.12.0.jar") == []
    clean = Path("/tmp/play-youtube-check-clean.apk")
    dirty = Path("/tmp/play-youtube-check-dirty.apk")
    with zipfile.ZipFile(clean, "w") as archive:
        archive.writestr("classes.dex", b"com.dantech.skimbrowser.MainActivity")
    with zipfile.ZipFile(dirty, "w") as archive:
        archive.writestr("classes.dex", b"org.schabi.newpipe.extractor.NewPipe")
    assert scan_apk(clean) == []
    assert scan_apk(dirty)
    clean.unlink()
    dirty.unlink()


def apk_files(path: Path) -> list[Path]:
    if path.is_dir():
        return sorted(item for item in path.rglob("*.apk") if item.is_file())
    if path.suffix == ".apk" and path.is_file():
        return [path]
    return []


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--apk", help="APK file or directory of APKs")
    parser.add_argument("--mapping", help="R8 mapping.txt for the play variant")
    parser.add_argument("--classpath", help="Text file of runtime classpath paths, one per line")
    args = parser.parse_args(argv)
    if args.self_test:
        self_test()
        print("check_play_has_no_youtube self-test passed")
        return 0
    if not args.apk and not args.mapping and not args.classpath:
        parser.error("pass --apk, --mapping, or --classpath, or --self-test")

    found: list[str] = []
    if args.apk:
        files = apk_files(Path(args.apk))
        if not files:
            print(f"No APK found at {args.apk}", file=sys.stderr)
            return 1
        for apk in files:
            found.extend(scan_apk(apk))
            print(f"Scanned {apk}")
    if args.mapping:
        mapping = Path(args.mapping)
        if mapping.is_file():
            text = mapping.read_text(encoding="utf-8", errors="replace")
            for marker in hits_in_text(text, APK_MARKERS):
                found.append(f"{mapping} contains {marker}")
            print(f"Scanned {mapping}")
    if args.classpath:
        listing = Path(args.classpath)
        for line in listing.read_text(encoding="utf-8").splitlines():
            for marker in classpath_hits(line):
                found.append(f"classpath contains {marker}: {line}")
                break
        print(f"Scanned classpath list {listing}")

    if found:
        print("Play build contains YouTube extractor code:", file=sys.stderr)
        for item in found:
            print(f"  - {item}", file=sys.stderr)
        return 1
    print("Play build has no YouTube extractor code")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
