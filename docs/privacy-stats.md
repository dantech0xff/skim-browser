# Blocking stats

Skim counts what the shield stops and keeps the totals on the device. Nothing in this count is sent off the phone.

## What is counted

A blocked network request increments the ads-and-trackers count for the local day. The compiled host list stores hashes of EasyList and EasyPrivacy together and does not keep which list a host came from, so those requests are one number.

A popup blocked by `onCreateWindow`, and a top-frame jump blocked as an odd redirect, each increment their own count.

A cosmetic element is an estimate. Skim counts each `{display:none}` rule in the hide stylesheet once per document, and one more for each blocked frame it collapses. It does not scan the page, so a rule that matches nothing is still counted.

A private tab adds to these counts. It does not add a site to the top-sites list.

## Data saved

The request is dropped before it is sent, so Skim never sees Content-Length. The WebView request also does not include a resource type. The estimate uses the last segment of the URL path.

| Suffix | Kind | Bytes |
|---|---|---|
| `js`, `mjs`, `cjs` | Script | 16 KiB |
| `png`, `jpg`, `jpeg`, `gif`, `webp`, `svg`, `avif`, `ico`, `bmp` | Image | 32 KiB |
| `json`, `xml` | XHR | 1 KiB |
| `mp4`, `webm`, `m4v`, `mp3`, `m4a`, `ogg`, `wav`, `aac`, `m3u8` | Media | 128 KiB |
| anything else, including no suffix | Other | 4 KiB |

Fetch and XHR URLs usually have no suffix, so they use the other average rather than the 1 KiB beacon.

## Time saved

```
timeSavedMillis = bytes / 125_000 * 1000 + (bytes % 125_000) * 1000 / 125_000 + requests * 50
```

125_000 bytes per second is 1 Mbps (`ONE_MEGABIT_BYTES_PER_SECOND`). 50 milliseconds is one round trip per blocked request (`ROUND_TRIP_MILLIS`). Popups, redirects, and cosmetic rules add neither term. The screen labels both data and time as estimates.

The constants and the integer arithmetic live in `domain/src/main/kotlin/com/dantech/skimbrowser/domain/shield/ShieldEstimate.kt`.

## Storage

`ShieldStatBuffer` holds the pending counts. The page-load path only updates that buffer. `ShieldStatRecorder` writes one batch about two seconds later, or sooner once 32 events are waiting. The batch is two Room tables, `shield_days` and `shield_hosts`, added in migration 3 to 4. A flush keeps at most 64 new sites. Reset drops the buffer and then deletes both tables.
