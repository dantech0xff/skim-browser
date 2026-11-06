import { createExecutionContext, runInDurableObject, waitOnExecutionContext } from "cloudflare:test";
import { env } from "cloudflare:workers";
import { http, HttpResponse } from "msw";
import { describe, expect, it, vi } from "vitest";
import worker from "../src/index";
import { hashIp } from "../src/ledger";
import { POLICY } from "../src/policy";
import { costMicro, reserveMicro } from "../src/usage";
import { network } from "./network";

const INSTALL = "11111111-1111-4111-8111-111111111111";
const OTHER = "22222222-2222-4222-8222-222222222222";
const IP = "203.0.113.10";
const OTHER_IP = "203.0.113.11";

function jsonBody(chars: number, maxTokens = 1): string {
  return JSON.stringify({
    model: POLICY.model,
    max_tokens: maxTokens,
    messages: [{ role: "user", content: "x".repeat(chars) }],
    stream: true,
  });
}

/** A body whose reservation is just over half the install cap and under the cap. */
function halfInstallBody(): string {
  let chars = 1000;
  let json = jsonBody(chars);
  while (reserveMicro(new TextEncoder().encode(json).byteLength, 1) <= POLICY.installCapMicro / 2) {
    chars += 50_000;
    json = jsonBody(chars);
  }
  const reserved = reserveMicro(new TextEncoder().encode(json).byteLength, 1);
  if (reserved >= POLICY.installCapMicro) {
    throw new Error(`reservation ${reserved} does not fit under the install cap`);
  }
  return json;
}

function request(init: { install?: string; ip?: string | null; json?: string; cf?: boolean } = {}): Request {
  const headers = new Headers({ "content-type": "application/json" });
  headers.set("X-Skim-Install", init.install ?? INSTALL);
  if (init.ip !== null) {
    headers.set("CF-Connecting-IP", init.ip ?? IP);
  }
  return new Request("https://skim-proxy.example/v1/messages", {
    method: "POST",
    headers,
    body: init.json ?? jsonBody(32),
    ...(init.cf ? { cf: { colo: "DFW" } } : {}),
  });
}

async function call(req: Request, salt: string = "test-ip-salt"): Promise<Response> {
  const ctx = createExecutionContext();
  const response = await worker.fetch(req, { ...env, IP_HASH_SALT: salt }, ctx);
  await waitOnExecutionContext(ctx);
  return response;
}

function sse(text: string): HttpResponse {
  return new HttpResponse(text, { headers: { "content-type": "text/event-stream" } });
}

const USAGE = [
  `data: ${JSON.stringify({
    type: "message_start",
    message: { usage: { input_tokens: 100, output_tokens: 1, cache_read_input_tokens: 4 } },
  })}`,
  `data: ${JSON.stringify({ type: "content_block_delta", delta: { type: "text_delta", text: "Hi" } })}`,
  `data: ${JSON.stringify({ type: "message_delta", delta: { stop_reason: "end_turn" }, usage: { output_tokens: 20 } })}`,
  `data: ${JSON.stringify({ type: "message_stop" })}`,
].join("\n\n");

