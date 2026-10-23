# Roadmap

The design implementation plan (`plans/261020-1611-skim-design-implementation/plan.md`) is the scope of the app as built. Phases 1–6 are complete. Phases 7–13 are done with concerns: the unit tests, goldens, and release wiring are in place, and the device gate has not been run (no system image in this environment).

## Decisions

| Id | Status | What was decided |
|---|---|---|
| D1 | Closed | Release uses full R8. No `packageScope`. `-dontwarn com.google.auto.value.AutoValue`. |
| D2 | Default, not validated on device | Incognito tabs are not restored after process death. Unit tests cover this. A device check is still open. |
| D3 | Closed | "Summarize my N open tabs" and automatic grouping skip sites whose Skim AI state is off. Page text is read when the person asks, from a live WebView. |
| D4 | Closed | A chat belongs to the page (URL without the fragment). Same-document navigations keep the chat. |
| D5 | Closed | Downloads cover http(s), `data:`, and `blob:`. |
| D6 | Closed | The client sends `X-Skim-Install`. Release waits on the proxy contract. |
| D7 | Closed | The only model id is `claude-haiku-5-5` (`AiConfig.MODEL_ID`). |
| D8 | Default, waiting on design | Undesigned states use the nearest pattern and the strings in `plan.md` ("Copy cần design review"). Server, refusal, and interrupted AI cards use those strings. There is no design PNG for them. |

## Not designed yet

From `design/README.md`. The app does not invent screens for these:

- Security UI: http in the address bar beyond the public icon, SSL errors, Safe Browsing. Platform behavior stays (`SSL` cancel, the system Safe Browsing interstitial).
- Dialogs the page opens: `alert`, `confirm`, `prompt`, leave-site, HTTP auth. The WebView default dialogs stay.
- AI server errors and a usage-limit screen beyond the 06c-style cards (D8).
- Search with no results, and most empty states beyond the one-line strings that ship.
- An opened bookmark folder other than the same list with the folder name as the title.
- Find in page with no matches (the UI shows `0/0`).
- An in-app Downloads screen.
- A list of sites in AI & privacy.
- Landscape and tablet layouts.

## Open questions

1. **Clean Architecture.** The original brief asked for a `domain/` layer, use cases, and repository interfaces. The plan and the code use `AppContainer` and concrete repositories. Rewriting phases 1–12 into that shape would touch every ViewModel.
   - Option A: keep the current graph.
   - Option B: add interfaces and use cases around the existing repositories without moving screens.
   - Option C: rewrite the graph into presentation / domain / data.
   - Recommendation: A. The plan's architecture is what the tests and screens are written against.

2. **D2 on a device.** Incognito tabs are omitted from Room and from `TabManager.restore()`. `ProcessDeathRestoreTest` recreates the Activity after `pool.destroyAll()`. It has not been run on `skim_api36`.
   - Option A: run the instrumented test and the adb checklist before any beta.
   - Option B: treat the unit test as enough.
   - Recommendation: A.

3. **D8 copy.** Server error, refusal, and interrupted answers use the strings marked default in the plan. There is no board for them.
   - Option A: keep the strings until design reviews them.
   - Option B: hide those states.
   - Recommendation: A. The strings are already what the cards show.

4. **Image options tile color.** The design HTML uses `#DCE2E8` / `#4A5560`. The app uses `surfaceContainerHighest` / `onSurfaceVariant` so dark theme has a token.
   - Option A: keep the token.
   - Option B: hard-code the gray.
   - Recommendation: A.

5. **History favicon letters.** `siteLetter` uses the first character of the host. The history board draws R and N for two hosts that do not start with those letters.
   - Option A: keep `siteLetter`.
   - Option B: special-case the board.
   - Recommendation: A. The same helper is used for history, bookmarks, and tabs.

6. **06b sheet height.** The board is 760 dp tall. The shared bottom sheet opens at full height.
   - Option A: keep the shared sheet.
   - Option B: a custom shorter sheet for this one screen.
   - Recommendation: A. It is recorded under known deviations.

7. **Live API key.** Debug calls Anthropic only when `skim.anthropicApiKey` is set. No key is committed. Live answers were not exercised.
   - Option A: the developer adds their own spend-limited key locally.
   - Option B: commit a key.
   - Recommendation: A.

8. **Device coverage number.** Kover measures the JVM unit tests. It does not include `androidTest`. The 80% bar is the unit-test number for `:app`.

## Known limits

- Blob downloads depend on a WebMessage listener. That path is still marked unverified against the current WebView.
- Find-in-page and long-press highlights are drawn by Chromium, not in the app's primary color.
- `ACCESS_LOCAL_NETWORK` is not requested. Pages that need the local network fail the way the platform fails them.
- `EXTRA_IS_SENSITIVE` exists only on API 33 and above. Incognito privacy on older APIs relies on `FLAG_SECURE` and on not restoring those tabs.
- Robolectric does not paint WebView, so golden reviews skip the sample page art.
- `usesCleartextTraffic` is implied by the network security config so the release-smoke test and http pages work. A production policy that forbids cleartext would have to exempt the smoke build only.
