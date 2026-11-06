/**
 * Starting limits. A new install is one whose first accepted request was
 * under 24 hours ago. Edit these before deploy if the spend cap needs a
 * different shape. They are not the Anthropic console cap.
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
} as const;

export type LimitPair = { perMinute: number; perDay: number };
