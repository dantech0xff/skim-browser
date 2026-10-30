# Tabs

Tabs shows the open tabs and can open a new one. The article tab stays in the list after the new tab opens.

## Sub-features

- `tabs-open` shows the switcher from the toolbar.
- `tabs-new` opens a new tab from the switcher.

## How to get to it (user POV)

- Tap the tabs button on the floating toolbar. Its content description starts with `Tabs,`.
- Tap New tab on the switcher.

## Driving it with verify-skim

Preconditions:

- The open-url steps have left the article page on screen.
- The same `drive.py drive` process is still running. Do not clear the app between these steps.

- **Open the switcher.** Tap the tabs control. The script taps the content description that contains `Tabs,` and saves `05-tabs.png`.
- **New tab.** Tap New tab. The script taps the content description `New tab` and saves `06-new-tab.png`. The home search field is the resulting screen.
- **Return.** The script taps `Tabs,` again, then the tab whose text contains `heat`, so the article is current before Ask Skim.

## Gotchas

- The tabs control's content description includes the count, for example `Tabs, 1 open`. Match the `Tabs,` prefix, not a fixed count.
- New tab is the content description of the add button, not a row in the grid. A tab card titled `New tab` is a different control.
- System Back on a new tab can leave the app. The script returns through the switcher instead.
