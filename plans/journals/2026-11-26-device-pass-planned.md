---
title: Device pass planned
date: 2026-11-26
summary: "Planned the device session that release items 5 to 7, the #41 device checks and phase 4's baseline numbers wait on."
---

# Device pass planned

## What happened

Everything left before a release, and the rest of the UI work, needs a device. This container cannot run an emulator, and no folder on the owner's computer is shared with cloud sessions, so the session wrote a plan for a local Claude Code session instead: `plans/261126-0649-device-pass/`.

- Phase 1 runs `docs/deployment-guide.md` items 5 to 7 on `skim_api36` and `skim_api28`: both connected suites, `ReleaseSmokeTest` with a logcat check, and a manual pass for process death, memory pressure and FLAG_SECURE.
- Phase 2 looks on a device at what #41 and the onboarding motion plan only proved in Robolectric: site icons, the pill-to-chip morph, the narrow pill, tab reorder, Remove animations, and haptics on the phone.
- Phase 3 measures `FrameBenchmarks` on the Pixel 6, makes the baseline profile there, and measures again.

Scouting found two facts the plan depends on: `app/src/main/baseline-prof.txt` does not exist although the Smooth Compose rule requires a profile in the release build, and `tools/bench_phone.sh` installs the benchmark build as `com.dantech.skimbrowser` and removes it after the run.

## Decision

The owner chose on 2026-11-26: the Pixel 6 holds the kept numbers, it has no Skim data to keep, the baseline profile is made and committed in this pass, and the numbers go into a new `docs/frame-timing.md`.

## Next steps

Run the plan in a local session with the two AVDs and the Pixel 6. Phase 4 of the identity plan starts after phase 3 and compares against its numbers.

> Historical work record — not durable authority. Prefer docs/specs/ADRs for current decisions.
