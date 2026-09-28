import { test as base, expect, Page } from '@playwright/test';

/**
 * Shared test data + UI helpers for the TakaFlow e2e suite.
 *
 * IMPORTANT: per playwright.config.ts, this suite runs against a live, shared Neon Postgres
 * instance — there is no test-specific database. Every user/category/transaction/budget plan
 * created by these helpers is prefixed with `e2e_test_` and suffixed with a per-call unique id
 * (Date.now() + a random suffix) so the data is unmistakably synthetic, never collides across
 * parallel or repeated runs, and stays trivially grep-able for later cleanup.
 */

export interface TestUser {
  fullName: string;
  username: string;
  email: string;
  password: string;
}

/** Builds a brand-new, guaranteed-unique synthetic user for a single test. */
export function makeTestUser(): TestUser {
  const unique = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
  return {
    fullName: `e2e_test_User_${unique}`,
    // Username policy (AuthService.USERNAME_PATTERN): 3-30 chars, letters/digits/underscore/period only.
    username: `e2e_test_${unique}`,
    email: `e2e_test_${unique}@example.com`,
    // Password policy (AuthService.PASSWORD_PATTERN): 8+ chars, upper, lower, digit, special char.
    password: `E2eTest_${unique}!A1`,
  };
}

/**
 * Fills out and submits the real `/signup` form (signup.html — the full registration form with
 * name/username/email/password/confirm/terms, backed by POST /api/auth/register) and waits for
 * the app's own post-signup redirect to the dashboard.
 *
 * Note: on this page the "Login" tab is active by default (even on the /signup route), so the
 * Sign Up fields are hidden until the "Sign Up" tab is clicked.
 */
export async function signUpNewUser(page: Page, user: TestUser): Promise<void> {
  await page.goto('/signup');
  await page.locator('#signup-tab').click();
  await page.locator('#signup-fullname').fill(user.fullName);
  await page.locator('#signup-username').fill(user.username);
  await page.locator('#signup-email').fill(user.email);
  await page.locator('#signup-password').fill(user.password);
  await page.locator('#signup-confirm-password').fill(user.password);
  await page.locator('#agree-terms').check();
  await page.locator('#signup-form button.btn-submit').click();
  await page.waitForURL('**/dashboard.html', { timeout: 15000 });
}

/**
 * Fills out and submits the real `/login` form (login.html) but leaves the outcome un-asserted
 * on purpose — callers decide what "success" (redirect) or "failure" (inline error) looks like
 * for their own scenario.
 */
export async function submitLogin(page: Page, usernameOrEmail: string, password: string): Promise<void> {
  await page.goto('/login');
  await page.locator('#login-username').fill(usernameOrEmail);
  await page.locator('#login-password').fill(password);
  await page.locator('#login-form button.btn-submit').click();
}

/** Logs in and waits for the app's own redirect to the dashboard, the way a real successful login does. */
export async function loginAndWaitForDashboard(page: Page, usernameOrEmail: string, password: string): Promise<void> {
  await submitLogin(page, usernameOrEmail, password);
  await page.waitForURL('**/dashboard.html', { timeout: 15000 });
}

/** Clicks the sidebar's Logout button (present on every authenticated page) and waits for the
 *  app's own redirect back to /login. */
export async function logout(page: Page): Promise<void> {
  await page.locator('#sidebarLogoutBtn').click();
  await page.waitForURL('**/login', { timeout: 15000 });
}

/**
 * Seeds the small built-in default category set (Salary/Food/Transport/Bills/DPS) via the real
 * "Restore defaults" button on Settings > Categories. Brand-new accounts start with zero
 * categories, and the transaction form requires one — this is the quickest genuine-UI path to
 * get a usable expense category without hand-authoring the "add category" row markup.
 */
export async function seedDefaultCategories(page: Page): Promise<void> {
  await page.goto('/settings?section=categories');
  await page.locator('#btnDefaultCats').click();
  await expect(page.locator('#catList')).toContainText('Food', { timeout: 15000 });
}

/**
 * Playwright fixture that signs up a fresh, unique user via the real UI before the test body
 * runs, landing on the dashboard already logged in. Keeps every spec file independent (its own
 * account, no shared/leftover state) without repeating the signup boilerplate in every test that
 * just needs "some logged-in user" to exercise a different feature.
 */
export const test = base.extend<{ newUser: TestUser }>({
  newUser: async ({ page }, use) => {
    const user = makeTestUser();
    await signUpNewUser(page, user);
    await use(user);
  },
});

export { expect };
