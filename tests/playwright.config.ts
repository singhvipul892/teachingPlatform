import { defineConfig } from '@playwright/test';
import { EPHEMERAL_BASE_URL, PROD_BASE_URL } from './src/config';

export default defineConfig({
  testDir: './specs',
  timeout: 30_000,
  expect: { timeout: 5_000 },
  fullyParallel: false, // destructive specs share one database
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: process.env.CI
    ? [['list'], ['html', { open: 'never' }], ['github']]
    : [['list'], ['html', { open: 'never' }]],

  projects: [
    // Waits for the throwaway stack, then seeds an admin and a student into it.
    {
      name: 'setup-local',
      testDir: './src',
      testMatch: /seed-ephemeral\.ts/,
      use: { baseURL: EPHEMERAL_BASE_URL },
    },

    // Everything, against the throwaway stack. Wiped by `npm run stack:down`.
    {
      name: 'local',
      dependencies: ['setup-local'],
      use: { baseURL: EPHEMERAL_BASE_URL },
    },

    // The single real environment. `grep` keeps destructive specs out; the
    // guard in src/guards.ts blocks any write that slips past it anyway.
    {
      name: 'prod',
      grep: /@readonly/,
      use: { baseURL: PROD_BASE_URL },
    },
  ],
});
