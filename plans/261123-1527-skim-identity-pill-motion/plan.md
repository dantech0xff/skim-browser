---
title: "Skim identity, browsing pill and motion"
description: "Give Skim its own look and movement: identity first, then the browsing pill, then motion."
status: in-progress
priority: P1
effort: "46h"
branch: claude/adoring-lovelace-1sstzn
tags: [feature, frontend, android, design]
blockedBy: []
blocks: []
created: 2026-11-23
---

# Skim identity, browsing pill and motion

## Overview

Give Skim its own look and movement: identity first, then the browsing pill, then motion. The app is built well, but it reads as a Chrome variant. Its surfaces and accent are Google's grays and blue, every site is a gray letter, the pill of a page is crowded and has no way to Ask Skim, and the moments a browser repeats most (open a tab, search, switch tabs) have no motion of their own.

Scope challenge: **HOLD SCOPE**. Five phases, because each one ships and can be reverted on its own.

## Decisions

Dan, 2026-11-23:

- Colors: Dan left the choice to the implementer. Chosen: the frame around a web page stays neutral gray, as decided on 2026-10-30. Surfaces Skim draws itself get a lavender tint at the same tones.
- Pill: Back and Forward leave the pill. Back is the system gesture. Forward is not supported.
- Order: identity first, then the pill, then motion.

Dan, 2026-11-24:

- Toolbar: Back and Forward also leave the 64 dp toolbar of Home, Reader, the incognito New tab page and a saved article. Done in `BrowserToolbar`; Forward has no control left in the app.
- Size: the baseline in `size-budget/` moves to the measured size of this work.

## Goals

| # | Goal | Priority |
|---|------|----------|
| 1 | Skim's own surfaces are recognizable next to Chrome, and the frame around a page still matches the page | P1 |
| 2 | A site shows its own icon wherever Skim lists it | P1 |
| 3 | A page has one calm pill with the address and Ask Skim in reach | P1 |
| 4 | Opening a tab, searching and leaving Home move from where they start | P2 |
| 5 | Touch has feedback: haptics, a lifted card, an answer that arrives | P2 |

## Constraints

- `docs/product-vision.md` wins: Material 3 Expressive, Android WebView only, and the Smooth Compose rules.
- No new library. Every phase measures `playReleaseSmoke` against `size-budget/releaseSmoke.json`; growth over 8,192 bytes needs Dan's yes before the baseline moves.
- Skim AI stays on the tertiary roles and the Sunny shape. Primary stays `#0B57D0`, which the launcher icon uses.
- Every motion has a Remove animations branch (`rememberReducedMotion`).
- The live WebView is never scaled or moved per frame. A transition draws the stored thumbnail.
- A deliberate visual change adds a row to the known-deviations table. Goldens are re-recorded per phase, and `:app:verifyRoborazziPlayDebug` passes at the end of each.

## Non-goals

- Blur or glass over the WebView, Lottie, or any motion library.
- Dynamic color, a new accent, a new launcher icon, tablets or landscape.
- Fetching icons for sites the person has not opened.
- Redrawing `design/screens/` or `design/html/`.

## Phases

| # | Phase | Status |
|---|-------|--------|
| 1 | [Identity colors](./phase-01-start.md) | Done |
| 2 | [Site icons](./phase-02-site-icons.md) | Done |
| 3 | [Browsing pill](./phase-03-browsing-pill.md) | Done |
| 4 | [Spatial transitions](./phase-04-spatial-transitions.md) | Pending: scouted, waits for a device |
| 5 | [Touch feedback](./phase-05-touch-feedback.md) | Done |

Phase 5 was built before phase 4, so everything on the branch up to it can be checked and merged without the transitions. Phase 4 is scouted, not built: its open risk is how the swap from a thumbnail to the live WebView looks, and that only shows on a device.

## Success Criteria

- [x] Home, Tabs, Settings, sheets and dialogs draw in the Skim scheme; the pill of a page draws in the chrome scheme, in light and dark.
- [x] A visited site shows its icon on tab cards, history, bookmarks, shortcuts and chats; an incognito icon never reaches the disk. Not yet seen on a device.
- [x] The pill has no Back or Forward and has Ask Skim; when a page scrolls it becomes a compact host chip instead of scaling. Not yet seen on a device.
- [ ] A tab card grows into its page and back, the search field grows from the bar that opened it, and Home hands over to the page.
- [x] Haptics and micro-interactions are in, each with a still branch for Remove animations. Not yet felt on a device.
- [x] Each phase ends with `:app:testPlayDebugUnitTest` and `:app:verifyRoborazziPlayDebug` green and the size measured. Phases 1 to 3 are 4 bytes over the baseline. Phase 5 went 16,388 over, past the budget of 8,192; Dan moved the baseline on 2026-11-24, to 4,412,909 bytes on play and 5,049,908 on full, measured with the toolbar change.
- [ ] Frame time for tabs, sheet and navigation is measured on a device with `FrameBenchmarks` and written down. This needs Dan's phone.

## Open questions

- Forward stays in the tab engine: `TabsController.goForward`, the pool's pending Forward and the scam warning's forward flag in `SkimWebViewClient`. Nothing in the app calls it now. Removing it changes `:domain`, `:browser` and their tests, so it is left for a change of its own if Dan wants it.
- Phase 4 needs a phone or the dedicated AVD in the loop, to look at the transitions and to run `FrameBenchmarks`. Who runs that, and when?

<!-- slug: skim-identity-pill-motion -->
