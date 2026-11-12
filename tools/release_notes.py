#!/usr/bin/env python3
"""Print one version's section from CHANGELOG.md.

A tag v1.2.3 publishes the body under the heading ## [1.2.3]. The heading may
also carry a date, as in ## [1.2.3] - 2026-11-12. The body runs until the next
## heading. The script writes that body to stdout and exits 1 when the heading
is missing, so a tag cannot publish an empty release.
"""

from __future__ import annotations

import argparse
import sys


def section(changelog: str, version: str) -> str:
    """Return the body under ## [version], without the heading line."""
    heading = f"## [{version}]"
    lines = changelog.splitlines()
    start = None
    for index, line in enumerate(lines):
        if line == heading or line.startswith(heading + " "):
            start = index + 1
            break
    if start is None:
        raise ValueError(f"CHANGELOG.md has no section {heading}")
    body: list[str] = []
    for line in lines[start:]:
        if line.startswith("## "):
            break
        body.append(line)
    text = "\n".join(body).strip()
    if not text:
        raise ValueError(f"CHANGELOG.md section {heading} is empty")
    return text + "\n"


def _self_test() -> None:
    sample = """# Changelog

## [Unreleased]

### Added

- Not this one.

## [1.2.3] - 2026-11-12

### Added

- The notes for this tag.

## [1.2.2]

### Fixed

- Older.
"""
    notes = section(sample, "1.2.3")
    if "The notes for this tag." not in notes or "Older." in notes or "Not this one." in notes:
        raise SystemExit("release notes extracted the wrong section")
    try:
        section(sample, "9.9.9")
    except ValueError:
        return
    raise SystemExit("a missing section should fail")


def main() -> int:
    parser = argparse.ArgumentParser(description="Print one CHANGELOG.md section.")
    parser.add_argument("changelog", nargs="?", help="Path to CHANGELOG.md")
    parser.add_argument("version", nargs="?", help="Version without the leading v")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        _self_test()
        return 0
    if not args.changelog or not args.version:
        parser.error("changelog and version are required")
    try:
        sys.stdout.write(section(open(args.changelog, encoding="utf-8").read(), args.version))
    except ValueError as error:
        print(error, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
