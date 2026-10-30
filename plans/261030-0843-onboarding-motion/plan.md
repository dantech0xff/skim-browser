---
title: "Smooth onboarding motion"
description: "Stagger the pieces inside Welcome and Setup, including each shape badge, and crossfade the Setup action when Skim becomes the default browser."
status: pending
priority: P2
effort: 4h
issue:
branch: master
tags: [feature, frontend, android]
blockedBy: []
blocks: []
created: 2026-10-30
---

# Smooth onboarding motion

## Overview

Welcome (01) and Setup (03) appear in their final layout. The hops already slide through `screenTransition` and `slowSpatialSpec`. This plan staggers the pieces inside each screen: the logo, each shape badge, the headlines and the supporting lines. Get started and Start browsing stay in place on the first frame. Scope challenge: **HOLD SCOPE**, plus the follow-up that the pieces inside the screens animate too. Mode: fast.

## Outcome

On a fresh launch the Welcome pieces arrive in order: the brand mark, the wordmark, the article badge, the search badge, the hero Sunny badge, the headline, then the body. Get started and the legal line are already there. Setup slides in as it does today, and inside that slide the title, the body, each step badge, each step's text, and the privacy row arrive in place. When Skim becomes the default browser, "Set as default" crossfades to a disabled "Done". With Remove animations on, both screens are the still layout and the label snaps.

## Constraints

- Badge pop: alpha and scale use `defaultEffectsSpec` (no overshoot). Text rise: alpha uses `defaultEffectsSpec`, the 12 dp rise uses `defaultSpatialSpec`.
- The theme already sets `MotionScheme.expressive()`.
- `rememberReducedMotion()` is the Remove animations check (`ANIMATOR_DURATION_SCALE == 0`).
- Board goldens for 01 and 03 stay. After the last piece settles, the tree matches today's layout, with no leftover `graphicsLayer`.
- `welcomeActions` clicks Get started, and `setupActions` clicks Set as default, Choose file, and Start browsing, as soon as the screen is composed.

## Non-goals

- Changing `screenTransition`, `SkimNavDisplay`, `finishOnboarding`, or `ShapeArt`.
- A horizontal move on a piece. The screen slide already owns that axis.
- Looping the badges, a splash animation, or a new motion token.
- A shared-element transition into the browser.

## Acceptance criteria

- Each Welcome badge and each Setup step tile eases in on its own, in the order below.
- Text blocks rise on their own. Card chrome, Get started, and Start browsing do not.
- Setup's action crossfades. The outgoing control does not receive the click.
- Remove animations shows both settled boards and snaps the label.
- `OnboardingScreenshotTest` and `ScreenMotionTest` pass without new board goldens.

## Phases

| Phase | Name | Status |
|-------|------|--------|
| 1 | [Onboarding piece entrance](./phase-01-start.md) | Pending |
| 2 | [Lock the settled frames](./phase-02-verify.md) | Pending |

## Success Criteria

- [x] Phase 1 keeps the public `WelcomeScreen` and `SetupScreen` parameters, the badge offsets, and the current spacing.
- [x] Phase 2's unit tests pass, including the existing board captures at the default animator scale and at scale 0.

## Dependencies

- `androidx.compose.animation`, already used by `AskPageSheet`.
- `rememberReducedMotion` in `app/src/main/java/com/dantech/skimbrowser/ui/theme/ReducedMotion.kt`.
- `ShapeArt` and `SunnyMark` in `app/src/main/java/com/dantech/skimbrowser/ui/components/ShapeTiles.kt`. The entrance wraps them. It does not change them.
