# Roadmap

This page is intent that is not shipped behavior. Product rules are `docs/product-vision.md`. Decisions already made are `docs/system-architecture.md` and `docs/deployment-guide.md`.

## Still open

| Item | What is still true |
|---|---|
| Incognito restore on a device | Incognito tabs are omitted from restore (`domain/src/main/kotlin/com/dantech/skimbrowser/domain/tab/TabRules.kt`). That has not been checked on `skim_api36`. Device setup is the README section "Device checks". |
| Undesigned screens and copy | The gaps are the "Not designed yet" list in `design/README.md`. The app does not invent screens for that list. Server, refusal, and interrupted answers already ship without a board: `ai_server_error_title`, `ai_server_error_body`, `ai_refusal_title`, and `ai_refusal_body` in `app/src/main/res/values/strings.xml`. An interrupted answer keeps the summary text already written (`SummaryState.Interrupted` in `app/src/main/java/com/dantech/skimbrowser/ui/ai/AskPageViewModel.kt`). Keep that copy until design reviews it. |

## Not in this repository

The release proxy. The contract, and the rule that release stays blocked until the contract is met, are `docs/deployment-guide.md`.
