---
phase: 3
title: "Frame timing and baseline profile on the Pixel 6"
status: todo
priority: P1
effort: "3h"
dependencies: []
---

# Phase 3: Frame timing and baseline profile on the Pixel 6

## Goal

Measure startup and frame time on the owner's Pixel 6 with `FrameBenchmarks`, make the baseline profile there, and record the numbers before and after it in `docs/frame-timing.md`, which phase 4 of the identity plan compares against.

## Before you start

- The Pixel 6 has no Skim data to keep (the owner, 2026-11-26). The harness installs the benchmark build as `com.dantech.skimbrowser` and removes it afterwards.
- The phone is charged, cool and not in battery saver, with other apps closed. Write down its Android version and the commit being measured.

## Steps

1. Run all four tests: `ANDROID_SERIAL=<Pixel 6 serial> tools/bench_phone.sh`. The first run compiles the minified, non-debuggable benchmark build.
2. Read the `*benchmarkData.json` files under `macrobenchmark/build/outputs/connected_android_test_additional_output/`; the summary is under `macrobenchmark/build/reports/androidTests/connected/`. Copy the P50, P90 and P99 values into the first Results table.
3. Run `navigationAndTabsAndSheet` twice more on its own to see the spread, because phase 4 compares against it: `ANDROID_SERIAL=<Pixel 6 serial> tools/bench_phone.sh com.dantech.skimbrowser.macrobenchmark.FrameBenchmarks.navigationAndTabsAndSheet`.
4. Make the baseline profile once those numbers are steady: `ANDROID_SERIAL=<Pixel 6 serial> ./gradlew :baselineprofile:connectedBenchmarkAndroidTest --no-configuration-cache`. Copy `StartupProfile_generate-baseline-prof.txt` from `baselineprofile/build/outputs/connected_android_test_additional_output/` to `app/src/main/baseline-prof.txt`.
5. Measure again with the profile, as the header of `tools/bench_phone.sh` describes: `CompilationMode.Partial()` instead of the `CompilationMode.None()` that `FrameBenchmarks` uses today. Fill the second Results table. Whether the benchmark keeps `Partial` afterwards is part of the profile change and is said in its pull request.
6. Write `docs/frame-timing.md`: the phone, its Android version, the date, the commit measured, both tables, and how they were run. Add the page to the Docs table in `README.md`. Link it from the frame-time criterion in `../261123-1527-skim-identity-pill-motion/plan.md` and check that criterion.
7. Commit the profile in its own change, separate from the numbers: `app/src/main/baseline-prof.txt`, and the benchmark change from step 5 if it stays. A profile made on an emulator is never committed.

## Results

Phone: Pixel 6, Android version: . Commit: .

Without a profile (`CompilationMode.None()`):

| Test | Metric | P50 | P90 | P99 | Notes |
|---|---|---|---|---|---|
| `coldStartup` | `timeToInitialDisplayMs` | | | | |
| `coldStartup` | `frameDurationCpuMs` | | | | |
| `warmStartup` | `timeToInitialDisplayMs` | | | | |
| `scrollPage` | `frameDurationCpuMs` | | | | |
| `scrollPage` | `frameOverrunMs` | | | | |
| `navigationAndTabsAndSheet` | `frameDurationCpuMs` | | | | |
| `navigationAndTabsAndSheet` | `frameOverrunMs` | | | | |

With the baseline profile (`CompilationMode.Partial()`):

| Test | Metric | P50 | P90 | P99 | Notes |
|---|---|---|---|---|---|
| `coldStartup` | `timeToInitialDisplayMs` | | | | |
| `warmStartup` | `timeToInitialDisplayMs` | | | | |
| `navigationAndTabsAndSheet` | `frameDurationCpuMs` | | | | |
| `navigationAndTabsAndSheet` | `frameOverrunMs` | | | | |

## Todo

- [ ] All four tests on the Pixel 6
- [ ] Two more runs of `navigationAndTabsAndSheet`
- [ ] Baseline profile made on the Pixel 6
- [ ] Measured again with the profile
- [ ] `docs/frame-timing.md`, its README row and the identity plan link
- [ ] Profile committed in its own change

## Success Criteria

- The numbers come from the Pixel 6, not an emulator, and name its Android version and the commit.
- `app/src/main/baseline-prof.txt` exists, came from the Pixel 6, and has its own commit.
- The identity plan's frame-time criterion is checked and links to `docs/frame-timing.md`.

## Risk Assessment

- `skim_api36` can run the same script as a smoke check, but a software GPU is not a jank result.
- A warm phone throttles. Let it cool between runs and note the spread.
- A profile from a debuggable or emulator build would mislead the release build. Only the `:baselineprofile` run on the Pixel 6 produces the committed file.
