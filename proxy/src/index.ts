import { checkBody, checkInstall, type Reject } from "./allowlist";
import { InstallQuota, type QuotaDecision } from "./quota";
import { POLICY } from "./policy";

export { InstallQuota };

export interface Env {
  ANTHROPIC_API_KEY: string;
  INSTALL_QUOTA: DurableObjectNamespace<InstallQuota>;
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    if (url.pathname !== "/v1/messages") {
      return reject(404, "path");
    }
    if (request.method !== "POST") {
      return reject(405, "method");
    }
    if (request.headers.get("anthropic-beta") !== null || request.headers.get("x-api-key") !== null) {
      return reject(400, "headers");
    }
    const install = checkInstall(request.headers.get("X-Skim-Install"));
    if (!install.ok) {
      return reject(install.status, install.code);
    }
    const bytes = await readBody(request);
    if (bytes instanceof Response) {
      return bytes;
    }
    let parsed: unknown;
    try {
      parsed = JSON.parse(new TextDecoder().decode(bytes));
    } catch {
      return reject(400, "body");
    }
    const verdict = checkBody(parsed);
    if (!verdict.ok) {
      return reject(verdict.status, verdict.code);
    }
    if (!env.ANTHROPIC_API_KEY) {
      return reject(500, "secret");
    }
    const quota = await reserve(env, request.headers.get("X-Skim-Install") as string);
    if (!quota.ok) {
      return reject(quota.status, quota.code, quota.retryAfter);
    }
    const upstream = await fetch(POLICY.upstream, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        accept: "text/event-stream",
        "x-api-key": env.ANTHROPIC_API_KEY,
        "anthropic-version": POLICY.anthropicVersion,
      },
      body: bytes,
    });
    return new Response(upstream.body, {
      status: upstream.status,
      headers: {
        "content-type": upstream.headers.get("content-type") ?? "text/event-stream",
        "cache-control": "no-store",
      },
    });
  },
} satisfies ExportedHandler<Env>;

async function reserve(env: Env, installId: string): Promise<QuotaDecision> {
  const stub = env.INSTALL_QUOTA.get(env.INSTALL_QUOTA.idFromName(installId));
  return stub.reserve(Date.now());
}

async function readBody(request: Request): Promise<Uint8Array | Response> {
  const declared = request.headers.get("content-length");
  if (declared !== null && Number(declared) > POLICY.maxBodyBytes) {
    return reject(413, "body_too_large");
  }
  if (request.body === null) {
    return reject(400, "body");
  }
  const reader = request.body.getReader();
  const chunks: Uint8Array[] = [];
  let total = 0;
  while (true) {
    const step = await reader.read();
    if (step.done) {
      break;
    }
    total += step.value.byteLength;
    if (total > POLICY.maxBodyBytes) {
      return reject(413, "body_too_large");
    }
    chunks.push(step.value);
  }
  const body = new Uint8Array(total);
  let offset = 0;
  for (const chunk of chunks) {
    body.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return body;
}

function reject(status: number, code: string, retryAfter?: number): Response {
  const headers: Record<string, string> = { "content-type": "application/json", "cache-control": "no-store" };
  if (retryAfter !== undefined) {
    headers["retry-after"] = String(retryAfter);
  }
  return new Response(JSON.stringify({ error: code }), { status, headers });
}

export type { Reject };
