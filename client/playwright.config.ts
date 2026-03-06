import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  use: {
    baseURL: 'http://localhost:3000',
    headless: true,
    navigationTimeout: 10_000,
  },
  webServer: {
    command: 'npm run dev',
    port: 3000,
    reuseExistingServer: true
  },
  fullyParallel: false,
  expect: {
    timeout: 10_000,   // applies to all expect() calls globally
  },
  retries: process.env.CI ? 2 : 0,
  workers: 1,
  projects: [
    {
      name: 'all-except-dicom',
      testIgnore: '**/dicom.spec.ts',
    },
    {
      name: 'dicom',  // need serial execution for stateful viewer tests
      testMatch: '**/dicom.spec.ts',
      dependencies: ['all-except-dicom'],
    },
  ],

});
