import { test, expect, seedDefaultCategories } from './fixtures';

/**
 * Adds a new transaction through the real "Add New Transaction" modal on the Transactions page
 * and confirms it shows up in the Recent Transactions list afterward.
 *
 * Uses the shared `newUser` fixture so this file creates its own fresh account and needs no
 * state from any other spec. A brand-new account has zero categories (the transaction form
 * requires one), so this test first seeds the built-in defaults via Settings > Categories
 * ("Restore defaults") — a genuine UI action, not a direct API/DB shortcut.
 */
test.describe('Transactions', () => {
  test('add a new transaction via the UI and confirm it appears in the transaction list', async ({ page, newUser }) => {
    void newUser; // only needed to trigger the shared signup/login fixture
    await seedDefaultCategories(page);

    const description = `e2e_test_tx_${Date.now()}`;

    await page.goto('/transactions');
    await page.locator('#openAddTransactionModalBtn').click();

    const modal = page.locator('#addTransactionModal');
    await expect(modal).toBeVisible();

    // The modal renders one bulk transaction row by default; it has no id, just these classes.
    await modal.locator('.tx-row-desc').fill(description);
    await modal.locator('.tx-row-amount').fill('123.45');
    // Type defaults to "expense". The category <select> is populated by an async fetch after the
    // modal opens, so wait for the seeded "Food" option to actually exist before selecting it.
    const categorySelect = modal.locator('.tx-row-category');
    await expect(categorySelect.locator('option', { hasText: 'Food' })).toHaveCount(1, { timeout: 10000 });
    await categorySelect.selectOption({ label: 'Food' });

    await modal.locator('#saveTransactionBtn').click();
    await expect(modal).toBeHidden({ timeout: 15000 });

    // Filter the Recent Transactions list down to our unique description and confirm it's there.
    await page.locator('#txSearchFilter').fill(description);
    await expect(page.locator('#recent-tx-body')).toContainText(description, { timeout: 15000 });
  });
});
