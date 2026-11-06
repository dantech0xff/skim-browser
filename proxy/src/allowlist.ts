import { POLICY } from "./policy";

export type Reject = { ok: false; status: number; code: string };
export type Accept = { ok: true };
export type Verdict = Accept | Reject;

const ALLOWED = new Set<string>(POLICY.allowedKeys);

/** Top-level keys only. Nested message text is not inspected and is not logged. */
export function checkBody(value: unknown): Verdict {
  if (value === null || typeof value !== "object" || Array.isArray(value)) {
    return { ok: false, status: 400, code: "body" };
  }
  const body = value as Record<string, unknown>;
  for (const key of Object.keys(body)) {
    if (!ALLOWED.has(key)) {
      return { ok: false, status: 400, code: "keys" };
    }
  }
  if (body.model !== POLICY.model) {
    return { ok: false, status: 400, code: "model" };
  }
  if (typeof body.max_tokens !== "number" || !Number.isInteger(body.max_tokens)) {
    return { ok: false, status: 400, code: "max_tokens" };
  }
  if (body.max_tokens < 1 || body.max_tokens > POLICY.maxTokens) {
    return { ok: false, status: 400, code: "max_tokens" };
  }
  if (body.stream !== true) {
    return { ok: false, status: 400, code: "stream" };
  }
  if (!Array.isArray(body.messages)) {
    return { ok: false, status: 400, code: "messages" };
  }
  if ("system" in body && body.system !== null && typeof body.system !== "string" && !Array.isArray(body.system)) {
    return { ok: false, status: 400, code: "system" };
  }
  if ("output_config" in body) {
    const config = body.output_config;
    if (config === null || typeof config !== "object" || Array.isArray(config)) {
      return { ok: false, status: 400, code: "output_config" };
    }
  }
  return { ok: true };
}

const UUID =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export function checkInstall(header: string | null): Verdict {
  if (header === null || !UUID.test(header)) {
    return { ok: false, status: 401, code: "install" };
  }
  return { ok: true };
}
