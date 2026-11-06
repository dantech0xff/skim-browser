# Deployment

The Worker that meets the contract below is `proxy/`. A release still waits until that Worker is deployed with its secret and the Anthropic spend cap, as [Run and deploy the AI proxy](proxy.md) describes. A release APK built against a placeholder URL only proves that R8 and the key scan succeed. It is not permission to ship.

How to build, test, and pass the legal URLs is the README. This page is the ship rule.

## Variants

The build types are in `app/build.gradle.kts`.

| Variant | Role |
|---|---|
| `debug` | Calls `skim.aiProxyUrl`, the same property as release. http is allowed for a local Worker. An empty URL fails closed before any connection. The APK has no key. |
| `release` | Calls `skim.aiProxyUrl` over https. The APK has no key. R8 covers every package. |
| `releaseSmoke` | Minified like release, for `ReleaseSmokeTest`. Not a variant that ships. Select it with the `skim.testBuildType` property. The command is in the README section "Device checks". |
| `benchmark` | For `:macrobenchmark`. Same local stand-in as `releaseSmoke`. Not a variant that ships. |

`CheckSkimReleaseConfig` runs before a `release` build. It requires https values for `skim.aiProxyUrl`, `skim.termsUrl`, `skim.privacyUrl`, and `skim.aiDataPolicyUrl`, and it fails when `skim.aiProxyUrl` is set in the committed `gradle.properties`. `releaseSmoke` does not run that check. The task is `checkSkimReleaseConfig` in `app/build.gradle.kts`.

A local proxy URL belongs in `local.properties` or in CI (`-P` or `ORG_GRADLE_PROJECT_skim_aiProxyUrl`). It does not belong in `gradle.properties` or in git. The Anthropic key belongs only in the Worker secret. `VerifyNoApiKey` scans every variant, debug included, for the `sk-ant-` prefix. `assemble` finalizes that task. For a non-debug variant, `bundle` finalizes it too, and `check` depends on it. The needle lives in that task, in `app/build.gradle.kts`.

R8 has no `packageScope`. The extra keep rule is the comment and the `-dontwarn` line in `app/src/main/keepRules/rules.keep`.

## Ship when all of these are true

1. The proxy meets the contract below.
2. `:app:check` is green.
3. `assembleRelease` and `bundleRelease` are green with the real proxy URL and the three legal URLs. The commands are in the README.
4. `verifyNoApiKeyRelease` reports no `sk-ant-` match for both the APK and the AAB.
5. `ReleaseSmokeTest` is green on `skim_api36`. Logcat has no `AnthropicInvalidDataException`, `ClassNotFoundException`, or `SerializationException`.
6. `connectedDebugAndroidTest` is green on `skim_api36` (incognito tests not skipped) and on `skim_api28`. Device setup is the README section "Device checks".
7. Process death, memory pressure, and `FLAG_SECURE` still need a manual pass on `skim_api36`. The checklist that used to describe those steps is not in the repo. A green instrumented test does not replace that pass.

## Proxy contract

Skim is only the client. The Worker in `proxy/` must do all of the following before a release build is distributed:

- Accept `POST {proxy}/v1/messages`, forward a valid body unchanged, and insert `x-api-key` and `anthropic-version: 2023-06-01`. Do not buffer the SSE response.
- Authenticate `X-Skim-Install` (one UUID per install). Apply a quota per install and a rate limit. A new install id is limited more tightly than an old one. Also rate limit by client IP, and enforce a 24 hour spend cap per install, per IP, and globally. The numbers live in `proxy/src/policy.ts`.
- Enforce a server allowlist: `model` is `claude-haiku-5-5`; `max_tokens` is at most 8192; the body has no `tools`, `tool_choice`, `mcp_servers`, `thinking`, or sampling fields; no beta headers; a body size limit; only the keys Skim sends (`model`, `max_tokens`, `system`, `messages`, `output_config`, `stream`). The shape the client sends is `ai/src/test/java/com/dantech/skimbrowser/ai/RequestShapeTest.kt`. The model id and token ceiling in the app are `ai/src/main/java/com/dantech/skimbrowser/ai/AiConfig.kt`.
- Put a spend cap on the Anthropic key the proxy uses, and alert when the cap is crossed.
- Play Integrity (a standard request) is recommended before the app is distributed widely. The app does not send an integrity token today.
- Do not log page text or questions. That matches `setup_privacy_note` and `ai_permission_body` in `app/src/main/res/values/strings.xml`.

If any of those is missing, do not ship.
