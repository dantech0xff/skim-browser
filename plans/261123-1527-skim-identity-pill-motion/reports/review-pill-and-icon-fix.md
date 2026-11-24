# Review: d295f9f (icon cache count) and bd27bc2 (browsing pill)

Read-only review. No Gradle run (build in use). Evidence: `git show`, source reads.

## Findings

### 1. Medium: a tile's remembered flow is orphaned once its cache entry is evicted
- `app/src/main/java/com/dantech/skimbrowser/ui/components/SiteIcons.kt:34` with
  `browser/src/main/java/com/dantech/skimbrowser/browser/SiteIconStore.kt:102,127,150`
- The flow lives inside the LRU `Entry`. After `remember(icons, host, incognito)` a composed tile
  keeps the first `Entry.flow` for as long as it stays composed. Before this commit every
  recomposition called `observe`, which re-fetched the current entry and touched its LRU recency.
- Scenario: a tile for host A is composed (a shortcut, a tab tile, a list row still on screen) and
  gets no further `get`. Other hosts' icons fill the cache (4 MB is about 64 icons at 128 px ARGB,
  and 1 MB is about 16 for incognito), so A's entry is evicted. Then:
  (a) A sends an icon. `put` makes a new `Entry`, and the tile keeps its letter until it leaves
  composition.
  (b) Clear history. `clearAll` nulls only the flows in `snapshot()`, so the tile keeps the
  cleared icon on screen.
- Raising `MAX_ICON_PX` from 96 to 128 cuts the cache's entry capacity by about 44%, so eviction
  comes sooner.
- Smallest fix: keep the per-host `MutableStateFlow`s in a plain map outside the LRU (they are
  tiny). Let the LRU own only the image bytes. `clearAll` / `clearIncognito` null every flow in
  that map. Add a test: observe, evict through a small `cacheBytes`, then put or clear, and assert
  the first flow follows.

### 2. Medium: the lock inside the address field takes taps meant for editing
- `app/src/main/java/com/dantech/skimbrowser/ui/components/AddressBar.kt:211-242`
- The lock is a 28 dp `clickable` nested inside the field's `clickable`, with 2 dp before the host
  text. Compose widens a pointer target smaller than 48 dp where no other node is hit directly
  (phase-03 relies on this widening for `assertA11y`). The text has no pointer input, so the
  lock's widened area covers about 10 dp of the host text and the full 36 dp height of the field.
  In the Main pass the lock consumes the tap first, so the field's click never fires.
- Scenario: on a short host ("x.com") the lock and the host are centered together. A tap on the
  start of the host, or just left of the field's center, opens Site information instead of
  editing the address. The tests hide this: `TouchHelpers.clickNearEnd` exists because a plain
  click on the field lands on the lock (the KDoc says so).
- Smallest fix: separate the lock from the text by at least 10 dp of non-text padding, or clip
  its target with an explicit 48 dp `Box` that holds the lock and no text. Add a test that taps
  the first character of the host and expects `onEditAddress`.

### 3. Low-medium: a tap on the chip during a fling is undone within 32 dp
- `app/src/main/java/com/dantech/skimbrowser/ui/browsing/BrowsingRoute.kt:140,172-174`,
  `BrowsingChromeScroll.kt:24-41`
- `onExpandChrome` sets `chromeCollapsed = false`. A tap on the Compose chip does not stop the
  WebView's fling, which keeps sending `dy > 0`. With `collapsed == false`, `run` restarts at the
  first down `dy` and reaches 32 dp in one or two frames. Then the pill collapses again.
- Scenario: fling down a long article, then tap the chip while the page still moves. The pill
  appears and turns back into the chip almost at once. This breaks the commit's claim that the
  pill "stays until the page moves down 32 dp again": a fling moves 32 dp in a frame.
- Smallest fix: add `BrowsingChromeScroll.onExpanded()`. It holds collapse until a `dy <= 0`
  event or a new gesture, or it resets `run` and ignores down moves for a short window. Call it
  from `onExpandChrome`. Unit-test it with a series of down `dy` values after expand.

### 4. Low: Ask Skim's widened target in the chip covers the end of the host text
- `app/src/main/java/com/dantech/skimbrowser/ui/browsing/BrowsingChrome.kt:244,257,264`
- Same mechanism as finding 2. A 36 dp nested `clickable` inside the chip's `clickable` widens to
  48 dp, about 6 dp into the host text (which has 4 dp end padding).
