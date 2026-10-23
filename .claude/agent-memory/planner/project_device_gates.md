---
name: project-device-gates
description: Skim device testing uses dedicated AVDs skim_api36/skim_api28 on fixed ports; other projects' AVDs on this machine must not be touched
metadata:
  type: project
---

Device verification for skim-browser runs on dedicated emulators: `skim_api36` (port 5580, image `system-images;android-36;google_apis;arm64-v8a`) and `skim_api28` (port 5582, `system-images;android-28;google_apis;arm64-v8a`, downloaded via sdkmanager). Emulator binary is at `~/Library/Android/sdk/emulator/emulator` (not on PATH). The machine also has `duoly_baseline_profile_api36` and `localmsg_stress_api36` AVDs belonging to other projects — never use or stop them.

**Why:** Validation Session 1 (2026-10-21) chose "AVD 36 + download API 28"; red team found the plan assumed no emulator existed and lacked API 26–28 coverage.

**How to apply:** Any plan/phase with instrumented tests should reference the device-gate procedure in the skim plan.md (track emulator PID/port, reuse if running, `emu kill` at the end; a gate that can't run → DONE_WITH_CONCERNS). Related: [[reference-ak-plan-cli]].
