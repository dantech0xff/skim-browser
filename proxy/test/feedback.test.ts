import { env, runDurableObjectAlarm } from "cloudflare:test";
import { createExecutionContext, waitOnExecutionContext } from "cloudflare:test";
import { describe, expect, it } from "vitest";
import worker from "../src/index";
import { POLICY } from "../src/policy";

const INSTALL = "11111111-1111-4111-8111-111111111111";
const ANSWER = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee";

function rating(extra: Record<string, unknown> = {}, revision = 1): string {
  return JSON.stringify({
    answerId: ANSWER,
    revision,
    rating: "good",
    surface: "page",
    appVersion: "1.0",
    ...extra,
  });
}

function request(init: { json?: string; install?: string | null; method?: string; path?: string; auth?: string } = {}): Request {
  const headers = new Headers({ "content-type": "application/json", "CF-Connecting-IP": "203.0.113.8" });
  if (init.install !== null) headers.set("X-Skim-Install", init.install ?? INSTALL);
  if (init.auth) headers.set("authorization", init.auth);
  return new Request(`https://skim-proxy.example${init.path ?? "/v1/feedback"}`, {
    method: init.method ?? "POST",
    headers,
    body: init.method === "GET" ? undefined : (init.json ?? rating()),
  });
}

async function call(req: Request): Promise<Response> {
  const ctx = createExecutionContext();
  const response = await worker.fetch(req, env, ctx);
  await waitOnExecutionContext(ctx);
  return response;
}

async function summary(): Promise<{ good: number; poor: number; none: number }> {
  const response = await call(request({ method: "GET", path: "/v1/feedback/summary", auth: "Bearer test-read-token" }));
  expect(response.status).toBe(200);
  return response.json();
}

describe("rating ledger", () => {
  it("stores one row for good, replaces it with poor, and clears it without adding rows", async () => {
    expect((await call(request())).status).toBe(200);
    expect(await summary()).toMatchObject({ good: 1, poor: 0, none: 0 });
    const poor = await call(request({ json: rating({ rating: "poor" }, 2) }));
    expect(await poor.json()).toMatchObject({ applied: true, storedRevision: 2 });
    expect(await summary()).toMatchObject({ good: 0, poor: 1, none: 0 });
    await call(request({ json: rating({ rating: "none" }, 3) }));
    expect(await summary()).toMatchObject({ good: 0, poor: 0, none: 1 });
  });

  it("treats a retry of the same revision as a no-op and ignores an older revision", async () => {
    await call(request({ json: rating({ rating: "good" }, 2) }));
    const retry = await call(request({ json: rating({ rating: "good" }, 2) }));
    expect(await retry.json()).toMatchObject({ applied: false, storedRevision: 2 });
    const late = await call(request({ json: rating({ rating: "poor" }, 1) }));
    expect(await late.json()).toMatchObject({ applied: false, storedRevision: 2 });
    expect(await summary()).toMatchObject({ good: 1, poor: 0, none: 0 });
  });

  it("rejects a repeated revision that names a different rating", async () => {
    await call(request({ json: rating({ rating: "good" }, 1) }));
    const conflict = await call(request({ json: rating({ rating: "poor" }, 1) }));
    expect(conflict.status).toBe(409);
    expect(await summary()).toMatchObject({ good: 1, poor: 0 });
  });

  it("rejects an unknown field, a huge body, and a missing install id", async () => {
    expect((await call(request({ json: rating({ page: "https://example.com" }) }))).status).toBe(400);
    expect((await call(request({ json: "x".repeat(POLICY.feedbackMaxBodyBytes + 1) }))).status).toBe(413);
    expect((await call(request({ install: null }))).status).toBe(401);
    expect(await summary()).toMatchObject({ good: 0, poor: 0, none: 0 });
  });

  it("does not store the install id in the ledger", async () => {
    await call(request());
    const stub = env.FEEDBACK.get(env.FEEDBACK.idFromName("ledger"));
    const stored = await stub.summary();
    expect(JSON.stringify(stored)).not.toContain(INSTALL);
  });

  it("stops a burst from one install and still accepts another", async () => {
    for (let i = 0; i < POLICY.feedbackPerMinute; i++) {
      const id = `aaaaaaaa-bbbb-4ccc-8ddd-${String(i).padStart(12, "0")}`;
      const response = await call(request({ json: rating({ answerId: id }) }));
      expect(response.status).toBe(200);
    }
    expect((await call(request({ json: rating({ answerId: "aaaaaaaa-bbbb-4ccc-8ddd-000000000099" }) }))).status).toBe(429);
  });

  it("drops a rating older than the retention window", async () => {
    await call(request());
    const stub = env.FEEDBACK.get(env.FEEDBACK.idFromName("ledger"));
    const removed = await stub.prune(Date.now() + POLICY.feedbackRetentionMs + 1000);
    expect(removed).toBe(1);
    expect(await summary()).toMatchObject({ good: 0, poor: 0, none: 0 });
  });

  it("prunes from the alarm", async () => {
    await call(request());
    const stub = env.FEEDBACK.get(env.FEEDBACK.idFromName("ledger"));
    await stub.prune(Date.now() + POLICY.feedbackRetentionMs + 1000);
    await runDurableObjectAlarm(stub);
    expect(await summary()).toMatchObject({ good: 0 });
  });

  it("hides the summary without the read token", async () => {
    expect((await call(request({ method: "GET", path: "/v1/feedback/summary" }))).status).toBe(401);
  });
});
