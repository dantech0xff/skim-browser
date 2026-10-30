# Ask Skim

Ask Skim reads the current page and shows a summary. The first time for a site, Allow is required. This journey uses the local fake backend, not the Anthropic API.

## Sub-features

- `ask-allow` shows the site permission dialog the first time.
- `ask-summary` shows the streamed answer after Allow.

## How to get to it (user POV)

- On a loaded page, tap Ask Skim about this page.
- Tap Allow on the first-use dialog.

## Driving it with verify-skim

Preconditions:

- The article tab is current and Ask Skim is enabled. The tabs steps return there.
- `tools/verify-skim/fake_backend.py` is serving `summary-with-citations.sse` on port 8787, which `drive.py drive` starts.
- `adb reverse tcp:8787 tcp:8787` is in place, which the same command sets.

- **Ask.** Tap Ask Skim about this page. The script taps the content description `Ask Skim about this page`.
- **Allow.** Tap Allow. The script taps the text `Allow`.
- **Summary.** The screen shows the text `minus 25`. The script waits for that text and saves `07-ask.png`.
- **Request.** `fake-backend.log` contains `POST /v1/messages`, `x-api-key=None`, and `X-Skim-Install-present=True`.

## Gotchas

- Incognito has no Ask Skim. This journey stays on a normal tab.
- A missing Allow tap leaves the dialog up and no POST is sent. Do not treat the dialog as the summary.
- The release APK that calls the real proxy is the wrong build. Use `releaseSmoke`, whose base URL is `http://127.0.0.1:8787`.
- The fixture text `minus 25` is the proof string. A different SSE file will not show it.
