import { test, expect } from '@playwright/test';

const KEYWORD_PLACEHOLDER = 'Search by name, description, or SKU…';

/**
 * Exercises the dedicated /search route (frontend/src/app/search), which is distinct from
 * the /products list — it has its own keyword box (debounced 300ms, see
 * search-page.ts KEYWORD_DEBOUNCE_MS), category/price/sort filters (search-filters.ts), and
 * "Add to Cart" affordance (search-results.html) instead of Edit/Delete.
 */
test.describe('Search page', () => {
  test('keyword search returns the matching seeded product', async ({ page }) => {
    await page.goto('/search');
    await expect(page.getByRole('heading', { name: 'Search Products' })).toBeVisible();

    await page.getByPlaceholder(KEYWORD_PLACEHOLDER).fill('Running');

    // SHOE-001 is seeded as "Classic Running Shoes" (test/fixtures/seed.sql). Its *name* can
    // legitimately be "Updated Running Shoes" instead if the csv-import spec's fixture (which
    // deliberately upserts SHOE-001 to exercise update-on-existing-SKU behavior) already ran
    // against this same DB — both names contain "Running Shoes", so asserting that substring
    // keeps this test correct regardless of suite execution order.
    const card = page.getByRole('article').filter({ hasText: 'SHOE-001' });
    await expect(card).toBeVisible();
    await expect(card).toContainText('Running Shoes');
  });

  test('filtering by category shows only products in that category', async ({ page }) => {
    await page.goto('/search');

    await page.locator('#category').selectOption('Electronics');

    const cards = page.getByRole('article');
    await expect(cards.first()).toBeVisible();

    const count = await cards.count();
    expect(count).toBeGreaterThan(0);

    for (let i = 0; i < count; i += 1) {
      await expect(cards.nth(i)).toContainText('Electronics');
    }
  });

  test('clearing the category filter restores the full product list', async ({ page }) => {
    await page.goto('/search');

    await page.locator('#category').selectOption('Electronics');
    // SHOE-001 is Footwear, so it must drop out of an Electronics-only view.
    await expect(page.getByRole('article').filter({ hasText: 'SHOE-001' })).toHaveCount(0);

    await page.locator('#category').selectOption('');
    await expect(page.getByRole('article').filter({ hasText: 'SHOE-001' })).toBeVisible();
  });

  test('prefix search matches partial keywords', async ({ page }) => {
    await page.goto('/search');

    await page.getByPlaceholder(KEYWORD_PLACEHOLDER).fill('Run');

    const card = page.getByRole('article').filter({ hasText: 'SHOE-001' });
    await expect(card).toBeVisible();
    await expect(card).toContainText('Running Shoes');
  });

  test('searching for a non-existent term shows the empty state', async ({ page }) => {
    await page.goto('/search');

    await page.getByPlaceholder(KEYWORD_PLACEHOLDER).fill('zzz-nonexistent-product-xyz');

    await expect(page.getByText('No products found')).toBeVisible();
  });
});
