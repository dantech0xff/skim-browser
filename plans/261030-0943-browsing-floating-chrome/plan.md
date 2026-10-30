---
title: "Browsing floating chrome"
description: "Replace the browsing screen's top address band and bottom toolbar with one floating cluster so the WebView draws edge to edge."
status: pending
priority: P1
effort: 7h
branch: master
tags: [android, ui, browsing, feature]
blockedBy: []
blocks: []
created: 2026-10-30
---

# Browsing floating chrome

## Overview

On the browsing screen the address bar is a solid top band and the toolbar floats on top of whatever page height is left. This plan removes that band. The WebView fills the screen, under the status bar and the navigation bar. Address, find, and the existing toolbar buttons sit in one floating cluster anchored to the bottom.

Scope challenge: **HOLD SCOPE**. The top band and the bottom toolbar become one bottom cluster. Home, Reader, Tabs, and the incognito New tab page keep the toolbar they have.

Research: [insets](./research/researcher-01-insets.md), [chrome](./research/researcher-02-chrome.md). Scout: [report](./reports/scout-report.md). Subagents did not start (usage limit); those notes are from direct reads.

## Outcome

A loaded page, find in page, and the load-error screen show no top address band. The page view reaches the screen edges. One rounded cluster at the bottom holds the address or the find field, then Back, Forward, Tabs, and More, with the AI FAB beside the icon row.

## Constraints

- Touch targets stay at least 48 dp (`A11yAssertions.kt`). A single 64 dp row cannot hold the address and the four icons.
- `BrowserToolbar`'s drawn metrics stay. `ToolbarScreenshotTest` and `BarsScreenshotTest` are not re-recorded.
- `WebViewHost` stays `fillMaxSize`. A wrap-content WebView lays the page out at height 0 (`WebViewPool.kt`).
- `BrowserKey` is both the New tab page and a loaded page. Snackbar clearance grows only while a page is showing and no sheet or dialog is on top.
- Design PNGs in `design/screens/` and the HTML boards stay as drawn. The app's Roborazzi goldens and `docs/design-guidelines.md` record the new chrome. `designParity` is not a gate.

## Non-goals

- Auto-hiding the cluster on scroll, a status-bar scrim, or injecting CSS `safe-area` into the page.
- Changing `SystemBarsEffect`, `FullscreenController`, or `WebViewHost`.
- Restyling Home, Reader, Tabs, or the incognito New tab page.
- Redrawing `design/html/` or `design/screens/`.

## Acceptance criteria

- The page box has no status-bar spacer and no top address frame. At rest it is `fillMaxSize`.
- Site info, edit address, Reader, wavy progress, find, Back, Forward, Tabs, More, and the AI FAB still do what they do now.
- Find still replaces the address row and still takes focus. The cluster sits above the IME. The page box shortens only while the IME is visible, by the IME inset plus the cluster.
- The pull-to-refresh indicator clears the status bar. Load-error content clears the status bar and the cluster.
- A snackbar on a page with no overlay clears the cluster. A snackbar on the New tab page, Reader, or over a sheet stays on `AboveToolbar`.
- `ToolbarScreenshotTest` and `BarsScreenshotTest` pass without new goldens.

## Dependencies

- Existing `ToolbarMetrics`, `AddressBar`, `FindInPageBar`, `BrowserToolbar`, `AiFab`.
- `TabsState.currentTab` (`domain/.../tab/Tab.kt`) and `tab.url == null` as the New tab page (`BrowserRoute.kt`).

## Phases

| Phase | Name | Status |
|-------|------|--------|
| 1 | [Edge-to-edge page and bottom cluster](./phase-01-start.md) | Pending |
| 2 | [Snackbar, spec, and goldens](./phase-02-verify.md) | Pending |

## Success Criteria

- [x] Phase 1 leaves the WebView full bleed and moves address and find into the bottom cluster.
- [x] Phase 2 keeps snackbars off that cluster on a page, updates the written spec, and re-records only the goldens whose backdrop changed.

## Red Team Review

