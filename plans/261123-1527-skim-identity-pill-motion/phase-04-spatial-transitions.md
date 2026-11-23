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

## Scout notes

Read from the code on 2026-11-23. No spike was built: frames drawn under Robolectric show where things are, but not whether the swap from an image to the live WebView is visible, and that swap is the open risk.

- The pinned Navigation 3 (1.2.0) has what the tab transition needs: one `NavDisplay` overload takes a `SharedTransitionScope`, and an entry reads `LocalNavAnimatedContentScope`. No new library.
- Tabs is pushed over the browser (`SkimNavigator.openTabs`). A card opens its tab with `showTab`, which selects the tab and pops back to the browser. So "the card grows into the page" is the pop from `TabsKey` to `BrowserKey`, and "the page shrinks into its card" is the push.
- A thumbnail is not a picture of the page as the person left it. It is the top of the view, cut at 168:92 and drawn at 0.35 scale (`ThumbnailCapture.kt`). It is taken after a page loads and when the WebView leaves its host, and the WebView leaves its host after the push transition ends.
  - At the start of "the page shrinks into its card" the stored thumbnail can show another scroll position.
  - At the end of "the card grows into the page" the image covers only the top of the screen and is soft.
  - Two ways out: take a full-height snapshot when the transition starts (a software draw of the WebView on the main thread, to be timed on a device), or grow the cut thumbnail and fade the page in under it. Either has to be looked at on a device.
- The browser entry's content is composed from the first frame of the pop. `WebViewHost` has to wait for the transition to settle before it attaches the WebView, or the live view moves with the entry.
- Home and a page are one entry: `BrowserRoute` switches on `tab.url`. To keep the WebView still, Home fades out over the page. The page is not the layer that moves or fades.
- The search layer is a `Dialog` that scales from 0.92 (`SearchOverlay.kt`). The bounds it starts from have to come from the Home search bar or the pill's address field, in window coordinates.

## Todo

- [x] Scout notes (above). The spike waits for a device.
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
