---
title: "Android splash screen"
description: "Show the Sunny mark on the neutral window color at cold start via androidx.core:core-splashscreen 1.2.0."
status: completed
priority: P2
effort: 4h
issue: 
branch: master
tags: [android, splash, ui]
blockedBy: []
blocks: []
created: 2026-10-30
---

# Android splash screen

## Overview

Cold start shows the Sunny mark centered on the same window color the app already uses (white in day, `#202124` at night), on every API from 26 up. The splash then hands the window back to `Theme.SkimBrowser`. Scope challenge: **HOLD SCOPE**. The settled contract is the `core-splashscreen` direction from the brainstorm. Animated icons, a bottom brand mark, holding the splash until settings load, and syncing the splash to the in-app Theme setting are out of scope.

## Outcome

The starting window shows the blue Sunny shape with white sparkles on `#FFFFFF` or `#202124`, then the existing edge-to-edge Compose UI.

## Constraints

- `minSdk` 26, `targetSdk` 37. `MainActivity` already calls `enableEdgeToEdge()` before `super.onCreate()` (`MainActivity.kt:70-72`).
- Splash colors follow system night mode (`values` / `values-night`). The in-app `ThemeSetting` lives in DataStore and is applied only in Compose (`SkimAppViewModel.kt:91-95`). The system draws the splash before that read.
- Startup may wait up to 8 seconds for settings and another 8 seconds for tabs (`SkimAppViewModel.kt:132-137`, `START_TIMEOUT_MS` at line 153). The splash must not wait for that.
- `appcompat-resources` comes in with splashscreen 1.2.0. The app stays on `ComponentActivity` and `android:Theme.Material`, not `AppCompatActivity`.

## Non-goals

- Animated icon, `windowSplashScreenBrandingImage`, `windowSplashScreenIconBackgroundColor`.
- `setKeepOnScreenCondition`. The empty `Surface` before `started` (`SkimApp.kt:56-60`) stays. Its color matches the splash background.
- Persisting night mode with `UiModeManager.setApplicationNightMode` so a forced Light/Dark setting matches the next cold start.
- Changing the launcher adaptive icon or its webp densities.

## Acceptance criteria

- Day splash background is `#FFFFFF`. Night splash background is `#202124`.
- The splash icon is a dedicated drawable whose sparkles are painted white, not punched out of the blue shape.
- After the splash, the activity theme is `Theme.SkimBrowser` and `enableEdgeToEdge()` still runs.
- `MainActivityLifecycleTest` still launches. A Robolectric test reads the splash theme in day and night.

## Dependencies

- `androidx.core:core-splashscreen:1.2.0` (stable, 5 Nov 2025). Not bundled in `core-ktx` 1.19.1.
- Existing colors `@color/skim_window_light` and `@color/skim_window_dark`.
- Path geometry already in `app/src/main/res/drawable/ic_launcher_foreground.xml`.

Research: [API](./research/researcher-01-splashscreen-api.md), [icon mask](./research/researcher-02-splash-icon-mask.md). Scout: [report](./reports/scout-report.md).

## Phases

| Phase | Name | Status |
|-------|------|--------|
| 1 | [Splash theme and icon](./phase-01-start.md) | Done |
| 2 | [Install and verify](./phase-02-install-and-verify.md) | Done |

## Success Criteria

- [x] Phase 1 theme and drawable are in the resource graph and reference `Theme.SplashScreen`.
- [x] Phase 2 calls `installSplashScreen()` before `enableEdgeToEdge()` and `super.onCreate()`.
- [x] `./gradlew :app:testDebugUnitTest --no-configuration-cache --tests 'com.dantech.skimbrowser.ui.theme.SplashThemeTest'` passes.

## Red Team Review

Reviewers: [Security Adversary](d40a8782-eda6-4dfc-8a43-3e925c46d75b), [Assumption Destroyer](358c6e74-924d-4365-aebe-fc60a6435256). Two phases, so the lenses are security and assumptions. Light tier, fact-check only. Accepted edits are in the phase files.

| # | Finding | Severity | Disposition |
|---|---------|----------|-------------|
| 1 | Night style must restate `parent="Theme.SplashScreen"`, not only the items. Both reviewers. | High | Accept |
| 2 | API 33+ non-launcher starts omit the icon unless `android:windowSplashScreenBehavior` is `icon_preferred`. | High | Accept |
| 3 | External URL is queued during the splash-colored empty window. | High | Reject. `receiveLink` is existing behavior. The settled non-goal is not to hold the splash or change link handling. Soften the phase 2 sentence that says install does not touch intents. |
| 4 | On API 26–30 the icon is inflated with `AppCompatResources` before `setTheme`. The test must inflate `ic_splash_icon`. | High | Accept |
| 5 | An attribute test does not prove the live activity theme. | High | Accept in part. Assert `postSplashScreenTheme` is `Theme.SkimBrowser` and the packed colors. Do not add an instrumented `ACTION_VIEW` gate. |
| 6 | Call order does not control `isNavigationBarContrastEnforced`. Activity 1.13 sets that from `SystemBarStyle.auto`. | Medium | Accept. Rewrite the risk. Do not change `SystemBarsEffect`. |
| 7 | `appcompat-resources` constrains `appcompat` but does not depend on it. | Medium | Reject the extra build rule. Keep the note: do not add `androidx.appcompat:appcompat`, and keep the app's `colorControlNormal`. |
| 8 | Empty `Surface` follows DataStore theme, not the splash. | High | Reject. Settled non-goal: do not keep the splash until settings load. |
| 9 | Scale `1.055` must stay inside splash radius 36, not the launcher safe radius 33. | High | Accept the check. Reject shrinking to radius 33. |
| 10 | `uiMode` in `configChanges` means `setTheme` runs once. | Medium | Reject a code change. The window theme already does not follow a night-mode change mid-process (`AndroidManifest.xml:60`). |

### Whole-Plan Consistency Sweep

Decision delta: night style restates its parent; `values-v33` sets `icon_preferred`; the test inflates the drawable and checks packed colors plus platform attrs at SDK 31; the contrast claim is gone; radius 33 is not the splash target.

Searched the phase files for a night style that only overrides the background, for a contrast fix via call order, and for a radius-33 scale. Those claims remain in this table and in the research notes. Phase 1 steps 4 and 5 and phase 2 steps 1 and 4 match the delta. No unresolved contradiction.

## Validation Log

### Verification Results

- Claims checked: 8
- Verified: 8 | Failed: 0 | Unverified: 0
- Tier: Light
- `MainActivity.kt:70-72` calls `enableEdgeToEdge()` then `super.onCreate()`.
- `themes.xml:10-11` and `values-night/themes.xml:2-3` are full style replacements with different parents.
- `AndroidManifest.xml:52` sets the application theme. The activity has no theme of its own (`AndroidManifest.xml:58-63`).
- `SkimAppViewModel.kt:153` is `START_TIMEOUT_MS = 8_000L`, used twice in `startUp()`.
- `colors.xml` window colors are `#FFFFFF` and `#202124`.

The red-team choice was to apply the accepted fixes. No further open question. The settled non-goals stay: do not keep the splash until settings load, and do not sync it to the DataStore theme.
