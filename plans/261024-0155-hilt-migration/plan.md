---
title: "Move manual DI to Hilt 2.60.1"
description: "Replace AppContainer with Hilt on the current AGP 9 toolchain, including the singleton graph, ViewModels, and activity scope."
status: completed
priority: P2
effort: 40h
branch: master
tags: [refactor, android, infra]
blockedBy: []
blocks: []
created: 2026-10-24
---

# Move manual DI to Hilt

## Overview

`AppContainer` is the process graph, created in `SkimApplication.onCreate`. This plan replaces it with Hilt 2.60.1 (`com.google.dagger.hilt.android`) on AGP 9.4.1 built-in Kotlin and KSP 2.3.12. Process types live on `SingletonComponent`. `BrowserHost` and the objects `createBrowserHost` builds live on `ActivityComponent`. Screens obtain `@HiltViewModel` types from `androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel`. `domain/` does not import `javax.inject` or `dagger`. Each phase leaves `:app:assembleDebug` and `:app:testDebugUnitTest` passing with `--no-configuration-cache`.

The four phases below are the ones to implement. `research/researcher-02-hilt-migration-patterns.md` also sketches a later split of the six-round close test and extra phases; those are not in force. The reopen tests stay one method each. Each round drops the generation and the next read builds a new one. Hilt is the only DI library in this change.

## Dependencies

Phase 2 depends on 1, phase 3 on 2, and phase 4 on 3. `blockedBy` is empty.

`app/build.gradle.kts`, `DomainBoundaryTest.kt`, `AppContainer.kt`, and `MainActivity.kt` change in more than one phase. Run the phases in order, one at a time.

## Phases

| Phase | Name | Status |
|-------|------|--------|
| 1 | [Toolchain](./phase-01-start.md) | Completed |
| 2 | [Singleton graph](./phase-02-singleton-graph.md) | Completed |
| 3 | [ViewModels](./phase-03-viewmodels.md) | Completed |
| 4 | [Activity graph](./phase-04-activity-graph.md) | Completed |

## Reports

Toolchain, tests, and R8: [researcher-01-hilt-toolchain.md](./research/researcher-01-hilt-toolchain.md). Lifetimes, Navigation 3, and the generation: [researcher-02-hilt-migration-patterns.md](./research/researcher-02-hilt-migration-patterns.md). Call sites and line inventory: [scout-manual-di.md](./research/scout-manual-di.md). The originals remain in `plans/reports/`.

## Red Team Review

Three reviewers (security, failure, assumptions). Standard tier, 4 phases. The disposition form was skipped, so the recommended Accept set was applied. Two findings were rejected.

### Accepted

