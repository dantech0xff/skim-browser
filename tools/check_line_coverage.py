#!/usr/bin/env python3
"""Fail when the aggregated Kover line rate is under the floor.

The Gradle build still only reports coverage. This script is the CI floor.
It reads the root LINE counter of a Kover XML report, which is the total
for that report, and ignores the per-class counters nested under it.
"""

from __future__ import annotations

import argparse
import sys
import xml.etree.ElementTree as ET

MIN_LINE_PERCENT = 80


def root_line_counter(xml_text: str) -> tuple[int, int]:
    root = ET.fromstring(xml_text)
    for child in list(root):
        if child.tag != "counter":
            continue
        if child.attrib.get("type") != "LINE":
            continue
        return int(child.attrib["covered"]), int(child.attrib["missed"])
    raise ValueError("Kover report has no root LINE counter")


def meets_floor(covered: int, missed: int, floor_percent: int = MIN_LINE_PERCENT) -> bool:
    total = covered + missed
    if total <= 0:
        return False
    return covered * 100 >= floor_percent * total


def line_percent(covered: int, missed: int) -> float:
    total = covered + missed
    if total <= 0:
        return 0.0
    return 100.0 * covered / total


def self_test() -> None:
    sample = """<?xml version="1.0" ?>
<report>
  <package>
    <counter type="LINE" missed="50" covered="50"/>
  </package>
  <counter type="INSTRUCTION" missed="1" covered="9"/>
  <counter type="LINE" missed="20" covered="80"/>
</report>
"""
    covered, missed = root_line_counter(sample)
    assert (covered, missed) == (80, 20), (covered, missed)
    assert meets_floor(80, 20)
    assert meets_floor(800, 200)
    assert not meets_floor(79, 21)
    assert not meets_floor(0, 0)
    nested = """<report><class><counter type="LINE" missed="0" covered="100"/></class></report>"""
    try:
        root_line_counter(nested)
    except ValueError:
        pass
    else:
        raise AssertionError("a nested LINE counter must not count as the total")


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("report", nargs="?", help="Path to the Kover XML report")
    parser.add_argument("--self-test", action="store_true", help="Run the parser checks and exit")
    parser.add_argument("--floor", type=int, default=MIN_LINE_PERCENT)
    args = parser.parse_args(argv)
    if args.self_test:
        self_test()
        print("check_line_coverage self-test passed")
        return 0
    if not args.report:
        parser.error("report path is required unless --self-test is set")
    covered, missed = root_line_counter(open(args.report, encoding="utf-8").read())
    percent = line_percent(covered, missed)
    print(
        f"line coverage {percent:.2f}% "
        f"(covered={covered} missed={missed} floor={args.floor})"
    )
    if not meets_floor(covered, missed, args.floor):
        print(
            f"line coverage {percent:.2f}% is under the {args.floor}% floor",
            file=sys.stderr,
        )
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
