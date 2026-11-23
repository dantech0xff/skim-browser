---
title: "Phase 4: Spatial transitions"
status: todo
priority: P2
effort: "16h"
dependencies: [3]
---

# Phase 4: Spatial transitions

## Overview

Make the three moves a browser repeats most start from where the finger was. Today every screen change is the same horizontal slide (`ScreenMotion.kt`), Home becomes a page in one frame (`BrowserRoute.kt`), and the search layer scales from 0.92 and vanishes when it closes (`SearchOverlay.kt`).

## Requirements

- [ ] Tabs to page: the tapped card grows into the page, and Back to Tabs shrinks the page into its card. The moving image is the thumbnail in `ThumbnailStore`; the live WebView appears when the move ends. A tab without a thumbnail, and an incognito tab under FLAG_SECURE, fall back to a fade.
- [ ] Search: the layer's field starts at the bounds of the bar that opened it (the Home search bar or the pill's address) and returns there when it closes. The layer is a dialog window, so the start bounds are passed in; this is not a shared element.
- [ ] Home to page: the page fades and rises in over Home when the tab gets a URL, and Home returns the same way.
- [ ] Other screens keep the slide of `screenTransition`. Sheets and dialogs keep their own motion.
- [ ] Predictive back drives the tab card transition with the gesture.
- [ ] Remove animations: every change above is instant.
- [ ] `SkimNavigator` stays the only writer of the back stack. No `NavHost`.

## Related files

- Scout first: `SharedTransitionLayout` with Navigation 3 in the versions pinned in `gradle/libs.versions.toml`, `BottomSheetSceneStrategy.kt`, `SecureFlagEffect.kt`, how `WebViewHost` attaches a tab's view
- Modify: `app/.../ui/navigation/SkimNavDisplay.kt`, `ScreenMotion.kt`, `ui/tabs/TabsScreen.kt`, `ui/components/TabCard.kt`, `ui/browser/BrowserRoute.kt`, `ui/search/SearchOverlay.kt`
- Tests: `ScreenMotionTest` and new specs beside it

## Implementation Steps

1. Research and scout: confirm the shared-bounds API against the pinned Compose and Navigation 3, with a throwaway spike that is not committed.
2. Tabs and page first, behind the reduced-motion branch from the start.
3. Search field bounds next, then Home to page.
4. Measure the APK. Shared transitions keep more of Compose animation after R8.
5. Device check, owed by Dan: `FrameBenchmarks.navigationAndTabsAndSheet` before and after on a mid-range phone, numbers written into `docs/`.

## Todo

- [ ] Scout and spike report
- [ ] Tab card and page
- [ ] Search field
- [ ] Home and page
- [ ] Tests, goldens of settled frames unchanged
- [ ] Size measured
- [ ] Frame time on a device (Dan)

## Success Criteria

- Settled frames match today's goldens: the transitions leave no layer behind.
- No frame draws a scaled WebView: a test asserts the WebView host is composed only at rest.
- `:app:testPlayDebugUnitTest` and `:app:verifyRoborazziPlayDebug` pass.

## Risk

- Robolectric cannot show whether a transition is smooth. Until the device numbers exist, this phase is unproven against the Smooth Compose rule and stays off `master`.