Review was done in this session. Reviewer subagents did not start (usage limit). Two phases, so the lenses are Security and Assumptions, Light tier, fact-check only. Findings without a `file:line` were not kept.

| # | Finding | Severity | Disposition | Applied To |
|---|---------|----------|-------------|------------|
| 1 | Stacking `navigationBarsPadding()` and `imePadding()` lifts the cluster by both insets while the keyboard is open. | High | Accept | Phase 1 |
| 2 | Growing `SnackbarPlacement.AboveToolbar` also lifts snackbars on Home, Reader, and Tabs. `BrowserKey` covers both the New tab page and a page (`SkimKeys.kt:43-47`). | High | Accept | Phase 2 |
| 3 | Extracting the toolbar buttons can move the FAB. `ToolbarScreenshotTest` must pass with the old golden. | High | Accept | Phase 1 |
| 4 | `PageBehind` (`OverlaysScreenshotTest.kt:304`) is a second copy of the address bar. Leaving it in place makes sheet goldens lie. | High | Accept | Phase 2 |
| 5 | A wrap-content WebView lays the page out at height 0 (`WebViewPool.kt:388-390`). | High | Accept | Phase 1 |
| 6 | Putting the address into the 64 dp row fails the 48 dp touch rule (`A11yAssertions.kt:12`). | High | Reject | The cluster is two rows. The request is one floating bar, which this card is. |
| 7 | A status-bar scrim would keep icons readable on a dark page. | Medium | Reject | The request is a full-bleed page. `SystemBarsEffect.kt:26` stays. |
| 8 | Injecting CSS safe-area into the WebView would pad every site. | Medium | Reject | Non-goal. Pages already scroll under the toolbar (`navigation-map.md:22`). |

### Whole-Plan Consistency Sweep

Decision delta: one bottom `Surface` plus the existing FAB; WebView full bleed at rest; IME uses `WindowInsets.union`; snackbar offset grows only for a page with no overlay; `BrowserToolbar` visuals stay; `PageBehind` calls the new cluster.

Searched the plan files for a top address band, stacked `imePadding()`, a taller `AboveToolbar`, a 64 dp single row, and a CSS safe-area inset. None of those remain as instructions. Phase 2 lists the same golden classes as the acceptance criteria above.

## Validation Log

### Verification Results

- Claims checked: 10
- Verified: 10 | Failed: 0 | Unverified: 0
- Tier: Light

| Claim | Result |
|-------|--------|
| `BrowsingScreen` column, status spacer, address or find, `imePadding` on the page, toolbar overlay | Verified `BrowsingScreen.kt:86-157` |
| `WebViewHost(..., Modifier.fillMaxSize())` | Verified `BrowsingRoute.kt:93` |
| Wrap-content WebView height 0 | Verified `WebViewPool.kt:388-390` |
| `BrowserToolbar` is shared; Home and Reader call it | Verified `NewTabPageRoute.kt:61`, `ReaderScreen.kt:141` |
| Address production caller is `BrowsingScreen` | Verified by search for `AddressBar(` |
| `AboveToolbar` formula | Verified `SnackbarController.kt:59-60` |
| Snackbar host reads `topScreen(keys)?.placement` only in `SkimApp` | Verified `SkimApp.kt:88` |
| `tab.url == null` selects the New tab page | Verified `BrowserRoute.kt:99` |
| `TabsState.currentTab` | Verified `Tab.kt:63` |
| `PageBehind` draws `AddressBar` under a status-bar pad | Verified `OverlaysScreenshotTest.kt:311-326` |

No user interview. The request fixes the layout: one bottom cluster, WebView edge to edge. The 48 dp rule fixes the shape as two rows inside that cluster. A status-bar scrim was rejected because the page is full bleed.

### Whole-Plan Consistency Sweep

Re-read `plan.md`, `phase-01-start.md`, and `phase-02-verify.md` after the red-team notes. Cluster height is 56 + 64 dp everywhere it is stated. `SnackbarPlacement.AboveToolbar` is unchanged. `LoadErrorContent`'s private `AboveToolbar` grows by the address row, which is a different symbol. The golden lists in the overview and phase 2 match. No unresolved contradiction.
