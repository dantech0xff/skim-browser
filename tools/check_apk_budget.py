#!/usr/bin/env python3
"""Fail when the releaseSmoke APK file grows past the committed baseline.

The gate is the APK file size in bytes. apkanalyzer download size is recorded
in the baseline for the write-up. It is not the number that fails the build,
because that estimate depends on the analyzer version.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path


def over_budget(size: int, baseline: int, max_growth: int) -> bool:
    return size > baseline + max_growth


def self_test() -> None:
    assert over_budget(100, 80, 10)
    assert not over_budget(90, 80, 10)
    assert not over_budget(80, 80, 0)
    assert not over_budget(80, 80, 32)


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", default="size-budget/releaseSmoke.json")
    parser.add_argument("--apk", help="APK path. Defaults to the path in the baseline.")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args(argv)
    if args.self_test:
        self_test()
        print("check_apk_budget self-test passed")
        return 0
    baseline_path = Path(args.baseline)
    baseline = json.loads(baseline_path.read_text(encoding="utf-8"))
    apk = Path(args.apk or baseline["apk"])
    size = apk.stat().st_size
    base = int(baseline["fileSizeBytes"])
    growth = int(baseline["maxGrowthBytes"])
    delta = size - base
    print(
        f"apk {size} bytes, baseline {base}, "
        f"growth {delta}, allowed {growth}, limit {base + growth}"
    )
    if over_budget(size, base, growth):
        print(
            f"releaseSmoke APK is {delta - growth} bytes over the budget. "
            f"See docs/lightweight-budget.md for how to update the baseline.",
            file=sys.stderr,
        )
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
