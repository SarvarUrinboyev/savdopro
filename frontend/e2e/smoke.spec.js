// Smoke E2E against the staging stack (docker-compose.staging.yml).
//
// Covers the money paths a broken build takes down first: the marketing
// landing renders, a real login round-trips through the license server,
// the authenticated shell (sidebar + dashboard) mounts, and the POS page
// loads its catalogue UI. Deeper flows (checkout, refunds, debt) belong in
// per-feature specs once selectors get data-testid hooks.
//
// Login uses the seeded demo tenant (DemoUserSeeder / DemoDataSeeder,
// ALLOW_DEMO_SEED=true in the staging compose): demo_owner on account 90001.
import { test, expect } from '@playwright/test';

const LICENSE_URL = process.env.E2E_LICENSE_URL || 'http://localhost:19090';
const DEMO_USER = process.env.E2E_DEMO_USER || 'demo_owner';
const DEMO_PASSWORD = process.env.DEMO_SEED_PASSWORD || 'DemoStaging2026';

async function login(page) {
  // ?licenseUrl= wires the SPA to the staging license server on first visit
  // (licenseClient.js persists it to localStorage for the whole session).
  await page.goto(`/login?licenseUrl=${encodeURIComponent(LICENSE_URL)}`);
  await page.locator('input[autocomplete="username"]').fill(DEMO_USER);
  await page.locator('input[autocomplete="current-password"]').fill(DEMO_PASSWORD);
  await page.locator('button[type="submit"]').click();
  // Successful login leaves /login and mounts the authenticated Layout.
  await expect(page).not.toHaveURL(/\/login/, { timeout: 20_000 });
}

test('landing page renders for anonymous visitors', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByText('aqlli tizimda boshqaring')).toBeVisible();
});

test('demo owner can log in and see the dashboard shell', async ({ page }) => {
  await login(page);
  // Sidebar brand = the authenticated Layout mounted with a working JWT.
  await expect(page.getByText('SavdoPRO').first()).toBeVisible();
  // Dashboard actually talks to the backend (any KPI/dashboard content
  // implies /api calls succeeded with the license-minted token).
  await expect(page.locator('body')).not.toContainText('ruxsatingiz yo\'q');
});

test('POS page loads its catalogue UI', async ({ page }) => {
  await login(page);
  await page.goto('/pos');
  // The search box is the heart of the POS screen.
  await expect(
    page.locator('input[placeholder*="Nomi yoki SKU"]')
  ).toBeVisible({ timeout: 20_000 });
});
