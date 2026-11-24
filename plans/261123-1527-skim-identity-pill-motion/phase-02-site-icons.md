---
title: "Phase 2: Site icons"
status: done
priority: P1
effort: "8h"
dependencies: [1]
---

# Phase 2: Site icons

## Overview

Show the icon a site sends wherever Skim lists that site. Today every site is a gray letter in a Cookie4Sided tile, so tabs, history, bookmarks and shortcuts look like placeholders. `design/spec/tokens.md` already names the letter tile as the drawing "for shortcuts without an icon", so the letter stays as the fallback.

## Requirements

- [x] The icon comes from the page the person opened: `WebChromeClient.onReceivedIcon`. Skim sends no request of its own for an icon.
- [x] Icons are kept per host. A normal tab's icon is a small file; an incognito tab's icon lives in memory only and goes with the session.
- [x] Clearing history removes the icons, in the place `BrowsingDataCleaner` clears thumbnails.
- [x] `FaviconLetterTile` draws the icon when there is one and the letter when there is not. Shortcut tiles keep their MaterialShapes clip.
- [x] A tile with an icon reads the same for accessibility as it does today.
- [x] Goldens do not change: no fixture has an icon. New goldens cover a tile with an icon, light and dark.

## Related files

- Scout first: `browser/.../SkimChromeClient.kt`, `ThumbnailStore.kt`, `WebViewPool.kt`, `BrowsingDataCleaner.kt`, `app/.../di/ShellEntryPoint.kt`
- Modify: `app/.../ui/components/ShapeTiles.kt` and its ten call sites (tab card, history, bookmarks, Home, Skim history, More menu, site information, large title, segmented list)
- Create: an icon store beside `ThumbnailStore`, with its unit test

## Implementation Steps

1. Scout the thumbnail path end to end and write the icon store on the same pattern: put, observe, delete, clear, incognito in memory.
2. Feed it from `SkimChromeClient.onReceivedIcon`, scaled to the largest size a tile draws.
3. Give `FaviconLetterTile` an optional image and pass it from each call site.
4. Tests: the store (incognito never on disk, clear, eviction), and the tile (icon over letter, letter when the icon is missing).
5. Measure the APK. This phase adds code, so expect more than 8,192 bytes and ask Dan before moving the baseline.

## Todo

- [x] Scout report
- [x] Icon store and tests
- [x] WebView feed
- [x] Tile and call sites
- [x] Goldens for a tile with an icon
- [x] Size measured: the APK file is 4,397,565 bytes, 648 over the baseline, the same as after phase 1. The new code fit in the padding that aligns the native libraries, so the baseline does not move.
- [x] `docs/design-guidelines.md` row, `docs/privacy-stats.md` untouched

## Success Criteria

- A visited site shows its icon on a tab card, in History, Bookmarks, Skim history and a shortcut after its page loads.
- `BrowsingDataCleanerTest` proves the icons go with history, and `IncognitoSessionTest` that they go with the incognito session.
- `:app:testPlayDebugUnitTest`, `:browser:testDebugUnitTest` and `:app:verifyRoborazziPlayDebug` pass.

## Decided after the phase

- Dan, 2026-11-24, from the review of PR 41: Cached images and files also clears the site icons, also while no WebView pool exists. History still clears them too.
