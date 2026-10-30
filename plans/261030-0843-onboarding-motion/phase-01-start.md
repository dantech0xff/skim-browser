---
phase: 1
title: "Onboarding piece entrance"
status: pending
priority: P2
effort: "3h"
dependencies: []
---

# Phase 1: Onboarding piece entrance

## Goal

Each decorative badge and each text block inside Welcome and Setup eases into the layout that exists today. The primary buttons stay put.

## Context Links

- `app/src/main/java/com/dantech/skimbrowser/ui/onboarding/WelcomeScreen.kt`
- `app/src/main/java/com/dantech/skimbrowser/ui/onboarding/SetupScreen.kt`
- `app/src/main/java/com/dantech/skimbrowser/ui/components/ShapeTiles.kt` — `ShapeArt` and `SunnyMark` draw the badges
- `app/src/main/java/com/dantech/skimbrowser/ui/navigation/SkimNavDisplay.kt` — `slowSpatialSpec` already slides these hops
- `app/src/main/java/com/dantech/skimbrowser/ui/ai/AskPageSheet.kt` — `rememberReducedMotion()` plus `snap` or a motion-scheme fade
- `design/spec/tokens.md` — effects springs do not overshoot; spatial springs may

## Key Insights

The Welcome badges are three shapes in one 300 by 240 box: Cookie9Sided (article) at `(184, 4)`, Clover4Leaf (search) at `(12, 140)`, and the 148 dp Sunny mark at `(76, 46)`. Setup's badges are the 48 dp step tiles: Cookie4Sided for the default browser, Clover4Leaf for import. The privacy row's `ic_verified_user` is the small icon beside that note.

Setup's pieces animate during the screen slide. Their motion stays on scale and a short vertical rise, so it does not fight the horizontal slide. Children compose immediately. A fade does not remove the node, so the existing click tests still find their labels.

## Requirements

- Public parameters of `WelcomeScreen` and `SetupScreen` stay the same.
- Badge offsets, sizes, spacing, copy, colors, and the legal links stay the same.
- The entrance plays once per visit. `rememberSaveable` on each screen holds that it finished, so a rotation does not replay it. Leaving a screen before it finishes and coming back does replay it.
- Remove animations composes the settled tree on the first frame of both screens.
- The outgoing Setup button keeps the look of the state it is leaving and ignores clicks.
- Get started, the legal line, each step's `SkimButton`, and Start browsing are composed with no entrance wrapper.

## Architecture

Create `app/src/main/java/com/dantech/skimbrowser/ui/onboarding/OnboardingMotion.kt`. It owns three private constants: `PieceStaggerMs = 40L`, `PieceRise = 12.dp`, `BadgeScaleFrom = 0.86f`. One internal composable, `OnboardingPiece(index, motion, still, onSettled, content)`, wraps a piece.

`motion` is pop or rise.

- Pop, for a badge or the privacy icon: alpha 0 to 1 and scale `BadgeScaleFrom` to 1, both with `defaultEffectsSpec<Float>()`. Translation stays 0. Transform origin is the center.
- Rise, for a text block: alpha 0 to 1 with `defaultEffectsSpec<Float>()`, translationY `PieceRise` to `0.dp` with `defaultSpatialSpec<Dp>()`. Scale stays 1.

Beat `index` waits `index * PieceStaggerMs` before those animations start. When `still` is true, call `content()` and return, with no `graphicsLayer`. Otherwise the layer is applied and `content()` is still called on the first frame. Ignore a finish callback that arrives before the delay ends. `onSettled` runs only from the last index, and only after every animation on that piece has finished.

Each screen reads `rememberReducedMotion()` once, keeps `var settled by rememberSaveable { mutableStateOf(false) }`, and passes `still = reduced || settled`.

Welcome order, last index 6 calls `onSettled`:

| Index | Piece | Motion |
|-------|--------|--------|
| 0 | Logo `SunnyMark` (36 dp, `brand = true`) | pop |
| 1 | Wordmark | rise |
| 2 | Article badge, Cookie9Sided | pop |
| 3 | Search badge, Clover4Leaf | pop |
| 4 | Hero `SunnyMark`, 148 dp | pop |
| 5 | Headline | rise |
| 6 | Body under the headline | rise |

Apply pop on the same modifier chain as the existing `offset`, with `offset` outside `graphicsLayer`, so each badge scales around its own center inside the 300 by 240 box. Leave `clearAndSetSemantics` on that box.

Setup order, last index 7 calls `onSettled`. The `SkimCard` chrome is not a piece.

