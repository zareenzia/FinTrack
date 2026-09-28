import { test, expect } from './fixtures';

/**
 * Creates a budget plan through the real "Create Budget" modal on the Budget Planner page,
 * confirms it appears in the plan dropdown, and actually exercises selecting it from that
 * dropdown (not just relying on the post-save auto-selection).
 *
 * Uses the shared `newUser` fixture so this file creates its own fresh account and needs no
 * state from any other spec — a brand-new account has zero budget plans.
 */
test.describe('Budget Planner', () => {
  test('create a budget plan via the UI and confirm it can be selected from the plan dropdown', async ({ page, newUser }) => {
    void newUser; // only needed to trigger the shared signup/login fixture
    const planName = `e2e_test_budget_${Date.now()}`;

    await page.goto('/budget-planner');

    await page.locator('#openCreatePlanBtn').click();

    const modal = page.locator('#planModal');
    await expect(modal).toBeVisible();

    await modal.locator('#planName').fill(planName);
    // Period type/label/start/end are pre-filled by openCreatePlanModal() with sensible
    // current-month defaults — only the name needs to be unique for this test.
    await modal.locator('#savePlanBtn').click();
    await expect(modal).toBeHidden({ timeout: 15000 });

    // A successful save auto-loads and displays the new plan.
    await expect(page.locator('#bpPlanName')).toHaveText(planName, { timeout: 15000 });

    const planSelect = page.locator('#planSelect');
    const option = planSelect.locator('option', { hasText: planName });
    await expect(option).toHaveCount(1);

    // Actually exercise picking it from the dropdown.
    const optionValue = await option.getAttribute('value');
    expect(optionValue).toBeTruthy();
    await planSelect.selectOption(optionValue!);
    await expect(page.locator('#bpPlanName')).toHaveText(planName, { timeout: 15000 });
  });
});
