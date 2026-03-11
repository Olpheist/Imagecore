import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  use: {
    baseURL: 'http://localhost:3000',
    headless: true,
    navigationTimeout: 10_000,
  },
  webServer: {
    command: "npm run generate && npx serve dist -l 3000",
    port: 3000,
    reuseExistingServer: true
  },
  fullyParallel: true,
  expect: {
    timeout: 10_000,   // applies to all expect() calls globally
  },
  retries: process.env.CI ? 2 : 0,
  workers: undefined,
  projects: [
    {
      name: 'all-except-dicom',
      testIgnore: '**/dicom.spec.ts',
    },
    {
      name: 'dicom',  // need serial execution for stateful viewer tests
      testMatch: '**/dicom.spec.ts',
      dependencies: ['all-except-dicom'],
      workers: 1
    },
  ],

});
