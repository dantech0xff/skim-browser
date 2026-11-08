import { DurableObject } from "cloudflare:workers";
import { checkInstall } from "./allowlist";
import { POLICY } from "./policy";
import type { InstallQuota } from "./quota";

const UUID =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const VERSION = /^[A-Za-z0-9._+-]{1,32}$/;
const FIELDS = ["answerId", "revision", "rating", "surface", "appVersion"] as const;
const RATINGS = new Set(["good", "poor", "none"]);
const SURFACES = new Set(["page", "tabs"]);
const LEDGER = "ledger";
const DAY_MS = 24 * 60 * 60 * 1000;

export type Rating = "good" | "poor" | "none";
export type Surface = "page" | "tabs";

export type FeedbackInput = {
  answerId: string;
  revision: number;
  rating: Rating;
  surface: Surface;
  appVersion: string;
};

export type RecordResult =
  | { ok: true; applied: boolean; storedRevision: number }
  | { ok: false; status: 409; code: "conflict" };

type RatingRow = {
  answer_id: string;
  revision: number;
  rating: Rating;
  surface: Surface;
  app_version: string;
  updated_at: number;
};

/**
 * One ledger for every rating. A rating is one row per answer, replaced when a
 * newer revision arrives, so a retry or a late older event does not add a count.
 * The install id is not a column. Retention is [POLICY.feedbackRetentionMs].
 */
export class FeedbackStore extends DurableObject {
  constructor(ctx: DurableObjectState, env: Env) {
    super(ctx, env);
    this.ctx.storage.sql.exec(
      `CREATE TABLE IF NOT EXISTS ratings (
        answer_id TEXT PRIMARY KEY,
        revision INTEGER NOT NULL,
        rating TEXT NOT NULL,
        surface TEXT NOT NULL,
        app_version TEXT NOT NULL,
        updated_at INTEGER NOT NULL
      )`,
    );
  }

  async record(input: FeedbackInput, now: number): Promise<RecordResult> {
    const existing = this.row(input.answerId);
    if (existing === null) {
      this.ctx.storage.sql.exec(
        `INSERT INTO ratings (answer_id, revision, rating, surface, app_version, updated_at)
         VALUES (?, ?, ?, ?, ?, ?)`,
        input.answerId,
        input.revision,
        input.rating,
        input.surface,
        input.appVersion,
        now,
      );
      await this.ensureAlarm(now);
      return { ok: true, applied: true, storedRevision: input.revision };
    }
    if (input.revision < existing.revision) {
      return { ok: true, applied: false, storedRevision: existing.revision };
    }
    if (input.revision === existing.revision) {
      const same =
        existing.rating === input.rating &&
        existing.surface === input.surface &&
        existing.app_version === input.appVersion;
      if (!same) return { ok: false, status: 409, code: "conflict" };
      return { ok: true, applied: false, storedRevision: existing.revision };
    }
    this.ctx.storage.sql.exec(
      `UPDATE ratings
       SET revision = ?, rating = ?, surface = ?, app_version = ?, updated_at = ?
       WHERE answer_id = ?`,
      input.revision,
      input.rating,
      input.surface,
      input.appVersion,
      now,
      input.answerId,
    );
    return { ok: true, applied: true, storedRevision: input.revision };
  }

  async summary(): Promise<FeedbackSummary> {
    const counts = emptyCounts();
    const bySurface: Record<Surface, FeedbackCounts> = { page: emptyCounts(), tabs: emptyCounts() };
    const byAppVersion: Record<string, FeedbackCounts> = {};
    const rows = this.ctx.storage.sql
      .exec(`SELECT rating, surface, app_version, COUNT(*) AS n FROM ratings GROUP BY rating, surface, app_version`)
      .toArray() as { rating: Rating; surface: Surface; app_version: string; n: number }[];
    for (const row of rows) {
      const n = Number(row.n);
      counts[row.rating] += n;
      bySurface[row.surface][row.rating] += n;
      const version = byAppVersion[row.app_version] ?? emptyCounts();
      version[row.rating] += n;
      byAppVersion[row.app_version] = version;
    }
    return { good: counts.good, poor: counts.poor, none: counts.none, bySurface, byAppVersion };
  }

  async prune(now: number): Promise<number> {
    const cutoff = now - POLICY.feedbackRetentionMs;
    const before = this.count();
    this.ctx.storage.sql.exec(`DELETE FROM ratings WHERE updated_at < ?`, cutoff);
    return before - this.count();
  }

  async alarm(): Promise<void> {
    const now = Date.now();
    await this.prune(now);
    await this.ctx.storage.setAlarm(now + DAY_MS);
  }

  private row(answerId: string): RatingRow | null {
    return (
      this.ctx.storage.sql
        .exec(`SELECT answer_id, revision, rating, surface, app_version, updated_at FROM ratings WHERE answer_id = ?`, answerId)
        .toArray()[0] as RatingRow | undefined
    ) ?? null;
  }

  private count(): number {
    const row = this.ctx.storage.sql.exec(`SELECT COUNT(*) AS n FROM ratings`).one() as { n: number };
    return Number(row.n);
  }

