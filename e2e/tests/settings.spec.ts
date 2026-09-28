import { test, expect } from './fixtures';

/**
 * Toggles the Color Theme appearance setting on the Settings > Appearance page (the default
 * section, so no nav click is needed) and confirms it survives a full page reload — the
 * round-trip through localStorage (and the best-effort server sync) that "persisted" means here.
 *
 * Uses the shared `newUser` fixture so this file creates its own fresh account and needs no
 * state from any other spec. A brand-new account always starts on the "forest" default theme.
 */
test.describe('Settings', () => {
  test('changing the color theme persists after a reload', async ({ page, newUser }) => {
    void newUser; // only needed to trigger the shared signup/login fixture
    await page.goto('/settings');

    const forestOption = page.locator('.theme-option[data-theme-key="forest"]');
    const wineOption = page.locator('.theme-option[data-theme-key="wine"]');

    // Sanity check on the default before changing anything.
    await expect(forestOption).toHaveClass(/active/);

    await wineOption.click();
    await expect(wineOption).toHaveClass(/active/);
    await expect(page.locator('html')).toHaveAttribute('data-color-theme', 'wine');

    await page.reload();

    await expect(page.locator('.theme-option[data-theme-key="wine"]')).toHaveClass(/active/, { timeout: 15000 });
    await expect(page.locator('html')).toHaveAttribute('data-color-theme', 'wine');
  });
});
