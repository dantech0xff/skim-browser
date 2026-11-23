---
title: "Phase 1: Identity colors"
status: todo
priority: P1
effort: "6h"
dependencies: []
---

# Phase 1: Identity colors

## Overview

Split the one neutral scheme into two. The chrome scheme keeps today's grays for what floats over a web page. The Skim scheme tints every surface Skim draws itself, at the tones it has today, so text contrast does not move. The dark Skim scheme also gets a `surfaceContainerHigh` of its own; today it equals `surfaceContainer`.

## Key decisions

- Hue: HCT 283, the hue of the surfaces on the design boards (`#F8F5FF`). It sits between the blue accent (270) and the AI pink (335).
- Chroma: 6 to 17 in light and 7 to 14 in dark. The boards used 7 to 28, which is what clashed with dark pages. Google's grays are 3 to 5.
- Tone: unchanged per role. `onSurface` on `surface` stays about 16:1 in light and 13:1 in dark.
- Light `surface` stays `#FFFFFF`, so Home and Reader keep a white page and the window color does not change. Dark `surface` becomes `#212127`.
- Unchanged in both schemes: primary, tertiary, error, scrim, `surfaceContainerLowest`.

| Role | Light | Dark |
|---|---|---|
| surface | `#FFFFFF` | `#212127` |
| surfaceContainerLow | `#FBF8FF` | `#292933` |
| surfaceContainer | `#F4F1FE` | `#30303B` |
| surfaceContainerHigh | `#EAE8FC` | `#373743` |
| surfaceContainerHighest | `#DADAF4` | `#3E3E4C` |
| secondaryContainer | `#E9E8FF` | `#3E3E4F` |
| onSurface | `#1F202F` | `#ECE9EF` |
| onSurfaceVariant | `#616170` | `#9F9EAA` |
| outline | `#767582` | `#908F9B` |
| outlineVariant | `#C7C5D3` | `#464550` |
| inverseSurface | `#30303B` | `#ECE9EF` |

The chrome zone is the browsing pill with Find in page, the translation bar above it, and the caption of a tab swipe on a page. Sheets, dialogs, the search layer, the error and warning pages, Reader, and the 64 dp toolbar of Home, Reader and Tabs are Skim surfaces.

## Requirements

- [ ] `SkimLightColors` and `SkimDarkColors` are the Skim scheme. `SkimChromeLightColors` and `SkimChromeDarkColors` hold today's values.
- [ ] `SkimChromeTheme` swaps only the color scheme and follows the light or dark of the theme around it.
- [ ] `BrowsingChrome` and `PageTranslationBar` apply the chrome scheme themselves, so a screenshot of either alone matches the app.
- [ ] `skim_window_dark` equals the dark `surface`.
- [ ] The pill goldens do not change. Every other changed golden differs in color only.

## Related files

- Modify: `app/src/main/java/com/dantech/skimbrowser/ui/theme/Color.kt`, `Theme.kt`
- Modify: `app/src/main/java/com/dantech/skimbrowser/ui/browsing/BrowsingChrome.kt`, `PageTranslationBar.kt`, `BrowsingScreen.kt`
- Modify: `app/src/main/res/values/colors.xml`
- Modify: `app/src/test/java/com/dantech/skimbrowser/ui/theme/SkimThemeTest.kt`
- Modify: `design/spec/tokens.md`, `design/spec/tokens.json`, `docs/design-guidelines.md`
- Re-record: goldens under `app/src/test/screenshots/` that draw a Skim surface

## Implementation Steps

1. Write both schemes in `Color.kt`. The Skim schemes are a `copy` of the chrome ones, so only the tinted roles are listed.
2. Add `SkimChromeTheme` to `Theme.kt` and wrap the pill, the translation bar, and the tab swipe of a page.
3. Set `skim_window_dark` to `#212127`.
4. Tests first in `SkimThemeTest`: the chrome theme gives the chrome scheme inside light, dark and incognito; type and motion are the ones outside it; the Skim and chrome schemes share primary and tertiary; each tinted role keeps the luminance of its chrome role within a small tolerance.
5. Record all goldens. Check with a pixel diff that the pill goldens are byte-identical and that the others moved in color only.
6. Update `tokens.md` and `tokens.json` (two schemes, the roles above), the Token section of `docs/design-guidelines.md`, and add one row to the known-deviations table.

## Todo

- [ ] Schemes and `SkimChromeTheme`
- [ ] Chrome zone wrapped in the three places
- [ ] Window color
- [ ] Theme tests
- [ ] Goldens re-recorded and diffed
- [ ] `tokens.md`, `tokens.json`, `design-guidelines.md`
- [ ] Size measured against `size-budget/releaseSmoke.json`

## Success Criteria

- `:app:testPlayDebugUnitTest` and `:app:verifyRoborazziPlayDebug` pass.
- `browser-toolbar` and address-bar goldens that draw the pill are unchanged.
- No text pair loses contrast: the test compares luminance per role.
- `playReleaseSmoke` grows by less than 8,192 bytes.

## Risk and rollback

- The tint is a taste call that Dan delegated. It is one commit: `git revert` restores the neutral scheme and its goldens.
- A component that reads the scheme outside `SkimChromeTheme` while drawing over a page would be tinted. The pill goldens catch that for the pill; the translation bar has no golden yet, so the test in step 4 covers the theme instead.
