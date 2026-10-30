# Open a URL

Open a URL loads an http address in the current tab and shows Ask Skim once the page has finished loading.

## Sub-features

- `open-from-intent` loads an address another app sends with `ACTION_VIEW`.
- `open-ready` enables Ask Skim only after that page finishes loading.

## How to get to it (user POV)

- Finish welcome and setup, then open a link from another app.
- The address bar on the loaded page shows the site.

## Driving it with verify-skim

Preconditions:

- The emulator is booted and doctor reports `device`.
- `drive.py drive` is the command. It clears the app, completes welcome, then sends the article URL.

- **Welcome.** The first screen shows Get started. The script waits for the text `Get started` and saves `01-welcome.png`.
- **Setup.** Tap Get started. The script taps the text `Get started` and saves `02-setup.png`.
- **Home.** Tap Start browsing. The script taps the text `Start browsing` and saves `03-home.png`.
- **Open the article.** The script runs `adb shell am start -a android.intent.action.VIEW -d http://127.0.0.1:8787/article -n com.dantech.skimbrowser/.MainActivity`.
- **Page is ready.** The content description `Ask Skim about this page` is present, and `04-page.png` is saved. `fake-backend.log` contains a `GET` line.

## Gotchas

- Without `adb reverse tcp:8787 tcp:8787`, the page request never reaches the host. The drive command sets the reverse. A manual `am start` without it fails closed on a connection error.
- The content description `Ask Skim about this page, unavailable until the page loads` is not the ready state. Wait for the exact ready string.
- A slow emulator shows "isn't responding" dialogs. The drive taps Wait and continues. Close app would discard the screen under test.
- A debug APK does not use this backend for Ask Skim. Page loads still need the reverse only when the URL is `127.0.0.1:8787`.
- On a software-rendered emulator, creating the first WebView can SIGTRAP inside Trichrome (`AwContents.<init>`). Welcome, setup, and home still run. A page load then shows "Skim keeps stopping". That is a WebView failure on that emulator, not a missing tap.
