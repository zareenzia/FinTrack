import { defineConfig, devices } from '@playwright/test';

/**
 * TakaFlow — Playwright E2E configuration.
 *
 * IMPORTANT: This config intentionally does NOT define a `webServer` block.
 * The Spring Boot app under test talks to a live, shared Neon Postgres
 * instance with no test-specific datasource override for a plain `bootRun`,
 * so these tests must be pointed at an already-running, properly-configured
 * instance (local or staging) — never booted by this config.
 *
 * Point the suite at your instance via BASE_URL, e.g.:
 *   BASE_URL=http://localhost:8585 npx playwright test
 * Defaults to http://localhost:8585 (the app's `server.port` default).
 */
export default defineConfig({
  testDir: './tests',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 2 : undefined,
  reporter: [['html', { open: 'never' }], ['list']],

  // Generous but bounded timeouts — the app is a real Spring Boot service
  // backed by a remote Postgres database, so network round-trips are slower
  // than a purely local/mocked stack.
  timeout: 60_000,
  expect: {
    timeout: 10_000,
  },

  use: {
    baseURL: process.env.BASE_URL || 'http://localhost:8585',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    actionTimeout: 15_000,
    navigationTimeout: 30_000,
  },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
    {
      name: 'firefox',
      use: { ...devices['Desktop Firefox'] },
    },
    {
      name: 'webkit',
      use: { ...devices['Desktop Safari'] },
    },
  ],

  // No webServer: the target app must already be running (see BASE_URL above).
});
