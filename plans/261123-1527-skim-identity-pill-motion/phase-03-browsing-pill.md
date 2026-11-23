---
title: "Phase 3: Browsing pill"
status: todo
priority: P1
effort: "8h"
dependencies: [1]
---

# Phase 3: Browsing pill

## Overview

Rebuild the pill of a page around the address and Ask Skim. Back and Forward leave it, as Dan decided: Back is the system gesture and Forward is not supported. Ask Skim, hidden since the pill became one row, comes back. When the page scrolls, the pill becomes a compact chip with the host instead of scaling to 0.70, which also shrank the text and the touch targets.

## Requirements

- [ ] The pill holds site information, the address, Reader view, Save video when there is one, Tabs and More options. No Back, no Forward.
- [ ] Ask Skim is on the page as the AI FAB beside the pill, the place it has beside the toolbar of Home and Reader. `aiFabMode` decides on or off, as it does there.
- [ ] Each control has a 48 dp touch target.
- [ ] Collapsed: a chip with the lock and the host. A tap brings the pill back. Find in page, the keyboard and a covered page keep the full pill, as today.
- [ ] The pill's Back and Forward actions and their enabled state leave `BrowsingActions` and `BrowsingUiState`. Tests that used them are rewritten for system Back, not deleted. Forward itself stays in the tab engine while the 64 dp toolbar offers it (see the open question).
- [ ] `ic_history_back.xml` and `ic_history_forward.xml` are deleted.

## Related files

- Modify: `app/.../ui/browsing/BrowsingChrome.kt`, `BrowsingScreen.kt`, `BrowsingRoute.kt`, `BrowsingViewModel.kt`, `BrowsingChromeScroll.kt`
- Modify: `app/.../ui/components/AddressBar.kt`, `FloatingToolbars.kt` (metrics)
- Modify: `app/src/main/res/values/strings.xml`, `docs/design-guidelines.md` (rows for boards 05, 05c, 11)
- Scout first: tests that drive Back or Forward through the pill, `A11yAssertions.kt`, the snackbar placement above the pill

## Implementation Steps

1. Scout the tests and the snackbar offsets that depend on the pill's size.
2. Remove Back and Forward from the pill. System Back keeps `browserBack`.
3. Place the AI FAB beside the pill and wire `onAskPage`, which `BrowsingRoute` already passes.
4. Replace the scale with two drawn states, full and chip, and one spring between them. Remove animations snaps.
5. Tests: the pill's controls and targets, Ask Skim opens the sheet and is off in incognito and on a failed page, the chip expands on tap and at the top of the page.
6. Re-record the goldens of 05, 05c, 11 and the dialogs over a page. Update the deviation rows.

## Todo

- [ ] Scout report
- [ ] Pill without Back and Forward
- [ ] AI FAB on the page
- [ ] Chip state
- [ ] Tests and goldens
- [ ] Deviation rows, size measured

## Success Criteria

- From a loaded page, one tap opens Ask about this page.
- `A11yAssertions` passes on the pill without an exception for small targets.
- `:app:testPlayDebugUnitTest` and `:app:verifyRoborazziPlayDebug` pass.

## Open question

- The 64 dp toolbar of Home, Reader and the incognito New tab page still has Back and Forward. On Home both are always off. Removing them there is not in this phase until Dan says so.
