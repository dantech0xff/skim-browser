import { DurableObject } from "cloudflare:workers";
import { POLICY, type LimitPair } from "./policy";

export type QuotaDecision =
  | { ok: true }
  | { ok: false; status: 429; code: "rate_limited" | "quota"; retryAfter: number };

type Counters = {
  firstSeen: number;
  minuteStart: number;
  minuteCount: number;
  dayStart: number;
  dayCount: number;
};

/**
 * One object per install id. Requests to that object are serialized, so two
 * in-flight calls cannot both pass the same remaining slot. KV would let them.
 */
export class InstallQuota extends DurableObject {
  async reserve(now: number): Promise<QuotaDecision> {
    const stored = await this.ctx.storage.get<Counters>("counters");
    const counters: Counters = stored ?? {
      firstSeen: now,
      minuteStart: now,
      minuteCount: 0,
      dayStart: now,
      dayCount: 0,
    };
    if (now - counters.minuteStart >= POLICY.minuteMs) {
      counters.minuteStart = now;
      counters.minuteCount = 0;
    }
    if (now - counters.dayStart >= POLICY.dayMs) {
      counters.dayStart = now;
      counters.dayCount = 0;
    }
    const limits: LimitPair =
      now - counters.firstSeen < POLICY.newWindowMs ? POLICY.newInstall : POLICY.established;
    if (counters.minuteCount >= limits.perMinute) {
      return {
        ok: false,
        status: 429,
        code: "rate_limited",
        retryAfter: Math.max(1, Math.ceil((POLICY.minuteMs - (now - counters.minuteStart)) / 1000)),
      };
    }
    if (counters.dayCount >= limits.perDay) {
      return {
        ok: false,
        status: 429,
        code: "quota",
        retryAfter: Math.max(1, Math.ceil((POLICY.dayMs - (now - counters.dayStart)) / 1000)),
      };
    }
    counters.minuteCount += 1;
    counters.dayCount += 1;
    await this.ctx.storage.put("counters", counters);
    return { ok: true };
  }
}
