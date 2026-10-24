---
name: agent-workflow-contracts
description: How Dan runs this repo — orchestrated subagents with binding plan contracts (Vietnamese plans under plans/), deviations allowed only with cited evidence; what a useful Kongming answer looks like here
metadata:
  type: user
---

Dan (danhtran.developer@gmail.com) builds Skim (Android, Kotlin, Compose M3 Expressive, Navigation 3) through an orchestrator that hands subagents a binding contract copied from the phase plan (plans are written in Vietnamese; code and reports in English). A controller verifies each subagent's report and writes the next agent's prompt from the reported signatures.

Consults from subagents therefore need: a single clear recommendation, the exact public shape (signatures) to report, a list of which contract items are deviations and the `file:line` evidence that justifies each, and the handoff lines the controller must put into the next agent's prompt. Deviations are accepted when evidence-backed; unjustified ones cost trust.

Existing conventions worth knowing: ViewModels use `MutableStateFlow` + `asStateFlow()` and `viewModel { }` factory lambdas with a manual `AppContainer`; tests are `runTest` + Robolectric (`AndroidJUnit4`) with `backgroundScope` for long-lived work and a `MainDispatcherRule` (default Unconfined) in `app/src/test/.../testing/`.