- Scenario: a tap on the last letters of the host opens Ask Skim instead of bringing the pill
  back. Ask Skim then navigates away.
- Fix: give the Ask Skim slot 48 dp, or put 8 dp or more between the text and the slot.

## Categories with no finding
- SiteIconStore `resize` accounting. Correct: `remove`, then mutate, then `put` keeps
  `LruCache.size` equal to the sum of `sizeOf`. `clearIncognito` nulls `image` but leaves `bytes`
  alone before `evictAll`, so the count stays consistent. The new tests fail on the old code
  (`evictAll` throws, and the old in-place `put` left the 3-host test with no eviction).
- File order against memory. Writes and deletes are now posted under `lock` onto a fair-mutex
  `SerialWriter`. A read posted outside the lock is still made safe by the identity and version
  check at `SiteIconStore.kt:196`. Posting from inside `synchronized` does not deadlock, because
  the block runs after `withContext(ioDispatcher)` and the monitor is reentrant.
- AnimatedContent morph. `showsChip` is false while Find is open, the IME is up or the page is
  covered, so Find's field and its focus are never disposed by the switch. Reversal mid-animation
  is handled by `AnimatedContent`/`SizeTransform`. `chromeCollapsed` is written only when it
  flips, so a scroll event that does not flip it costs no recomposition.
- System Back. The cases match the removed pill Back. GoBack, CloseAndLeave, CloseAndReturn and
  NewTabPage go through the `BrowserRoute` handler. Find, the certificate warning and the scam
  warning go through the `BrowsingRoute` handlers, which run first. On the first page of a plain
  tab the handler is disabled, as the pill Back was. Forward was removed on purpose, as the user
  decided (phase-03 line 13, plan.md line 27). Not a defect.
- Disabled Ask Skim has no click action and is announced as a disabled button. The Tabs count
  text is `clearAndSetSemantics`, so it is not read twice. The Error mark in the field is its own
  node, so the field's label stays the address.
- BrowsingChromeScroll flip-flop from layout. The chip does not change `contentBottom`, so there
  is no resize-and-scroll feedback loop. No stuck state found beyond finding 3.
- Tests. `aTileAsksForItsIconOnceWhileItsSiteStaysTheSame` and the cache-count tests prove their
  names. `askSkimInThePillAsksAboutThisPageInOneTap` accepts either `AiPermissionKey` or
  `AskPageKey`. That is weak but justified by the gate. No test covers findings 1 to 3.
  `withRemoveAnimations...` writes `ANIMATOR_DURATION_SCALE` globally. Robolectric resets
  `Settings` per test, so this is not a leak.

## Unresolved questions
1. Is finding 2's widened area confirmed on a device? The reasoning comes from Compose's
   minimum-touch-target hit testing (`isHitInMinimumTouchTargetBetter`). A connected or Paparazzi
   check was not run.
2. When an icon is evicted while a tile is on screen, should that tile keep showing it?
   `SiteIconStore`'s KDoc says a handed-out flow keeps its value. The question is whether that
   should also hold after `clearAll`.
3. 96 px icons already on disk are kept and not re-fetched at 128 px. Is that acceptable until the
   site sends its icon again?

## Follow-up

- **Finding 1, not changed.** The cache pushes out the least recently asked for first, and a tile asks when it is composed. For the entry of a tile on screen to go, everything asked for before it must be gone and the icons asked for since must still overflow the cache: at least 64 large icons in normal mode (15 in incognito) while that one tile stays on screen. No screen composes that many tiles around one that stays. The invariant is now written on `siteIcon`. A screen that pins a tile above a long list of other sites would break it and needs a second look then.
- **Finding 2, fixed.** The whole field is the edit button and the lock is drawn over it as a sibling, so a tap beside the lock is a direct hit on the field. `aTapJustPastTheLockOpensTheAddressNotSiteInformation` fails without the change. No golden moves.
- **Finding 3, fixed.** `BrowsingChromeScroll.onExpandedByTap` holds the pill: moves down do not count until the page has been still for 150 ms or has moved up. Three tests with a clock cover the fling, a move up and a tap on a page at rest.
- **Finding 4, fixed.** Ask Skim is drawn over the end of the chip's row as a sibling. `aTapJustBeforeTheChipsMarkBringsThePillBackNotAskSkim` fails without the change.
- **Question 3.** Icons were kept at 96 px for one commit of this branch, so no installed app has such files; a site sends its icon again on its next load.

