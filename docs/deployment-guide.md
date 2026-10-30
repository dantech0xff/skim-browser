# Deployment

Release is blocked until a proxy outside this repository meets the contract below. A release APK built against a placeholder URL only proves that R8 and the key scan succeed. It is not permission to ship.

How to build, test, and pass the legal URLs is the README. This page is the ship rule.

## Variants

The build types are in `app/build.gradle.kts`.

| Variant | Role |
|---|---|
| `debug` | Calls Anthropic directly when a debug key is set. That key is inside the APK. Do not distribute a debug APK. |
| `release` | Calls `skim.aiProxyUrl`. The API key field is null. R8 covers every package. |
| `releaseSmoke` | Minified like release, for `ReleaseSmokeTest`. Not a variant that ships. Select it with the `skim.testBuildType` property. The command is in the README section "Device checks". |
| `benchmark` | For `:macrobenchmark`. Same local stand-in as `releaseSmoke`. Not a variant that ships. |

`CheckSkimReleaseConfig` runs before a `release` build. It requires https values for `skim.aiProxyUrl`, `skim.termsUrl`, `skim.privacyUrl`, and `skim.aiDataPolicyUrl`, and it fails when `skim.aiProxyUrl` is set in the committed `gradle.properties`. `releaseSmoke` does not run that check. The task is `checkSkimReleaseConfig` in `app/build.gradle.kts`.

The debug key and a local proxy URL belong in `local.properties` or in CI secrets (`-P` or `ORG_GRADLE_PROJECT_skim_aiProxyUrl`). They do not belong in `gradle.properties` or in git. `VerifyNoApiKey` scans non-debug APKs and AABs. `assemble` and `bundle` finalize with that task, and `check` depends on it. The needles live in that task, in `app/build.gradle.kts`.

R8 has no `packageScope`. The extra keep rule is the comment and the `-dontwarn` line in `app/src/main/keepRules/rules.keep`.

## Ship when all of these are true

1. The proxy meets the contract below.
2. `:app:check` is green.
3. `assembleRelease` and `bundleRelease` are green with the real proxy URL and the three legal URLs. The commands are in the README.
4. `verifyNoApiKeyRelease` reports no match for both the APK and the AAB while a debug key is present in `local.properties`.
5. `ReleaseSmokeTest` is green on `skim_api36`. Logcat has no `AnthropicInvalidDataException`, `ClassNotFoundException`, or `SerializationException`.
6. `connectedDebugAndroidTest` is green on `skim_api36` (incognito tests not skipped) and on `skim_api28`. Device setup is the README section "Device checks".
7. Process death, memory pressure, and `FLAG_SECURE` still need a manual pass on `skim_api36`. The checklist that used to describe those steps is not in the repo. A green instrumented test does not replace that pass.

## Proxy contract

Skim is only the client. The proxy is not in this repository. It must do all of the following before a release build is distributed:

- Accept `POST {proxy}/v1/messages`, forward a valid body unchanged, and insert `x-api-key` and `anthropic-version: 2023-06-01`. Do not buffer the SSE response.
- Authenticate `X-Skim-Install` (one UUID per install). Apply a quota per install and a rate limit. A new install id is limited more tightly than an old one.
- Enforce a server allowlist: `model` is `claude-haiku-5-5`; `max_tokens` is at most 8192; the body has no `tools`, `tool_choice`, `mcp_servers`, `thinking`, or sampling fields; no beta headers; a body size limit; only the keys Skim sends (`model`, `max_tokens`, `system`, `messages`, `output_config`, `stream`). The shape the client sends is `ai/src/test/java/com/dantech/skimbrowser/ai/RequestShapeTest.kt`. The model id and token ceiling in the app are `ai/src/main/java/com/dantech/skimbrowser/ai/AiConfig.kt`.
- Put a spend cap on the Anthropic key the proxy uses, and alert when the cap is crossed.
- Play Integrity (a standard request) is recommended before the app is distributed widely. The app does not send an integrity token today.
- Do not log page text or questions. That matches `setup_privacy_note` and `ai_permission_body` in `app/src/main/res/values/strings.xml`.

If any of those is missing, do not ship.
