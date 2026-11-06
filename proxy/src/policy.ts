/**
 * Starting limits. Edit these before deploy if the spend shape should change.
 * They are not the Anthropic console cap.
 *
 * A new install is one whose first accepted request was under 24 hours ago.
 * Windows are fixed: a counter resets 24 hours after that window started, not
 * on a sliding clock.
 *
 * Spend is an estimate for claude-haiku-5-5 using the published Haiku 4.5
 * rates ($1 / million input tokens, $5 / million output tokens). One
 * microdollar is one millionth of a dollar, so those rates are 1 and 5
 * microdollars per token. Cache tokens are counted with input.
 */
export const POLICY = {
  model: "claude-haiku-5-5",
  maxTokens: 8192,
  maxBodyBytes: 2 * 1024 * 1024,
  anthropicVersion: "2023-06-01",
  upstream: "https://api.anthropic.com/v1/messages",
  newWindowMs: 24 * 60 * 60 * 1000,
  minuteMs: 60 * 1000,
  dayMs: 24 * 60 * 60 * 1000,
  allowedKeys: ["model", "max_tokens", "system", "messages", "output_config", "stream"] as const,
  newInstall: { perMinute: 5, perDay: 30 },
  established: { perMinute: 20, perDay: 300 },
  /** Request counts for one hashed client IP, across every install id. */
  ip: { perMinute: 40, perDay: 500 },
  inputMicroPerToken: 1,
  outputMicroPerToken: 5,
  /** Bytes of request body reserved as one input token. Three overestimates English. */
  inputBytesPerToken: 3,
  /** $1, $2, and $20 over each fixed 24 hour window. */
  installCapMicro: 1_000_000,
  ipCapMicro: 2_000_000,
  globalCapMicro: 20_000_000,
} as const;

export type LimitPair = { perMinute: number; perDay: number };
