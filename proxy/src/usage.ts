import { POLICY } from "./policy";

export type Usage = { input: number; output: number };

type RawUsage = {
  input_tokens?: number;
  output_tokens?: number;
  cache_creation_input_tokens?: number;
  cache_read_input_tokens?: number;
};

/**
 * Reads Anthropic SSE usage without holding the stream.
 * `message_start` carries input. `message_delta` carries the final output
 * total, so a later output value replaces the earlier one instead of adding.
 */
export function createUsageTap() {
  let buffer = "";
  let saw = false;
  const usage: Usage = { input: 0, output: 0 };
  const decoder = new TextDecoder();
  return {
    get usage() {
      return usage;
    },
    get saw() {
      return saw;
    },
    push(chunk: Uint8Array) {
      buffer += decoder.decode(chunk, { stream: true });
      buffer = drain(buffer, usage, () => {
        saw = true;
      });
    },
    finish() {
      buffer += decoder.decode();
      drain(`${buffer}\n`, usage, () => {
        saw = true;
      });
      buffer = "";
    },
  };
}

export function reserveMicro(bodyBytes: number, maxTokens: number): number {
  const inputTokens = Math.ceil(bodyBytes / POLICY.inputBytesPerToken);
  return inputTokens * POLICY.inputMicroPerToken + maxTokens * POLICY.outputMicroPerToken;
}

export function costMicro(usage: Usage): number {
  return usage.input * POLICY.inputMicroPerToken + usage.output * POLICY.outputMicroPerToken;
}

function drain(buffer: string, usage: Usage, onUsage: () => void): string {
  const lines = buffer.split("\n");
  const rest = lines.pop() ?? "";
  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed.startsWith("data:")) continue;
    const payload = trimmed.slice("data:".length).trim();
    if (payload === "" || payload === "[DONE]") continue;
    let event: { type?: string; message?: { usage?: RawUsage }; usage?: RawUsage };
    try {
      event = JSON.parse(payload) as typeof event;
    } catch {
      continue;
    }
    const raw = event.type === "message_start" ? event.message?.usage : event.type === "message_delta" ? event.usage : undefined;
    if (!raw) continue;
    applyUsage(usage, raw);
    onUsage();
  }
  return rest;
}

function applyUsage(usage: Usage, raw: RawUsage) {
  if (
    raw.input_tokens !== undefined ||
    raw.cache_creation_input_tokens !== undefined ||
    raw.cache_read_input_tokens !== undefined
  ) {
    usage.input =
      (raw.input_tokens ?? 0) + (raw.cache_creation_input_tokens ?? 0) + (raw.cache_read_input_tokens ?? 0);
  }
  if (raw.output_tokens !== undefined) {
    usage.output = raw.output_tokens;
  }
}
