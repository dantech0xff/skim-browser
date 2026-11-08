# Run and deploy the AI proxy

The Worker in `proxy/` is the Skim AI proxy. Debug and release both call `skim.aiProxyUrl`. The Anthropic key is a Worker secret. It is not in the app and not in git.

`releaseSmoke` and `benchmark` stay on `http://127.0.0.1:8787`, which is the fake backend in the instrumented tests. Do not point those variants at this Worker.

## Where things live

| Thing | Where |
|---|---|
| Worker code | `proxy/` |
| Rate and quota counters | One Durable Object per install id |
| IP rate, IP spend, install spend, global spend | Durable Object class `AbuseLedger` (migration `v2`) |
| Like and dislike ratings | Durable Object class `FeedbackStore` (migration `v3`), one ledger for every rating |
| Anthropic key, deployed | Worker secret `ANTHROPIC_API_KEY` |
| IP hash salt, deployed | Worker secret `IP_HASH_SALT` |
| IP hash salt, on your machine | `IP_HASH_SALT=` in `proxy/.dev.vars` |
| Anthropic key, on your machine | `proxy/.dev.vars`, which git ignores |
| URL the app calls | `skim.aiProxyUrl` in `local.properties` |

Durable Objects keep the counters. A request to one install id runs on one object, so two requests cannot both take the last slot. KV is eventually consistent, and that race would let a new install past the tighter limit.

The starting numbers are in `proxy/src/policy.ts`. A new install is one whose first accepted request was under 24 hours ago. A new install gets 5 requests a minute and 30 a day. An older install gets 20 a minute and 300 a day.

A client IP, from `CF-Connecting-IP`, gets 40 requests a minute and 500 a day, across every install id. Minting a new UUID does not reset that. The header is required on Cloudflare. A missing header is rejected there. `wrangler dev` has no edge header, so local requests share one bucket. The IP is hashed with `IP_HASH_SALT` before it is used as an object name, and it is not written into storage. An alarm deletes an object after its 24 hour window.

Spend is a Haiku estimate: $1 per million input tokens and $5 per million output tokens, including cache tokens with input. Each of these has to pass, over a fixed 24 hour window: $1 per install id, $2 per IP, and $20 for the whole Worker. Before the upstream call the Worker reserves the output `max_tokens` plus one input token per 3 body bytes. After the stream it replaces that reservation with the usage from `message_start` and `message_delta`. A failed upstream call refunds the reservation. A cap responds with 429 and `{"error":"install_cap"}`, `ip_cap`, or `global_cap`, plus `Retry-After`. The app shows "Daily limit reached" for any 429.

The Anthropic console spend cap is separate, and you still set it there.

## Ratings

`POST /v1/feedback` records a like or dislike. The body is only `answerId`, `revision`, `rating` (`good`, `poor`, or `none`), `surface` (`page` or `tabs`), and `appVersion`. `none` is a cleared rating. The page, the title, the prompt, and the answer are not accepted. An extra field is rejected.

`answerId` is a random UUID the app creates for that answer. It is not the install id. `revision` starts at 1 and grows when the person changes the rating. The ledger keeps one row per answer. A retry of the same revision does not add a row. An older revision does not replace a newer one. The same revision with a different rating is a conflict.

`X-Skim-Install` is checked so one install cannot flood the ledger. The id is the name of the existing quota object and is not a column in the ratings table. The client IP is not read on this path.

Rows older than 90 days are deleted by the ledger's alarm. That window is `feedbackRetentionMs` in `proxy/src/policy.ts`.

The app asks before the first send. A declined choice, an incognito tab, and a rating chosen before the person agreed are not sent. A failed send leaves the rating on the phone and does not show it as delivered. While chat history is on, an unsent revision is tried again later. While chat history is off, a retry lasts only as long as that chat stays open.

`GET /v1/feedback/summary` returns the current counts (`good`, `poor`, `none`, and the same counts by surface and by app version). It needs `Authorization: Bearer` matching the Worker secret `FEEDBACK_READ_TOKEN`. The app does not have that secret. Counts are rows, not events, so a toggle does not inflate them. `none` is a rating that was cleared and has not expired yet.

```
curl -s -H "Authorization: Bearer $FEEDBACK_READ_TOKEN" https://<proxy>/v1/feedback/summary
```

Set `FEEDBACK_READ_TOKEN` with `npx wrangler secret put FEEDBACK_READ_TOKEN` before that read works on a deploy. Local `proxy/.dev.vars` can hold a throwaway token. Do not commit it.

## Run Ask Skim on a device

You need a Cloudflare account only if you deploy. Local `wrangler dev` does not.

1. From `proxy/`, install dependencies with `npm ci`.
2. Create `proxy/.dev.vars` with `ANTHROPIC_API_KEY=` and `IP_HASH_SALT=`, and paste your own key and any local salt after the equals signs. Do not commit that file.
3. Start the Worker with `npx wrangler dev`. It listens on port 8787.
4. On the computer that runs the emulator or the phone, run `adb reverse tcp:8787 tcp:8787`.
5. In `local.properties`, set `skim.aiProxyUrl=http://127.0.0.1:8787`. Leave it empty to build without live AI. Ask Skim then fails before it opens a connection.
6. Install a debug build. Debug accepts http so this local URL works. A release build still requires https.

Do not run the instrumented fake backend on 8787 at the same time. That port is the one `releaseSmoke` uses for its stand-in.

## Deploy

Do this when you are ready to host the proxy. Nothing in CI deploys it.

You need a Cloudflare account and an Anthropic account.

1. In the Anthropic console, create a key, set a spend cap on it, and add an alert for when the cap is crossed.
2. From `proxy/`, run `npx wrangler login`.
3. Run `npx wrangler deploy`. The command prints the `https://` URL.
4. Run `npx wrangler secret put ANTHROPIC_API_KEY` and paste the key when asked. Run `npx wrangler secret put IP_HASH_SALT` and paste a long random salt. Neither value is stored in the repo. Each command publishes a new version of the Worker.
5. Put that `https://` URL in `skim.aiProxyUrl` for the release build. Do not commit it. `gradle.properties` stays without that property.

Play Integrity is not part of this Worker. The app does not send an integrity token.

The Worker does not log page text, questions, or the install id. It prints no message body. Traces are off so a trace does not keep the request body. Workers Logs are on, and they only have what the Worker prints.
