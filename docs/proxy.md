# Run and deploy the AI proxy

The Worker in `proxy/` is the Skim AI proxy. Debug and release both call `skim.aiProxyUrl`. The Anthropic key is a Worker secret. It is not in the app and not in git.

`releaseSmoke` and `benchmark` stay on `http://127.0.0.1:8787`, which is the fake backend in the instrumented tests. Do not point those variants at this Worker.

## Where things live

| Thing | Where |
|---|---|
| Worker code | `proxy/` |
| Rate and quota counters | One Durable Object per install id |
| Anthropic key, deployed | Worker secret `ANTHROPIC_API_KEY` |
| Anthropic key, on your machine | `proxy/.dev.vars`, which git ignores |
| URL the app calls | `skim.aiProxyUrl` in `local.properties` |

Durable Objects keep the counters. A request to one install id runs on one object, so two requests cannot both take the last slot. KV is eventually consistent, and that race would let a new install past the tighter limit.

The starting numbers are in `proxy/src/policy.ts`. A new install is one whose first accepted request was under 24 hours ago. A new install gets 5 requests a minute and 30 a day. An older install gets 20 a minute and 300 a day. The Anthropic spend cap is separate, and you set it in the Anthropic console.

## Run Ask Skim on a device

You need a Cloudflare account only if you deploy. Local `wrangler dev` does not.

1. From `proxy/`, install dependencies with `npm ci`.
2. Create `proxy/.dev.vars` with one line, `ANTHROPIC_API_KEY=`, and paste your own key after the equals sign. Do not commit that file.
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
4. Run `npx wrangler secret put ANTHROPIC_API_KEY` and paste the key when asked. The value is not stored in the repo. This command publishes a new version of the Worker.
5. Put that `https://` URL in `skim.aiProxyUrl` for the release build. Do not commit it. `gradle.properties` stays without that property.

Play Integrity is not part of this Worker. The app does not send an integrity token.

The Worker does not log page text, questions, or the install id. It prints no message body. Traces are off so a trace does not keep the request body. Workers Logs are on, and they only have what the Worker prints.