| Index | Piece | Motion |
|-------|--------|--------|
| 0 | Setup title | rise |
| 1 | Setup body | rise |
| 2 | Default-browser `StepTile` | pop |
| 3 | That step's title and body | rise |
| 4 | Import `StepTile` | pop |
| 5 | That step's title and body | rise |
| 6 | `ic_verified_user` | pop |
| 7 | Privacy note | rise |

`SetupStep` still crossfades its action. `SetupScreen` passes the reduced-motion boolean in. `AnimatedContent` target is `action to actionEnabled`. The spec is `snap<Float>()` when motion is reduced, otherwise `defaultEffectsSpec()`. The button uses that pair's own label and enabled flag. `onClick` calls `onAction` only for the pair that matches the current arguments.

## Related Code Files

- Create: `app/src/main/java/com/dantech/skimbrowser/ui/onboarding/OnboardingMotion.kt`
- Modify: `app/src/main/java/com/dantech/skimbrowser/ui/onboarding/WelcomeScreen.kt`
- Modify: `app/src/main/java/com/dantech/skimbrowser/ui/onboarding/SetupScreen.kt`
- Do not modify: `ShapeTiles.kt`, `ScreenMotion.kt`, `SkimNavDisplay.kt`, `SkimNavigator.kt`, `SkimButtons.kt`, `design/spec/tokens.md`

## Implementation Steps

1. Add `OnboardingMotion.kt` with the constants and `OnboardingPiece` above. Opt into `ExperimentalMaterial3ExpressiveApi` on the composable that reads `motionScheme`.
2. In `WelcomeScreen`, split `Logo()` so the mark and the wordmark are separate pieces at indexes 0 and 1. Wrap the three shapes in `WelcomeArt` at indexes 2, 3, and 4, and the headline and body at 5 and 6. Leave the bottom column unwrapped. `still` is `rememberReducedMotion() || settled`.
3. In `SetupScreen`, wrap the eight pieces in the table. Pass `still` and the step index base into `SetupStep` (default-browser pieces start at 2, import pieces at 4). Leave each step's `SkimButton` and the Start browsing button unwrapped.
4. Pass `rememberReducedMotion()` into the action `AnimatedContent` described above. Keep the outgoing button's own enabled colors. An empty `onClick` on the outgoing pair is enough.
5. Keep `OnboardingPiece` internal to the onboarding package. Do not add a second motion type, and do not animate a piece on the horizontal axis.

## Todo

- [x] `OnboardingPiece` pops badges and rises text, then the screen drops `graphicsLayer`
- [x] Welcome badges scale in the order article, search, hero
- [x] Setup tiles and the privacy icon pop during the screen slide
- [x] Get started, the step buttons, and Start browsing are outside the pieces
- [x] Setup action crossfades and the outgoing button ignores clicks
- [x] Reduced motion takes the settled branch on the first frame of both screens

## Success Criteria

- A cold Welcome brings in the mark, the wordmark, three badges, the headline, and the body, in that order. Get started is tappable immediately.
- Setup's two tiles and the privacy icon pop in place while the screen slides. Start browsing is tappable immediately.
- After the last piece settles, neither screen keeps an animation modifier on those nodes.
- Turning the default-browser flag from false to true crossfades "Set as default" into a disabled "Done".
- `ScreenMotion.screenTransition` and the badge offsets are unchanged.

## Risk Assessment

- **Assumption:** finish listeners fire at the target value, so swapping to the bare tree does not jump a badge from 0.86 back to 1. It fails if a listener fires early. Response: `onSettled` waits for every animation on the last piece, and only that piece. Do not remove a layer on the first `alpha == 1` sample.
- **Assumption:** `waitForIdle()` advances every 40 ms stagger and both springs, so the goldens are the settled trees. Phase 2 checks 01 and 03. If a golden diffs, the wrapper is still in the tree. Fix the settled handoff. Do not replace the board PNG.
- A `graphicsLayer` alpha of 0 does not remove semantics. The click tests keep finding their labels. Do not add `clearAndSetSemantics` to a piece. `WelcomeArt`'s box already clears the three badges.
- Eight setup indexes plus the screen slide can feel busy if a piece also moves sideways. Pop and rise stay free of `translationX`.

## Security Considerations

No new permissions, storage, or network. The legal `LinkAnnotation`s stay on the unwrapped text. Do not put a click-eating overlay over a button during the entrance.

## Next Steps

Phase 2 locks the settled frames for both screens in `OnboardingScreenshotTest` and reruns `ScreenMotionTest`.
