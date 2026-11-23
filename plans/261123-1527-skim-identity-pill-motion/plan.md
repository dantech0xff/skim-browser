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
| 2 | [Site icons](./phase-02-site-icons.md) | Pending |
| 3 | [Browsing pill](./phase-03-browsing-pill.md) | Pending |
| 4 | [Spatial transitions](./phase-04-spatial-transitions.md) | Pending |
| 5 | [Touch feedback](./phase-05-touch-feedback.md) | Pending |

Phases 2 to 5 are outlines. Each gets its own scout pass before it starts.

## Success Criteria

- [x] Home, Tabs, Settings, sheets and dialogs draw in the Skim scheme; the pill of a page draws in the chrome scheme, in light and dark.
- [ ] A visited site shows its icon on tab cards, history, bookmarks, shortcuts and chats; an incognito icon never reaches the disk.
- [ ] The pill has no Back or Forward and has Ask Skim; when a page scrolls it becomes a compact host chip instead of scaling.
- [ ] A tab card grows into its page and back, the search field grows from the bar that opened it, and Home hands over to the page.
- [ ] Haptics and micro-interactions are in, each with a still branch for Remove animations.
- [ ] Each phase ends with `:app:testPlayDebugUnitTest` and `:app:verifyRoborazziPlayDebug` green and the size measured.
- [ ] Frame time for tabs, sheet and navigation is measured on a device with `FrameBenchmarks` and written down. This needs Dan's phone.

## Open questions

- The 64 dp toolbar of Home, Reader and the incognito New tab page also has Back and Forward. Should they leave with the pill's? On Home both are always off.

<!-- slug: skim-identity-pill-motion -->
