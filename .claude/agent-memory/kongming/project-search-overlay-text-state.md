---
name: search-overlay-text-state
description: Phase 6 (04b search overlay) decision context — field state is a composable-owned TextFieldState, SearchViewModel stays Compose-free; why the plan's TextFieldValue contract was a slip
metadata:
  type: project
---

Advised on 2026-10-22: the 04b omnibox text lives in the overlay composable as `rememberTextFieldState(initialText, selection)`; `SearchViewModel` receives plain `onQueryChanged(text: String)` and exposes `SearchUiState` without a `query` field.

**Why:** The phase-6 plan sketch said `SearchUiState(query: TextFieldValue, ...)` but also `onQueryChanged(text: String)` and step 4 assigning selectAll to the overlay — internally inconsistent. material3 1.5.0-beta01 only has a non-deprecated `SearchBarDefaults.InputField(textFieldState, searchBarState, onSearch)`; value-based fields are deprecated. Foundation `TextFieldState` edits require the main thread and `snapshotFlow` needs apply notifications, which makes a VM-owned field awkward in JVM tests. Nav3 scopes ViewModels to the `BrowserKey` entry (one entry for all tabs), so a VM-owned field would go stale across opens/tabs.

**How to apply:** When phase 7 (05/11 address tap) or phase 12 (Ask Skim row) touch 04b, keep the split: composable owns `TextFieldState` (clearText / setTextAndPlaceCursorAtEnd / setTextAndSelectAll), VM owns results + `askSkim` computed synchronously in `onQueryChanged`. If a VM ever owns a `TextFieldState`, tests must call `Snapshot.sendApplyNotifications()` and the VM must be keyed per tab. See [[agent-workflow-contracts]].