  private async ensureAlarm(now: number): Promise<void> {
    if ((await this.ctx.storage.getAlarm()) === null) {
      await this.ctx.storage.setAlarm(now + DAY_MS);
    }
  }
}

export type FeedbackCounts = { good: number; poor: number; none: number };

export type FeedbackSummary = FeedbackCounts & {
  bySurface: Record<Surface, FeedbackCounts>;
  byAppVersion: Record<string, FeedbackCounts>;
};

function emptyCounts(): FeedbackCounts {
  return { good: 0, poor: 0, none: 0 };
}

export type FeedbackEnv = {
  FEEDBACK_READ_TOKEN?: string;
  INSTALL_QUOTA: DurableObjectNamespace<InstallQuota>;
  FEEDBACK: DurableObjectNamespace<FeedbackStore>;
};

/** POST /v1/feedback and GET /v1/feedback/summary. Neither path logs the body or the install id. */
export async function handleFeedback(request: Request, env: FeedbackEnv, pathname: string): Promise<Response> {
  if (pathname === "/v1/feedback/summary") {
    if (request.method !== "GET") return feedbackReject(405, "method");
    const token = env.FEEDBACK_READ_TOKEN;
    if (!token || request.headers.get("authorization") !== `Bearer ${token}`) {
      return feedbackReject(401, "summary");
    }
    const summary = await ledger(env).summary();
    return Response.json(summary, { headers: { "cache-control": "no-store" } });
  }
  if (request.method !== "POST") return feedbackReject(405, "method");
  const install = checkInstall(request.headers.get("X-Skim-Install"));
  if (!install.ok) return feedbackReject(install.status, install.code);
  const bytes = await readLimited(request, POLICY.feedbackMaxBodyBytes);
  if (bytes instanceof Response) return bytes;
  let parsed: unknown;
  try {
    parsed = JSON.parse(new TextDecoder().decode(bytes));
  } catch {
    return feedbackReject(400, "body");
  }
  const input = parseFeedback(parsed);
  if (!input.ok) return feedbackReject(input.status, input.code);
  const installId = request.headers.get("X-Skim-Install") as string;
  const gate = await env.INSTALL_QUOTA.get(env.INSTALL_QUOTA.idFromName(installId)).reserveFeedback(Date.now());
  if (!gate.ok) return feedbackReject(429, "feedback_rate_limited", gate.retryAfter);
  const recorded = await ledger(env).record(input.value, Date.now());
  if (!recorded.ok) return feedbackReject(recorded.status, recorded.code);
  return Response.json(
    { applied: recorded.applied, storedRevision: recorded.storedRevision },
    { headers: { "cache-control": "no-store" } },
  );
}

function ledger(env: FeedbackEnv): DurableObjectStub<FeedbackStore> {
  return env.FEEDBACK.get(env.FEEDBACK.idFromName(LEDGER));
}

export function parseFeedback(value: unknown): { ok: true; value: FeedbackInput } | { ok: false; status: number; code: string } {
  if (value === null || typeof value !== "object" || Array.isArray(value)) {
    return { ok: false, status: 400, code: "body" };
  }
  const body = value as Record<string, unknown>;
  const keys = Object.keys(body);
  if (keys.length !== FIELDS.length || FIELDS.some((field) => !keys.includes(field))) {
    return { ok: false, status: 400, code: "keys" };
  }
  if (typeof body.answerId !== "string" || !UUID.test(body.answerId)) {
    return { ok: false, status: 400, code: "answer_id" };
  }
  if (typeof body.revision !== "number" || !Number.isInteger(body.revision) || body.revision < 1 || body.revision > 1_000_000) {
    return { ok: false, status: 400, code: "revision" };
  }
  if (typeof body.rating !== "string" || !RATINGS.has(body.rating)) {
    return { ok: false, status: 400, code: "rating" };
  }
  if (typeof body.surface !== "string" || !SURFACES.has(body.surface)) {
    return { ok: false, status: 400, code: "surface" };
  }
  if (typeof body.appVersion !== "string" || !VERSION.test(body.appVersion)) {
    return { ok: false, status: 400, code: "app_version" };
  }
  return {
    ok: true,
    value: {
      answerId: body.answerId,
      revision: body.revision,
      rating: body.rating as Rating,
      surface: body.surface as Surface,
      appVersion: body.appVersion,
    },
  };
}

async function readLimited(request: Request, max: number): Promise<Uint8Array | Response> {
  const declared = request.headers.get("content-length");
  if (declared !== null && Number(declared) > max) return feedbackReject(413, "body_too_large");
  if (request.body === null) return feedbackReject(400, "body");
  const reader = request.body.getReader();
  const chunks: Uint8Array[] = [];
  let total = 0;
  while (true) {
    const step = await reader.read();
    if (step.done) break;
    total += step.value.byteLength;
    if (total > max) return feedbackReject(413, "body_too_large");
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

function feedbackReject(status: number, code: string, retryAfter?: number): Response {
  const headers: Record<string, string> = { "content-type": "application/json", "cache-control": "no-store" };
  if (retryAfter !== undefined) headers["retry-after"] = String(retryAfter);
  return new Response(JSON.stringify({ error: code }), { status, headers });
}
