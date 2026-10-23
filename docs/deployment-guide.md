# Deployment

Release is blocked until the proxy below meets the security contract. Building a release APK against a placeholder URL is a check that R8 and the key scan succeed. It is not permission to ship.

## Build types

| Type | Signing | AI endpoint | API key in the APK | Minify |
|---|---|---|---|---|
| `debug` | debug | `https://api.anthropic.com` | `skim.anthropicApiKey` if set | no |
| `release` | release | `skim.aiProxyUrl` | none (`ANTHROPIC_API_KEY` is null) | R8, every package |
| `releaseSmoke` | debug | `http://127.0.0.1:8787` | none | same as release |

`releaseSmoke` exists so `ReleaseSmokeTest` can install a minified build. It is not a shippable variant. `matchingFallbacks` includes `release`. Select it with `-Pskim.testBuildType=releaseSmoke`.

R8 runs on the whole program (no `packageScope`). The only extra keep rule is `-dontwarn com.google.auto.value.AutoValue` in `app/src/main/keepRules/rules.keep`, because the OpenTelemetry API pulled in by the Anthropic SDK references that annotation.

`CheckSkimReleaseConfig` runs before a `release` build. It requires https values for `skim.aiProxyUrl`, `skim.termsUrl`, `skim.privacyUrl`, and `skim.aiDataPolicyUrl`, and it fails if `skim.aiProxyUrl` is set in the committed `gradle.properties`. `releaseSmoke` does not run that check: its endpoint is fixed in the build type.

## Where the proxy URL comes from

CI should pass `skim.aiProxyUrl` as a secret (`-P` or `ORG_GRADLE_PROJECT_skim_aiProxyUrl`). A developer can put it in `local.properties` for a local release build. Do not commit it. Do not put it in `gradle.properties`.

The debug key is the same shape of secret: `skim.anthropicApiKey` in `local.properties` only. `verifyNoApiKey<Variant>` scans the non-debug APK and the AAB for the `sk-ant-` prefix and for the debug key value. `assemble` and `bundle` finalize with that task. `check` depends on it.

## Ship checklist

Do not ship until every line is true.

1. The proxy meets the contract in the next section.
2. `./gradlew :app:check` is green (`testDebugUnitTest`, `lintDebug`, and `verifyNoApiKey*` for non-debug variants).
3. `assembleRelease` and `bundleRelease` are green with the real proxy URL and the three legal URLs.
4. `verifyNoApiKeyRelease` reports no match, with a debug key present in `local.properties`, for both the APK and the AAB.
5. `ReleaseSmokeTest` is green on `skim_api36` (`-Pskim.testBuildType=releaseSmoke`). Logcat has no `AnthropicInvalidDataException`, `ClassNotFoundException`, or `SerializationException`.
6. `connectedDebugAndroidTest` is green on `skim_api36` (incognito tests not skipped) and on `skim_api28`.
7. The manual process-death, memory, and `FLAG_SECURE` checks in `plans/261020-1611-skim-design-implementation/phase-13-verification-hardening-docs.md` have been done on `skim_api36`.

## Proxy contract

Skim is only the client. The proxy is not in this repository. It must do all of the following before a release build is distributed:

- Accept `POST {proxy}/v1/messages`, forward a valid body unchanged, and insert `x-api-key` and `anthropic-version: 2023-06-01`. Do not buffer the SSE response.
- Authenticate `X-Skim-Install` (one UUID per install). Apply a quota per install and a rate limit. A new install id is limited more tightly than an old one.
- Enforce a server allowlist: `model` is `claude-haiku-5-5`; `max_tokens` is at most 8192; the body has no `tools`, `tool_choice`, `mcp_servers`, `thinking`, or sampling fields; no beta headers; a body size limit; only the keys Skim sends (`model`, `max_tokens`, `system`, `messages`, `output_config`, `stream`).
- Put a spend cap on the Anthropic key the proxy uses, and alert when the cap is crossed.
- Play Integrity (a standard request) is recommended before the app is distributed widely. The app does not send an integrity token today.
- Do not log page text or questions. That matches the in-app line that page text is sent encrypted and deleted after each answer.

If any of those is missing, do not ship.
