import { createExecutionContext, runInDurableObject, waitOnExecutionContext } from "cloudflare:test";
import { env } from "cloudflare:workers";
import { http, HttpResponse } from "msw";
import { afterEach, describe, expect, it, vi } from "vitest";
import worker from "../src/index";
import { POLICY } from "../src/policy";
import { network } from "./network";

const INSTALL = "11111111-1111-4111-8111-111111111111";
const OTHER = "22222222-2222-4222-8222-222222222222";
const PAGE = "PAGE_TEXT_MARKER question about the page";

function body(extra: Record<string, unknown> = {}, maxTokens = 1024): string {
  return JSON.stringify({
    model: POLICY.model,
    max_tokens: maxTokens,
    system: "You are Skim.",
    messages: [{ role: "user", content: PAGE }],
    output_config: { effort: "medium" },
    stream: true,
    ...extra,
  });
}

function request(init: { install?: string | null; json?: string; headers?: HeadersInit; ip?: string } = {}): Request {
  const headers = new Headers(init.headers);
  headers.set("content-type", "application/json");
  headers.set("CF-Connecting-IP", init.ip ?? crypto.randomUUID());
  if (init.install !== null) {
    headers.set("X-Skim-Install", init.install ?? INSTALL);
  }
  return new Request("https://skim-proxy.example/v1/messages", {
    method: "POST",
    headers,
    body: init.json ?? body(),
  });
}

async function call(req: Request, key: string = "test-proxy-secret"): Promise<Response> {
  const ctx = createExecutionContext();
  const response = await worker.fetch(req, { ...env, ANTHROPIC_API_KEY: key }, ctx);
  await waitOnExecutionContext(ctx);
  return response;
}

function sse(text: string): HttpResponse {
  return new HttpResponse(text, { headers: { "content-type": "text/event-stream" } });
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe("proxy contract", () => {
  it("forwards a valid body unchanged and streams the reply", async () => {
    const json = body();
    let seenBody = "";
    let seenKey: string | null = null;
    let seenVersion: string | null = null;
    network.use(
      http.post(POLICY.upstream, async ({ request: upstream }) => {
        seenBody = await upstream.text();
        seenKey = upstream.headers.get("x-api-key");
        seenVersion = upstream.headers.get("anthropic-version");
        return sse("data: one\n\ndata: two\n\n");
      }),
    );
    const logs: unknown[][] = [];
    for (const method of ["log", "info", "warn", "error"] as const) {
      vi.spyOn(console, method).mockImplementation((...args: unknown[]) => {
        logs.push(args);
      });
    }
    const response = await call(request({ json }));
    expect(response.status).toBe(200);
    expect(await response.text()).toBe("data: one\n\ndata: two\n\n");
    expect(seenBody).toBe(json);
    expect(seenKey).toBe("test-proxy-secret");
    expect(seenVersion).toBe(POLICY.anthropicVersion);
    expect(JSON.stringify(logs)).not.toContain(PAGE);
  });

  it("returns the first SSE chunk before the upstream stream ends", async () => {
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
            controller.enqueue(encoder.encode("data: two\n\n"));
            controller.close();
          },
        });
        return new HttpResponse(stream, { headers: { "content-type": "text/event-stream" } });
      }),
    );
    const response = await call(request());
    const reader = response.body!.getReader();
    const first = await Promise.race([
      reader.read(),
      new Promise<never>((_, reject) => setTimeout(() => reject(new Error("buffered")), 1000)),
    ]);
    expect(new TextDecoder().decode(first.value)).toBe("data: one\n\n");
    release();
    const second = await reader.read();
    expect(new TextDecoder().decode(second.value)).toBe("data: two\n\n");
    await reader.read();
  });

  it("rejects a missing or non-UUID install id", async () => {
    expect((await call(request({ install: null }))).status).toBe(401);
    expect((await call(request({ install: "install-1" }))).status).toBe(401);
  });

  it("rejects a client key, a beta header, and a body the app does not send", async () => {
    let calls = 0;
    network.use(
      http.post(POLICY.upstream, () => {
        calls += 1;
        return sse("data: no\n\n");
      }),
    );
    expect((await call(request({ headers: { "x-api-key": "leaked" } }))).status).toBe(400);
    expect((await call(request({ headers: { "anthropic-beta": "messages-2024" } }))).status).toBe(400);
    expect((await call(request({ json: body({ tools: [] }) }))).status).toBe(400);
    expect((await call(request({ json: body({ thinking: { type: "enabled" } }) }))).status).toBe(400);
    expect((await call(request({ json: body({ temperature: 0 }) }))).status).toBe(400);
    expect((await call(request({ json: body({ model: "claude-other" }) }))).status).toBe(400);
    expect((await call(request({ json: body({}, 8193) }))).status).toBe(400);
    expect((await call(request({ json: body({ stream: false }) }))).status).toBe(400);
    expect(calls).toBe(0);
    const error = await call(request({ json: body({ tools: [{ name: PAGE }] }) }));
    expect(await error.json()).toEqual({ error: "keys" });
  });

  it("rejects a body over the byte cap", async () => {
    const huge = "x".repeat(POLICY.maxBodyBytes + 1);
    const response = await call(request({ json: huge }));
    expect(response.status).toBe(413);
  });

  it("limits a new install more tightly than an old one", async () => {
    network.use(http.post(POLICY.upstream, () => sse("data: ok\n\n")));
    for (let i = 0; i < POLICY.newInstall.perMinute; i += 1) {
      expect((await call(request())).status).toBe(200);
    }
    const blocked = await call(request());
    expect(blocked.status).toBe(429);
    expect(await blocked.json()).toEqual({ error: "rate_limited" });

    const id = env.INSTALL_QUOTA.idFromName(OTHER);
    const stub = env.INSTALL_QUOTA.get(id);
    const now = Date.now();
    await runInDurableObject(stub, async (_instance, state) => {
      await state.storage.put("counters", {
        firstSeen: now - POLICY.newWindowMs - 1000,
        minuteStart: now,
        minuteCount: 0,
        dayStart: now,
        dayCount: 0,
      });
    });
    for (let i = 0; i < POLICY.newInstall.perMinute + 1; i += 1) {
      expect((await call(request({ install: OTHER }))).status).toBe(200);
    }
  });

  it("does not share a quota across install ids", async () => {
    network.use(http.post(POLICY.upstream, () => sse("data: ok\n\n")));
    for (let i = 0; i < POLICY.newInstall.perMinute; i += 1) {
      expect((await call(request())).status).toBe(200);
    }
    expect((await call(request({ install: OTHER }))).status).toBe(200);
  });

  it("refuses to call upstream when the secret is missing", async () => {
    let calls = 0;
    network.use(
      http.post(POLICY.upstream, () => {
        calls += 1;
        return sse("data: no\n\n");
      }),
    );
    const response = await call(request(), "");
    expect(response.status).toBe(500);
    expect(calls).toBe(0);
  });
});
