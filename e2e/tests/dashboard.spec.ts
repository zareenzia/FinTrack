import { test, expect } from './fixtures';

/**
 * Confirms the dashboard loads correctly for a freshly-signed-up user: the greeting and every
 * stat card render real (non-placeholder) values, the injected sidebar is present, and the page
 * neither throws an uncaught error nor logs a console error while loading.
 *
 * Uses the shared `newUser` fixture (see fixtures.ts) so this file needs no state from any other
 * spec — it creates and logs in its own account.
 */
test.describe('Dashboard', () => {
  test('loads with stat cards populated and no console/page errors', async ({ page, newUser }) => {
    const consoleErrors: string[] = [];
    page.on('console', (msg) => {
      if (msg.type() === 'error') consoleErrors.push(msg.text());
    });
    const pageErrors: string[] = [];
    page.on('pageerror', (err) => pageErrors.push(err.message));

    // The newUser fixture already landed on /dashboard.html via the signup redirect; navigate
    // there again so the console/pageerror listeners above observe a clean dashboard load.
    await page.goto('/dashboard');
    await expect(page).toHaveURL(/\/dashboard/);

    await expect(page.locator('#dashboardGreeting')).toContainText(newUser.username, { timeout: 15000 });

    const statCardIds = ['total-income', 'total-expense', 'total-savings', 'balance', 'total-assets', 'net-worth'];
    for (const id of statCardIds) {
      const card = page.locator(`#${id}`);
      await expect(card).toBeVisible();
      // Cards start as literal "..." placeholders until loadDashboard()'s fetches resolve.
      await expect(card).not.toHaveText('...', { timeout: 15000 });
    }

    // The sidebar is built dynamically by sidebar.js — its presence proves that script ran
    // without crashing.
    await expect(page.locator('#sidebar')).toBeVisible();
    await expect(page.locator('#sidebarLogoutBtn')).toBeVisible();

    expect(consoleErrors, `Unexpected console errors:\n${consoleErrors.join('\n')}`).toEqual([]);
    expect(pageErrors, `Unexpected uncaught page errors:\n${pageErrors.join('\n')}`).toEqual([]);
  });
});
