---
title: "Device pass: release checks, device look and frame timing"
description: "One session on the owner's machine runs the device checks a release still needs and the ones the merged UI work still owes, and writes every result down."
status: pending
priority: P1
effort: 8h
issue:
branch: master
tags: [android, release, verification, performance]
blockedBy: []
blocks: [261123-1527-skim-identity-pill-motion]
created: 2026-11-26
---

# Device pass: release checks, device look and frame timing

## Overview

A release and the rest of the UI work both wait on a device. This plan is one session on the owner's machine with the dedicated AVDs `skim_api36` and `skim_api28` and the owner's Pixel 6. It runs the device items of `docs/deployment-guide.md` ("Ship when all of these are true", items 5 to 7), looks at what pull request #41 and the onboarding motion plan have only seen in Robolectric, measures frame time with `FrameBenchmarks` on the Pixel 6, and makes the baseline profile the release build still lacks. A cloud container cannot run an emulator, so a local Claude Code session runs this plan.

Scope challenge: **HOLD SCOPE**. The pass records what it finds. A fix is a separate change with its own test.

## Goals

| # | Goal | Priority |
|---|------|----------|
| 1 | Release items 5 to 7 each have a recorded pass, or a failure with its log | P1 |
| 2 | Each feature from #41 and the onboarding entrance has been seen on a device | P2 |
| 3 | Pixel 6 frame timings exist for phase 4 of the identity plan to compare against | P1 |
| 4 | The release build carries a baseline profile made on the Pixel 6 | P1 |

## Constraints

- Gradle runs from the repo root with `--no-configuration-cache` (`AGENTS.md`).
- Instrumented runs use only `skim_api36` on port 5580 and `skim_api28` on port 5582. Do not install a system image, and do not reuse or stop another project's AVD (`AGENTS.md`). If either AVD is missing, stop and ask the owner.
- `pm clear`, `wm density` and the animation scales change only on those AVDs, never on the Pixel 6.
- Emulator numbers are a smoke check, not a result. Kept frame numbers and the baseline profile come from the Pixel 6 (README "Device checks").
- Boot each AVD once on its port, note its PID, and stop the AVDs this session booted when it ends.
- No proxy URL or key goes into the repo. Steps that use Ask Skim need `skim.aiProxyUrl` in `local.properties` (README "local.properties"); without it, record them as not checked.

## Non-goals

- Building phase 4 of the identity plan. It starts after phase 3 here and keeps its own plan.
- Release items 1 to 4: the proxy spend cap, the real proxy and legal URLs, and the release builds that use them.
- Play Integrity and redrawing the design boards.

## Phases

| # | Phase | Status |
|---|-------|--------|
| 1 | [Release checks on the AVDs](./phase-01-start.md) | Pending |
| 2 | [Device look](./phase-02-device-look.md) | Pending |
| 3 | [Frame timing and baseline profile on the Pixel 6](./phase-03-phone-frame-timing.md) | Pending |

## Success Criteria

- [ ] Every Results row in the three phases is filled: pass, fail with a log excerpt in `./reports/`, or not checked with the reason.
- [ ] `docs/frame-timing.md` holds the Pixel 6 numbers, before and after the baseline profile, and the identity plan's frame-time criterion links to it.
- [ ] `app/src/main/baseline-prof.txt`, made on the Pixel 6, is committed in its own change.
- [ ] The identity plan's "Not yet seen on a device" notes are updated from phase 2.
- [ ] The device box in `../261030-0843-onboarding-motion/phase-02-verify.md` is checked, or says what looked wrong.
- [ ] The AVDs this session booted are stopped.

## Decisions

The owner, 2026-11-26:

- The Pixel 6 is the phone for kept numbers. Phase 4 of the identity plan measures again on the same phone.
- The Pixel 6 has no Skim data to keep, so the harness may install the benchmark build there and remove it afterwards.
- This pass makes the baseline profile on the Pixel 6 and commits it. The Smooth Compose rule in `docs/product-vision.md` says the release build includes one, and `app/src/main/baseline-prof.txt` does not exist yet.
- The Pixel 6 numbers go into a new page, `docs/frame-timing.md`.

<!-- slug: device-pass -->
