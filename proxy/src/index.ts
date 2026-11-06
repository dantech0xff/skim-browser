import { checkBody, checkInstall, type Reject } from "./allowlist";
import { AbuseLedger, clientIp, hashIp, type Gate } from "./ledger";
import { POLICY } from "./policy";
import { InstallQuota, type QuotaDecision } from "./quota";
import { costMicro, createUsageTap, reserveMicro, type Usage } from "./usage";

export { AbuseLedger, InstallQuota };

export interface Env {
  ANTHROPIC_API_KEY: string;
  IP_HASH_SALT: string;
  INSTALL_QUOTA: DurableObjectNamespace<InstallQuota>;
  ABUSE_LEDGER: DurableObjectNamespace<AbuseLedger>;
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
    const installId = request.headers.get("X-Skim-Install") as string;
    const ip = clientIp(request);
    if (!ip.ok) {
      return reject(400, "ip");
    }
    if (!env.IP_HASH_SALT) {
      return reject(500, "salt");
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
    const now = Date.now();
    const ipKey = await hashIp(ip.ip, env.IP_HASH_SALT);
    const ipLedger = ledger(env, `ip:${ipKey}`);
    const ipRate = await ipLedger.reserveRate(now, POLICY.ip);
    if (!ipRate.ok) {
      return reject(429, "ip_rate_limited", ipRate.retryAfter);
    }
    const quota = await reserveInstall(env, installId, now);
    if (!quota.ok) {
      return reject(quota.status, quota.code, quota.retryAfter);
    }
    const maxTokens = (parsed as { max_tokens: number }).max_tokens;
    const reserved = reserveMicro(bytes.byteLength, maxTokens);
    const installLedger = ledger(env, `install:${installId}`);
    const globalLedger = ledger(env, "global");
    const installHold = await installLedger.reserveSpend(now, reserved, POLICY.installCapMicro);
    if (!installHold.ok) {
      return reject(429, "install_cap", installHold.retryAfter);
    }
    const ipHold = await ipLedger.reserveSpend(now, reserved, POLICY.ipCapMicro);
    if (!ipHold.ok) {
      await installLedger.settle(now, reserved, 0);
      return reject(429, "ip_cap", ipHold.retryAfter);
    }
    const globalHold = await globalLedger.reserveSpend(now, reserved, POLICY.globalCapMicro);
    if (!globalHold.ok) {
      await installLedger.settle(now, reserved, 0);
      await ipLedger.settle(now, reserved, 0);
      return reject(429, "global_cap", globalHold.retryAfter);
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
    const settle = (usage: Usage, saw: boolean) =>
      settleAll(installLedger, ipLedger, globalLedger, now, reserved, saw ? costMicro(usage) : reserved);
    if (!upstream.ok || upstream.body === null) {
      await settle({ input: 0, output: 0 }, true);
      return new Response(upstream.body, {
        status: upstream.status,
        headers: passHeaders(upstream),
      });
    }
    return new Response(trackUsage(upstream.body, settle), {
      status: upstream.status,
      headers: passHeaders(upstream),
    });
  },
} satisfies ExportedHandler<Env>;

function ledger(env: Env, name: string): DurableObjectStub<AbuseLedger> {
  return env.ABUSE_LEDGER.get(env.ABUSE_LEDGER.idFromName(name));
}

async function reserveInstall(env: Env, installId: string, now: number): Promise<QuotaDecision> {
  const stub = env.INSTALL_QUOTA.get(env.INSTALL_QUOTA.idFromName(installId));
  return stub.reserve(now);
}

async function settleAll(
  installLedger: DurableObjectStub<AbuseLedger>,
  ipLedger: DurableObjectStub<AbuseLedger>,
  globalLedger: DurableObjectStub<AbuseLedger>,
  now: number,
  reserved: number,
  actual: number,
) {
  await installLedger.settle(now, reserved, actual);
  await ipLedger.settle(now, reserved, actual);
  await globalLedger.settle(now, reserved, actual);
}

function trackUsage(
  body: ReadableStream<Uint8Array>,
  onDone: (usage: Usage, saw: boolean) => Promise<void>,
): ReadableStream<Uint8Array> {
  const reader = body.getReader();
  const tap = createUsageTap();
  let settled = false;
  const finish = async () => {
    if (settled) return;
    settled = true;
    tap.finish();
    try {
      await onDone(tap.usage, tap.saw);
    } catch {
      console.error("settle_failed");
    }
  };
  return new ReadableStream({
    async pull(controller) {
      const step = await reader.read();
      if (step.done) {
        await finish();
        controller.close();
        return;
      }
      tap.push(step.value);
      controller.enqueue(step.value);
    },
    async cancel() {
      await finish();
      await reader.cancel();
    },
  });
}

function passHeaders(upstream: Response): Headers {
  const headers = new Headers();
  headers.set("content-type", upstream.headers.get("content-type") ?? "text/event-stream");
  headers.set("cache-control", "no-store");
  return headers;
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

export type { Gate, Reject };
