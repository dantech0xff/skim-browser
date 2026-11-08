import { cloudflareTest } from "@cloudflare/vitest-plugin";
import { defineConfig } from "vitest/config";

export default defineConfig({
  plugins: [
    cloudflareTest({
      wrangler: { configPath: "./wrangler.jsonc" },
      miniflare: {
        bindings: {
          ANTHROPIC_API_KEY: "test-proxy-secret",
          IP_HASH_SALT: "test-ip-salt",
          FEEDBACK_READ_TOKEN: "test-read-token",
        },
      },
    }),
  ],
  test: {
    setupFiles: ["./test/setup.ts"],
  },
});
