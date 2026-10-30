---
phase: 2
title: "Lock the settled frames"
status: pending
priority: P2
effort: "1h"
dependencies: [1]
---

# Phase 2: Lock the settled frames

## Goal

Prove the settled Welcome and Setup boards still match the current goldens, including when Remove animations shows those boards on the first frame.

## Context Links

- `app/src/test/java/com/dantech/skimbrowser/ui/onboarding/OnboardingScreenshotTest.kt`
- `app/src/test/java/com/dantech/skimbrowser/ui/theme/ReducedMotionTest.kt` — sets `Settings.Global.ANIMATOR_DURATION_SCALE` and restores it
- `app/src/test/java/com/dantech/skimbrowser/ui/navigation/ScreenMotionTest.kt`
- `tools/verify-skim/drive.py` — taps Get started, then Start browsing, on a cleared install

## Key Insights

`welcome` and `setupBeforeSkimIsTheDefaultBrowser` call `waitForIdle()` before capturing `01-welcome` and `03-default-browser-import`. Those captures are the settled trees from phase 1. A diff means a piece wrapper is still applied, not that a badge moved.

`welcomeActions` and `setupActions` click as soon as the screen is composed. Those tests are the guard that the buttons sit outside the pieces.

The drive script's `01-welcome.png` is a step screenshot, not a golden. It may show the badges mid-pop. The drive passes when the journey reaches the article.

## Requirements

- Do not update `src/test/screenshots` board PNGs for 01 or 03, or `setup-done-03.png`, as part of making the tests pass.
- Restore `ANIMATOR_DURATION_SCALE` to `1f` after the reduced-motion tests.
- Do not `pm clear` a device a person is using. The drive skill refuses a shared emulator.

## Architecture

Two new tests in `OnboardingScreenshotTest` set the animator scale to `0f` before `setSkimContent`, then capture the existing `01-welcome` and `03-default-browser-import` boards. The default-scale tests stay. A third new test flips `SetupUiState.isDefault` from false to true on a `mutableStateOf`, waits until idle, and asserts "Done" is disabled and "Set as default" is gone.

## Related Code Files

- Modify: `app/src/test/java/com/dantech/skimbrowser/ui/onboarding/OnboardingScreenshotTest.kt`
- Do not modify: production files, unless a golden diff shows the phase 1 handoff still wraps the settled tree. That fix belongs in `OnboardingMotion.kt`, `WelcomeScreen.kt`, or `SetupScreen.kt`, and it does not change spacing, copy, or badge offsets.

## Implementation Steps

1. Add `@After fun restoreAnimations()` that writes `Settings.Global.ANIMATOR_DURATION_SCALE` back to `1f`, following `ReducedMotionTest`.
2. Add a test that sets the scale to `0f`, composes `Welcome()`, waits for idle, calls `captureBoard("01-welcome")`, and asserts accessibility the same way `welcome` does.
3. Add the same reduced-motion capture for `SetupScreen(SetupUiState(isDefault = false), ...)` against `captureBoard("03-default-browser-import")`.
4. Add a test that composes `SetupScreen` with `isDefault` held in `mutableStateOf(false)`, sets it to `true`, waits for idle, then asserts "Done" is not enabled and "Set as default" has count 0. This is the crossfade's resting state, not a new golden.
5. Run the command below. If `01-welcome`, `03-default-browser-import`, or `setup-done-03` diffs, remove the leftover piece wrapper and rerun. Do not record a new board PNG.
6. On a device or emulator this run owns, watch a cleared install. Welcome pops the article badge, the search badge, and the hero Sunny mark in that order, while Get started is already there. Setup slides in and pops the web tile, the bookmarks tile, and the privacy icon in place. The default-browser label crossfades. Start browsing slides to the browser. Turn on Remove animations and confirm both screens are still on the first frame. If `adb devices` already shows a device this run did not boot, skip the drive and use the unit tests plus a device the run owns.

## Todo

- [x] Reduced-motion Welcome captures the existing `01-welcome` board
- [x] Reduced-motion Setup captures the existing `03-default-browser-import` board
- [x] Setup label rest state is "Done" after the flag flips
- [x] Animator scale is restored
- [x] Screenshot and screen-motion tests pass
- [ ] A device this run owns shows the badges, both slides, and the crossfade

Skipped this run: `adb devices` showed a Pixel 6 already attached over USB, which this run did not boot. The drive was not started and the package was not cleared.

## Success Criteria

- `./gradlew :app:testDebugUnitTest --no-configuration-cache --tests 'com.dantech.skimbrowser.ui.onboarding.OnboardingScreenshotTest' --tests 'com.dantech.skimbrowser.ui.navigation.ScreenMotionTest'` exits 0.
- No new or replaced file under `app/src/test/screenshots/` for the onboarding boards.
- With the animator scale at 0, the Welcome and Setup goldens match the scale-1 goldens.

## Risk Assessment

- **Assumption:** Robolectric's compose clock reaches the end of `defaultEffectsSpec` and `defaultSpatialSpec` during `waitForIdle()`, including seven Welcome delays and eight Setup delays. It fails if a golden diffs by a fraction of a pixel while the nodes are already at rest. Response: drop `graphicsLayer` after settle, rerun once. If the diff remains on the bare tree, stop and replan. Do not bump the PNG.
- The drive's first screenshot is not a proof of smoothness. Watching the badges is the visual check. A failed tap on Get started or Start browsing means phase 1 wrapped that button. Unwrap it.

## Security Considerations

The test writes a settings value in the Robolectric process and restores it. Do not change the host device's animator scale from the unit test. The device check only toggles Remove animations on an emulator or device this run owns.

## Next Steps

None. The plan is complete after this phase's command exits 0 and the piece entrance has been seen on a device this run owns.