describe("abuse caps", () => {
  it("rate limits one IP across install ids", async () => {
    network.use(http.post(POLICY.upstream, () => sse("data: ok\n\n")));
    const logs: unknown[][] = [];
    vi.spyOn(console, "error").mockImplementation((...args: unknown[]) => {
      logs.push(args);
    });
    for (let i = 0; i < POLICY.ip.perMinute; i += 1) {
      const install = `33333333-3333-4333-8333-${String(i).padStart(12, "0")}`;
      expect((await call(request({ install, json: jsonBody(8) }))).status).toBe(200);
    }
    const blocked = await call(request({ install: OTHER, json: jsonBody(8) }));
    expect(blocked.status).toBe(429);
    expect(blocked.headers.get("retry-after")).toBeTruthy();
    expect(await blocked.json()).toEqual({ error: "ip_rate_limited" });
    expect(JSON.stringify(logs)).not.toContain(IP);
  });

  it("rejects a missing client IP on the edge and a missing salt", async () => {
    const missing = await call(request({ ip: null, cf: true }));
    expect(missing.status).toBe(400);
    expect(await missing.json()).toEqual({ error: "ip" });
    const unsalted = await call(request(), "");
    expect(unsalted.status).toBe(500);
    expect(await unsalted.json()).toEqual({ error: "salt" });
  });

  it("caps spend for one install id", async () => {
    network.use(http.post(POLICY.upstream, () => sse(USAGE)));
    const json = halfInstallBody();
    expect((await call(request({ json }))).status).toBe(200);
    const blocked = await call(request({ json }));
    expect(blocked.status).toBe(429);
    expect(blocked.headers.get("retry-after")).toBeTruthy();
    expect(await blocked.json()).toEqual({ error: "install_cap" });
    expect((await call(request({ install: OTHER, json }))).status).toBe(200);
  });

  it("caps spend for one IP across install ids", async () => {
    network.use(http.post(POLICY.upstream, () => sse("data: ok\n\n")));
    const hash = await hashIp(IP, "test-ip-salt");
    const stub = env.ABUSE_LEDGER.get(env.ABUSE_LEDGER.idFromName(`ip:${hash}`));
    await runInDurableObject(stub, async (_instance, state) => {
      await state.storage.put("spend", {
        windowStart: Date.now(),
        spent: POLICY.ipCapMicro - 1,
        reserved: 0,
      });
    });
    const blocked = await call(request({ json: jsonBody(8) }));
    expect(blocked.status).toBe(429);
    expect(blocked.headers.get("retry-after")).toBeTruthy();
    expect(await blocked.json()).toEqual({ error: "ip_cap" });
    expect((await call(request({ install: OTHER, ip: OTHER_IP, json: jsonBody(8) }))).status).toBe(200);
  });

  it("caps global spend after each install and IP still has room", async () => {
    network.use(http.post(POLICY.upstream, () => sse("data: ok\n\n")));
    const now = Date.now();
    const globalId = env.ABUSE_LEDGER.idFromName("global");
    await runInDurableObject(env.ABUSE_LEDGER.get(globalId), async (_instance, state) => {
      await state.storage.put("spend", {
        windowStart: now,
        spent: POLICY.globalCapMicro - 1,
        reserved: 0,
      });
    });
    const blocked = await call(request({ json: jsonBody(8) }));
    expect(blocked.status).toBe(429);
    expect(blocked.headers.get("retry-after")).toBeTruthy();
    expect(await blocked.json()).toEqual({ error: "global_cap" });
  });

  it("lets only one of two concurrent reservations through", async () => {
    let release = () => {};
    const opened = new Promise<void>((resolve) => {
      release = resolve;
    });
    network.use(
      http.post(POLICY.upstream, () => {
        const encoder = new TextEncoder();
        const stream = new ReadableStream({
          async start(controller) {
            controller.enqueue(encoder.encode("data: one\n\n"));
            await opened;
            controller.close();
          },
        });
        return new HttpResponse(stream, { headers: { "content-type": "text/event-stream" } });
      }),
    );
    const json = halfInstallBody();
    const [first, second] = await Promise.all([call(request({ json })), call(request({ json }))]);
    const statuses = [first.status, second.status].sort();
    expect(statuses).toEqual([200, 429]);
    const denied = first.status === 429 ? first : second;
    expect(await denied.json()).toEqual({ error: "install_cap" });
    release();
    const allowed = first.status === 200 ? first : second;
    await allowed.text();
  });

  it("settles the reservation down to the streamed usage", async () => {
    network.use(http.post(POLICY.upstream, () => sse(USAGE)));
    const json = jsonBody(30, 100);
    const response = await call(request({ json }));
    expect(await response.text()).toContain("message_stop");
    const reserved = reserveMicro(new TextEncoder().encode(json).byteLength, 100);
    const actual = costMicro({ input: 104, output: 20 });
    expect(actual).toBeLessThan(reserved);
    const stub = env.ABUSE_LEDGER.get(env.ABUSE_LEDGER.idFromName(`install:${INSTALL}`));
    const spend = await runInDurableObject(stub, async (_instance, state) => state.storage.get("spend"));
    expect(spend).toMatchObject({ spent: actual, reserved: 0 });
  });

  it("refunds the reservation when upstream fails", async () => {
    network.use(http.post(POLICY.upstream, () => new HttpResponse("no", { status: 502 })));
    const json = halfInstallBody();
    expect((await call(request({ json }))).status).toBe(502);
    expect((await call(request({ json }))).status).toBe(502);
  });

  it("drops a hashed IP once its window has expired", async () => {
    const hash = await hashIp(IP, "test-ip-salt");
    const stub = env.ABUSE_LEDGER.get(env.ABUSE_LEDGER.idFromName(`ip:${hash}`));
    const old = Date.now() - POLICY.dayMs - 1000;
    await runInDurableObject(stub, async (instance, state) => {
      await state.storage.put("spend", { windowStart: old, spent: 10, reserved: 0 });
      await state.storage.put("rate", { minuteStart: old, minuteCount: 1, dayStart: old, dayCount: 1 });
      await instance.alarm();
      expect(await state.storage.get("spend")).toBeUndefined();
      expect(await state.storage.get("rate")).toBeUndefined();
    });
  });
});