1. Phase 4 `assembleRelease` passes the four `-P` https URLs. Evidence: `app/build.gradle.kts:210-237`, `app/build.gradle.kts:312-313`, `gradle.properties:24-26`, `README.md:85-89`.
2. One `ProcessGraph` generation owns Room, DataStore, OkHttp, `TabManager`, and the repositories. `close()` drops them. Repository classes stay as they are (`SettingsRepository.kt:29-31`, `TabRepository.kt:24-26`). `TabManager` is not an unscoped `@Provides`.
3. `SkimAppRule`, `BrowserRig`, and `DeviceSqliteDataTest` call `ProcessShutdown.close()` before the next test opens Room. `TestStorage` is set before that test's component is used. `@Singleton` is per Hilt test case.
4. Phase 2 binds `SnackbarController`, `UndoRegistry`, `PageChats`, `ReaderHighlights`, `SiteDataCleaner`, `AskPageAccess`, `IncognitoDecisions`, and `NetworkAvailability`.
5. Process `CoroutineScope` parameters use `@AppScope`. `TabsViewModel.appScope` stays `CoroutineScope? = null` (`TabsViewModel.kt:87`, `TabsViewModelTest.kt:277-289`). Activity types with Kotlin defaults are built from `@Provides`, not `@Inject` (`DownloadRouter.kt:78-82`, `SitePermissionBridge.kt:79`).
6. `SearchViewModel` keeps the flow-and-function constructor (`SearchViewModel.kt:76-81`). A second `@Inject` constructor delegates to it. `SearchViewModelTest` is not rewritten.
7. The chat provider aliases the concrete class the way `ChatRepository.kt:7` does. `DomainBoundaryTest.kt:36-45` also flags the fully qualified name outside `data/`.
8. `BrowserRoute.kt` is not in `BROWSER_WIRING` (`DomainBoundaryTest.kt:145-155`). It receives `TabsController` from `SkimNavDisplay` or `di`.
9. One `WindowSecureController` per activity (`WindowSecureController.kt:46-54`). `remember` keys include `host.secure`.
10. `ActivityHost` is a binding. androidTest installs `TestActivityHost` (`BrowserRig.kt:281`). `BrowserHost.destroy()` calls `activityHost.destroy()` (`ActivityHost.kt:102-106`, `BrowserHost.kt:32-36`).
11. No `SkimApplication` cast in a property initializer (`MainActivityLifecycleTest.kt:64`, `SkimAppRule.kt:52-54`). `ReleaseSmokeTest.kt:52-56` puts `HiltAndroidRule` outside the chain that launches `MainActivity`. `DeviceCapabilitiesReportTest` stays without `@HiltAndroidTest`.
12. Route tests merge `@Config` qualifiers with `HiltTestApplication`, and the compose rule runs after the Hilt rule (`NewTabPageRouteTest.kt:110`, `NewTabPageRouteTest.kt:117`). Entry points do not return the Claude client, OkHttp, DataStore, or the database. `AiModule` copies `AppContainer.kt:321-322`. `PageScripts` stays process-scoped (`AppContainer.kt:250`).

### Rejected

- Changing `Log.e` of the `appScope` throwable (`AppContainer.kt:129-133`) to a class name. That is current behavior, outside this migration.
- Requiring a connected `releaseSmoke` run. The repo does not install a system image. The `Hilt_*` keep stays out until a runtime `ClassCastException`.

### Whole-Plan Consistency Sweep

- Files reread: `plan.md`, `phase-01-start.md`, `phase-02-singleton-graph.md`, `phase-03-viewmodels.md`, `phase-04-activity-graph.md`
- Decision deltas checked: generation instead of a repository lease; release `-P` URLs; `BrowserRoute` off the allowlist; nullable `TabsViewModel.appScope`; `SearchViewModel` test constructor kept
- Reconciled stale references: repository rewrite steps, `DataBindsModule`, "HTTP lease", phase 1 "lease", `SearchViewModelTest` edits, ten-file allowlist claim
- Unresolved contradictions: 0

## Validation Log

### Verification Results

- Claims checked: 48 across the three reviews, then the edits above
- Verified: file paths named after the edit exist, including `HistoryRepository.kt`, `ChatRepository.kt`, `DownloadRouter.kt`, `BrowserRig.kt`, `DomainBoundaryTest.kt:145-155`, and all 25 androidTest classes with `DeviceCapabilitiesReportTest` called out
- Failed: 0 after the edits
- Unverified: Hilt 2.60.1 on Kotlin 2.4.21 is a phase 1 compile, not a result of this planning pass
- Tier: Standard

### Session 1 — 2026-10-24

**Trigger:** post-plan validation after the red-team edits.
**Questions asked:** 0. The disposition form was skipped, and the accepted findings settled the load-bearing choices. No remaining product decision sits outside the request to move all manual DI to latest Hilt.

#### Confirmed Decisions

- Hilt 2.60.1, plugin `com.google.dagger.hilt.android`, `hilt-lifecycle-viewmodel-compose` 1.4.0, `kotlin-metadata-jvm` 2.4.21.
- `domain/` does not import `javax.inject` or `dagger`.
- Activity graph is in scope. `AppContainer` is deleted in phase 4.
- Unit tests keep calling public constructors.

### Whole-Plan Consistency Sweep

- Files reread: the same five plan files
- Decision deltas checked: the red-team list above
- Reconciled stale references: 0 further
- Unresolved contradictions: 0

<!-- slug: hilt-migration -->
