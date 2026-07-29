import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright configuration for E2E tests against the Dockerized frontend.
 *
 * This config assumes the full stack is already running via
 * `docker compose up` (or the dedicated `playwright` Compose service),
 * so no `webServer` is configured here.
 *
 * See docs/architecture/tech-stack.md, Section 3 (E2E, Playwright).
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  retries: 1,
  reporter: [['html', { outputFolder: 'artifacts/playwright-report' }]],
  use: {
    baseURL: 'http://localhost:8080',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
