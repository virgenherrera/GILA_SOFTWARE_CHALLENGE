import { test, expect } from '@playwright/test';

/**
 * Full product lifecycle against the real stack (browser -> nginx -> Clojure -> Postgres).
 * Uses a unique SKU so it never collides with the seeded catalog (see
 * test/fixtures/seed.sql), and cleans itself up by deleting the product at the end.
 *
 * Note: the product form (frontend/src/app/products/product-form/product-form.html) only
 * exposes SKU, Name, Description, Category, Price, and Stock fields. There is no weight
 * input on create/update — `weight_kg` is a read-only, backend-assigned field only shown on
 * the product detail page — so this test does not attempt to fill a "weight" field.
 */
test.describe('Product CRUD', () => {
  const sku = 'E2E-CRUD-001';

  test('creates, views, edits, and deletes a product', async ({ page }) => {
    await page.goto('/products');
    // `exact: true` matters here: the CSV-import fixture (test/fixtures/import-test.csv)
    // deliberately includes a row with an adversarial SQL-injection string as the product
    // name, which — if that spec has already run against this shared DB — renders as a
    // <h3>'; DROP TABLE products; --</h3> card heading. Playwright's default name matching is
    // a case-insensitive substring match, so an inexact "Products" query would ambiguously
    // match both that heading and the real page heading.
    await expect(page.getByRole('heading', { name: 'Products', exact: true })).toBeVisible();

    await page.getByRole('link', { name: 'New Product' }).click();
    await page.waitForURL((url) => url.pathname === '/products/new');
    await expect(page.getByRole('heading', { name: 'New Product', exact: true })).toBeVisible();

    await page.getByLabel('SKU').fill(sku);
    await page.getByLabel('Name').fill('E2E Widget');
    await page.getByLabel('Description').fill('Created by the Playwright product-crud test.');
    await page.getByLabel('Category').fill('E2E-Category');
    await page.getByLabel('Price').fill('25.5');
    await page.getByLabel('Stock').fill('10');

    await page.getByRole('button', { name: 'Save' }).click();
    await page.waitForURL((url) => url.pathname === '/products');

    // Filter the list down to our SKU so the assertion holds regardless of how many other
    // products (seed data, other suites' fixtures) exist across the default 20-per-page list.
    await page.getByPlaceholder('Search products…').fill(sku);

    // The new product must appear in the list with its submitted values.
    const card = page.getByRole('article').filter({ hasText: sku });
    await expect(card).toBeVisible();
    await expect(card).toContainText('E2E Widget');
    await expect(card).toContainText('25.50');
    await expect(card).toContainText('Stock: 10');

    // View details and verify every submitted field round-tripped through the API.
    await card.getByRole('button', { name: 'View' }).click();
    await page.waitForURL((url) => url.pathname === `/products/${sku}`);

    await expect(page.getByRole('heading', { name: 'E2E Widget', exact: true })).toBeVisible();
    await expect(page.getByText('Created by the Playwright product-crud test.')).toBeVisible();

    const detail = page.locator('dl');
    await expect(detail.locator('div').filter({ hasText: 'Category' }).locator('dd')).toHaveText(
      'E2E-Category',
    );
    await expect(detail.locator('div').filter({ hasText: 'Price' }).locator('dd')).toHaveText(
      '$25.50',
    );
    await expect(detail.locator('div').filter({ hasText: 'Stock' }).locator('dd')).toHaveText(
      '10',
    );

    // Edit: change name and price.
    await page.getByRole('button', { name: 'Edit' }).click();
    await page.waitForURL((url) => url.pathname === `/products/${sku}/edit`);
    await expect(page.getByRole('heading', { name: 'Edit Product', exact: true })).toBeVisible();

    // SKU is disabled/read-only in edit mode but still displays the existing value.
    await expect(page.getByLabel('SKU')).toHaveValue(sku);

    await page.getByLabel('Name').fill('E2E Widget Updated');
    await page.getByLabel('Price').fill('30');

    await page.getByRole('button', { name: 'Save' }).click();
    await page.waitForURL((url) => url.pathname === `/products/${sku}`);

    // Updated values must persist after the edit.
    await expect(
      page.getByRole('heading', { name: 'E2E Widget Updated', exact: true }),
    ).toBeVisible();
    await expect(
      page.locator('dl').locator('div').filter({ hasText: 'Price' }).locator('dd'),
    ).toHaveText('$30.00');

    // Delete: accept the native confirm() dialog raised by ProductDetail.onDelete().
    page.once('dialog', (dialog) => {
      void dialog.accept();
    });
    await page.getByRole('button', { name: 'Delete' }).click();
    await page.waitForURL((url) => url.pathname === '/products');

    // Filter by SKU again (searchTerm resets on navigation) and confirm the empty state —
    // proof the product is gone rather than merely pushed onto another page. A generous
    // timeout here absorbs backend latency when this suite runs alongside the other specs
    // against the same shared stack.
    await page.getByPlaceholder('Search products…').fill(sku);
    await expect(page.getByText('No products found.')).toBeVisible({ timeout: 10_000 });
    await expect(page.getByRole('article').filter({ hasText: sku })).toHaveCount(0);
  });
});
