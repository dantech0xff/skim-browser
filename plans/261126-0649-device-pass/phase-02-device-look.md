---
phase: 2
title: "Device look"
status: todo
priority: P2
effort: "2h"
dependencies: [1]
---

# Phase 2: Device look

## Goal

See on a device what pull request #41 and the onboarding motion plan were only tested for in Robolectric, and write down anything that looks or feels wrong.

## Where

- `skim_api36` (`emulator-5580`) for everything that needs a cleared install, a narrower screen or Remove animations. Install the debug build: `ANDROID_SERIAL=emulator-5580 ./gradlew :app:installPlayDebug --no-configuration-cache`.
- The Pixel 6 for haptics, because an emulator has no vibrator. It has no Skim data to keep, so install the debug build there: `ANDROID_SERIAL=<Pixel 6 serial> ./gradlew :app:installPlayDebug --no-configuration-cache`. Do not change its settings.

## Steps

1. **Onboarding entrance.** This is the open device box in `../261030-0843-onboarding-motion/phase-02-verify.md`. Run `adb -s emulator-5580 shell pm clear com.dantech.skimbrowser`, then launch Skim. On Welcome the article badge, the search badge and the hero Sunny mark pop in that order, while Get started is already there. Setup slides in and its pieces arrive in place. Make Skim the default browser in system settings and come back: "Set as default" crossfades into a disabled "Done".
2. **Site icons.** Visit three sites that show a favicon in another browser. Their icons, not letter tiles, show on the tab cards, in History, on a bookmark, on a Home shortcut, and on a saved chat when the proxy URL is set. Open an incognito tab on a fourth site: its card shows its icon, and `adb -s emulator-5580 shell run-as com.dantech.skimbrowser ls cache/site-icons` lists no `<host>.png` for that site. Then clear only "Cached images and files" in Clear browsing data: the normal tiles go back to letters, and the open incognito tab keeps its icon.
3. **Pill and chip.** On a long page, scroll down: the pill becomes the 40 dp chip with the host and Ask Skim. Scroll up, or tap the chip: the pill comes back. The pill has no Back or Forward, and system Back goes back in the page.
4. **Narrow pill.** Open a page with a video Skim can save, then run `adb -s emulator-5580 shell wm density 540`, which makes the Pixel 7 window about 320 dp wide. Save video leaves the pill, and More options offers "Download video". Run `adb -s emulator-5580 shell wm density reset` afterwards.
5. **Tab reorder.** In Tabs, long-press a card: it lifts, and the other cards of its group make room as it moves. On release it settles into its slot. A card dropped over another group stays in its own group.
6. **Smaller motion.** The tab count rolls to its new number, and the tab swipe caption fades. With the proxy URL set, the suggested questions in Ask Skim arrive one after another, a new question and its answer rise in, and the chat follows a question asked in the sheet.
7. **Remove animations.** Run `adb -s emulator-5580 shell settings put global animator_duration_scale 0`, and the same for `transition_animation_scale` and `window_animation_scale`. Repeat steps 1, 3 and 5: each screen appears settled at once. Set all three back to `1` afterwards.
8. **Haptics, on the Pixel 6.** Long-press a tab card (one haptic at pick up) and drop it (one at the drop). Swipe a card past the point where release closes it, swipe the pill past the point where release switches tabs, and pull to refresh past its point. Each gives one haptic at the point, not before it. Before Android 14 the point haptic falls back to a context click; only a phone on Android 13 or older shows that.

## Results

Result is pass, wrong (with a screenshot or a short `adb shell screenrecord` clip in `./reports/`), or not checked (with the reason).

| Check | Device | Result | Notes |
|---|---|---|---|
| Onboarding entrance and crossfade | skim_api36 | | |
| Site icons: normal, incognito, cache clear | skim_api36 | | |
| Pill to chip and back | skim_api36 | | |
| Narrow pill moves Save video into More options | skim_api36 | | |
| Tab reorder: lift, room, settle | skim_api36 | | |
| Tab count, caption, Ask Skim motion | skim_api36 | | |
| Remove animations | skim_api36 | | |
| Haptics | Pixel 6 | | |
| Haptic fallback before Android 14 | phone on Android 13 or older | | |

## Todo

- [ ] Onboarding entrance
- [ ] Site icons
- [ ] Pill and chip
- [ ] Narrow pill
- [ ] Tab reorder
- [ ] Smaller motion
- [ ] Remove animations
- [ ] Haptics

## Success Criteria

- Every row has a result.
- The onboarding motion plan's device box is checked, or says what looked wrong.
- The "Not yet seen on a device" and "Not yet felt on a device" notes in `../261123-1527-skim-identity-pill-motion/plan.md` are updated.

## Risk Assessment

- An emulator draws with a software GPU, so a stutter seen here is a note, not a jank result. Phase 3 measures frame time on the Pixel 6.
- `wm density` and the animation scales stay changed until reset. Check both are back before ending the session.
