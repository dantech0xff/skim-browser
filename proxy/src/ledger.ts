import { DurableObject } from "cloudflare:workers";
import { POLICY, type LimitPair } from "./policy";

export type Gate = { ok: true } | { ok: false; retryAfter: number };

type SpendWindow = { windowStart: number; spent: number; reserved: number };
type RateWindow = { minuteStart: number; minuteCount: number; dayStart: number; dayCount: number };

const SPEND = "spend";
const RATE = "rate";

/**
 * One object per hashed IP, per install id, or the single name `global`.
 * Storage holds counters only. The IP itself is not written.
 * An alarm deletes the object once its windows have expired, so a hashed IP
 * is not kept after the cap window.
 */
export class AbuseLedger extends DurableObject {
  async reserveRate(now: number, limits: LimitPair): Promise<Gate> {
    const rate = rollRate(await this.ctx.storage.get<RateWindow>(RATE), now);
    if (rate.minuteCount >= limits.perMinute) {
      return { ok: false, retryAfter: secondsUntil(now, rate.minuteStart, POLICY.minuteMs) };
    }
    if (rate.dayCount >= limits.perDay) {
      return { ok: false, retryAfter: secondsUntil(now, rate.dayStart, POLICY.dayMs) };
    }
    rate.minuteCount += 1;
    rate.dayCount += 1;
    await this.ctx.storage.put(RATE, rate);
    await this.arm(now);
    return { ok: true };
  }

  async reserveSpend(now: number, amount: number, cap: number): Promise<Gate> {
    const spend = rollSpend(await this.ctx.storage.get<SpendWindow>(SPEND), now);
    if (spend.spent + spend.reserved + amount > cap) {
      return { ok: false, retryAfter: secondsUntil(now, spend.windowStart, POLICY.dayMs) };
    }
    spend.reserved += amount;
    await this.ctx.storage.put(SPEND, spend);
    await this.arm(now);
    return { ok: true };
  }

  /** Replaces this request's reservation with the settled amount. Zero refunds it. */
  async settle(now: number, reserved: number, actual: number): Promise<void> {
    const current = await this.ctx.storage.get<SpendWindow>(SPEND);
    if (!current) return;
    if (now - current.windowStart >= POLICY.dayMs) {
      await this.ctx.storage.put(SPEND, { windowStart: now, spent: actual, reserved: 0 });
    } else {
      current.reserved = Math.max(0, current.reserved - reserved);
      current.spent += actual;
      await this.ctx.storage.put(SPEND, current);
    }
    await this.arm(now);
  }

  async alarm(): Promise<void> {
    const now = Date.now();
    const spend = await this.ctx.storage.get<SpendWindow>(SPEND);
    const rate = await this.ctx.storage.get<RateWindow>(RATE);
    const spendDue = !spend || now >= spend.windowStart + POLICY.dayMs;
    const rateDue = !rate || (now >= rate.dayStart + POLICY.dayMs && now >= rate.minuteStart + POLICY.minuteMs);
    if (spendDue && rateDue) {
      await this.ctx.storage.deleteAll();
      return;
    }
    await this.arm(now);
  }

  private async arm(now: number) {
    const spend = await this.ctx.storage.get<SpendWindow>(SPEND);
    const rate = await this.ctx.storage.get<RateWindow>(RATE);
    const times = [
      spend ? spend.windowStart + POLICY.dayMs : undefined,
      rate ? rate.dayStart + POLICY.dayMs : undefined,
      rate ? rate.minuteStart + POLICY.minuteMs : undefined,
    ].filter((time): time is number => time !== undefined && time > now);
    if (times.length > 0) {
      await this.ctx.storage.setAlarm(Math.min(...times));
    }
  }
}

export async function hashIp(ip: string, salt: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(`${salt}\0${ip}`));
  return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

/**
 * Cloudflare sets CF-Connecting-IP on every edge request. A missing header
 * there is rejected. wrangler dev has no `cf` object, so local Ask Skim shares
 * one bucket named `local` instead of failing the documented loopback setup.
 */
export function clientIp(request: Request): { ok: true; ip: string } | { ok: false } {
  const header = request.headers.get("CF-Connecting-IP")?.trim() ?? "";
  if (header !== "") return { ok: true, ip: header };
  if (request.cf != null) return { ok: false };
  return { ok: true, ip: "local" };
}

function rollSpend(state: SpendWindow | undefined, now: number): SpendWindow {
  if (!state || now - state.windowStart >= POLICY.dayMs) {
    return { windowStart: now, spent: 0, reserved: 0 };
  }
  return state;
}

function rollRate(state: RateWindow | undefined, now: number): RateWindow {
  const rate: RateWindow = state ?? { minuteStart: now, minuteCount: 0, dayStart: now, dayCount: 0 };
  if (now - rate.minuteStart >= POLICY.minuteMs) {
    rate.minuteStart = now;
    rate.minuteCount = 0;
  }
  if (now - rate.dayStart >= POLICY.dayMs) {
    rate.dayStart = now;
    rate.dayCount = 0;
  }
  return rate;
}

function secondsUntil(now: number, start: number, windowMs: number): number {
  return Math.max(1, Math.ceil((windowMs - (now - start)) / 1000));
}
