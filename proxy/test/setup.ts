import { reset } from "cloudflare:test";
import { afterAll, afterEach, beforeAll } from "vitest";
import { network } from "./network";

beforeAll(() => {
  network.enable();
});

afterEach(async () => {
  network.resetHandlers();
  await reset();
});

afterAll(() => {
  network.disable();
});
