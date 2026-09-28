import { test, expect } from '@playwright/test';
import { makeTestUser, signUpNewUser, logout } from './fixtures';

/**
 * Covers the core authentication journey against the real /signup and /login pages:
 * sign up a brand-new unique user, confirm the app lands on the dashboard, log out, log back in
 * with the same credentials, and a negative case for a wrong password.
 *
 * Each test creates its own unique account (via makeTestUser/signUpNewUser), so this file has no
 * dependency on any other spec file or leftover data.
 */
test.describe('Authentication', () => {
  test('sign up via the real form, land on the dashboard, log out, then log back in', async ({ page }) => {
    const user = makeTestUser();

    await test.step('sign up a brand-new user via the signup.html form', async () => {
      await signUpNewUser(page, user);
      await expect(page).toHaveURL(/\/dashboard\.html/);
      // Greeting is populated from the freshly-created account, proving the session is real.
      await expect(page.locator('#dashboardGreeting')).toContainText(user.username, { timeout: 15000 });
    });

    await test.step('log out via the sidebar', async () => {
      await logout(page);
      await expect(page).toHaveURL(/\/login$/);
    });

    await test.step('log back in with the same credentials', async () => {
      await page.locator('#login-username').fill(user.email);
      await page.locator('#login-password').fill(user.password);
      await page.locator('#login-form button.btn-submit').click();

      await expect(page).toHaveURL(/\/dashboard\.html/, { timeout: 15000 });
      await expect(page.locator('#dashboardGreeting')).toContainText(user.username, { timeout: 15000 });
    });
  });

  test('logging in with the wrong password shows an error and does not navigate away', async ({ page }) => {
    const user = makeTestUser();
    await signUpNewUser(page, user);
    await logout(page);

    // logout() already lands us back on /login with the Login tab active by default.
    await page.locator('#login-username').fill(user.email);
    await page.locator('#login-password').fill('Wrong_Password_123!');
    await page.locator('#login-form button.btn-submit').click();

    const notification = page.locator('#login-notification');
    await expect(notification).toHaveClass(/error/, { timeout: 10000 });
    await expect(notification).not.toBeEmpty();

    // The success path navigates away after a 1s delay — wait past that window and confirm we
    // never left /login.
    await page.waitForTimeout(1500);
    await expect(page).toHaveURL(/\/login$/);
  });
});
