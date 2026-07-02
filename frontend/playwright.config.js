// Playwright E2E config — runs against the STAGING stack, not a dev server.
//
//   docker compose -f ../docker-compose.staging.yml up -d --build
//   npm run e2e
//
// Env knobs (defaults match docker-compose.staging.yml):
//   E2E_BASE_URL        SPA + API origin        (default http://localhost:28086)
//   E2E_LICENSE_URL     license server origin   (default http://localhost:19090)
//   DEMO_SEED_PASSWORD  demo_owner's password   (default DemoStaging2026)
import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  expect: { timeout: 15_000 },
  // The staging stack is one shared stateful backend — keep runs serial so
  // tests never race each other's data.
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: process.env.E2E_BASE_URL || 'http://localhost:28086',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    // A system proxy (corp/VPN/AV) can swallow loopback:high-port requests
    // inside Chromium even though curl works — force direct connections.
    launchOptions: {
      args: ['--proxy-server=direct://', '--proxy-bypass-list=*'],
    },
  },
});
