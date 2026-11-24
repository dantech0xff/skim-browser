---
title: "Phase 3: Browsing pill"
status: done
priority: P1
effort: "8h"
dependencies: [1]
---

# Phase 3: Browsing pill

## Overview

Rebuild the pill of a page around the address and Ask Skim. Back and Forward leave it, as Dan decided: Back is the system gesture and Forward is not supported. Ask Skim, hidden since the pill became one row, comes back. When the page scrolls, the pill becomes a compact chip with the host instead of scaling to 0.70, which also shrank the text and the touch targets.

## Requirements

- [x] The pill holds the address field with site information inside it, Reader view, Save video when there is one, Tabs, More options and Ask Skim. No Back, no Forward.
- [x] Ask Skim is the last control of the pill, not a FAB beside it as first outlined: a 56 dp FAB and its gap would take 64 dp from the address, a slot in the pill takes 40 dp. `aiFabMode` decides on or off, as it does for the AI FAB of Home and Reader.
- [x] The buttons outside the field are 40 dp slots, up from 32 dp (22 dp for Back and Forward). Compose widens each touch area to 48 dp where no neighbour is hit, so they pass `assertA11y`; side by side they are 40 dp wide. The outline asked for 48 dp: five 48 dp slots would leave the address 112 dp on a 412 dp screen.
- [x] Collapsed: a chip with the site mark, the host and Ask Skim. A tap brings the pill back, and it stays until the page moves down 32 dp again. Find in page, the keyboard and a covered page keep the full pill, as before.
- [x] The pill's Back and Forward actions and their enabled state leave `BrowsingActions` and `BrowsingUiState`. The test of Back in the tab is now driven by system Back. Four tests whose only subject was a removed button or field are removed. Forward itself stays in the tab engine while the 64 dp toolbar offers it (see the open question).
- [x] `ic_history_back.xml` and `ic_history_forward.xml` are deleted.

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

- [x] Scout report
- [x] Pill without Back and Forward
- [x] AI FAB on the page
- [x] Chip state
- [x] Tests and goldens
- [x] Deviation rows, size measured: the `playReleaseSmoke` file is 4,396,921 bytes, 4 over the baseline (it was 648 over after phase 2).

## Success Criteria

- From a loaded page, one tap opens Ask about this page.
- `A11yAssertions` passes on the pill without an exception for small targets.
- `:app:testPlayDebugUnitTest` and `:app:verifyRoborazziPlayDebug` pass.

## Not seen on a device

- The timing of the morph between the pill and the chip.
- The lock's touch area on a short host: it is widened to 48 dp inside the field, so on a host like `t.co` it reaches the first letters.
- TalkBack's order inside the field.

## Decided after the phase

- Dan, 2026-11-24: Back and Forward also leave the 64 dp toolbar of Home, Reader, the incognito New tab page and a saved article. In Reader view system Back leaves Reader; a second Back goes back in the page. Forward has no control left; it stays in the tab engine (see the plan's open questions).
