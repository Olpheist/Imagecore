import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./tests",
  fullyParallel: true,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  expect: {
    timeout: 10_000,
  },
  use: {
    baseURL: "http://localhost:3000",
    headless: true,
    navigationTimeout: 10_000,
    trace: "off",
    video: "off",
    screenshot: "only-on-failure",
  },
  webServer: {
    command: "npm run generate && npx serve dist -l 3000",
    url: "http://localhost:3000",
    reuseExistingServer: true,
    timeout: 120_000,
  }
});