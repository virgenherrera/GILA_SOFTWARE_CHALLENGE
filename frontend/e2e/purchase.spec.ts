import { test, expect } from '@playwright/test';

/**
 * End-to-end purchase flow: browse -> add to cart -> adjust quantity -> checkout -> confirm.
 *
 * Each Playwright test gets a fresh, isolated browser context by default, so the anonymous
 * cart cookie (see frontend/src/app/cart/cart.service.ts) starts empty for this test —
 * no shared state with other spec files is possible.
 *
 * Uses SPORT-BALL-01 ("Professional Basketball", $29.99, stock 200 — see
 * test/fixtures/seed.sql) rather than the more obvious SHOE-001. SHOE-001 is deliberately
 * targeted by an upsert row in test/fixtures/import-test.csv (used by csv-import.spec.ts) that
 * overwrites its price and stock to exercise update-on-existing-SKU behavior. Since all four
 * spec files run against the same live stack and Playwright may run them concurrently,
 * decrementing SHOE-001's stock here while another worker concurrently overwrites it via the
 * CSV upsert is a genuine, observed race (confirmed while validating this suite: final stock
 * came out as seed-stock-minus-2 only when no concurrent import ran, and as
 * csv-upsert-stock-minus-2 otherwise). SPORT-BALL-01 never appears in that CSV fixture, so it
 * has no such cross-spec coupling — this test is independent of execution order or parallelism.
 * Price and stock are still read live rather than hardcoded, per the mission's own guidance
 * against hardcoding seed values.
 */
test.describe('Purchase flow', () => {
  const sku = 'SPORT-BALL-01';

  test('adds a product to the cart, adjusts quantity, checks out, and shows the order', async ({
    page,
  }) => {
    // Read the live starting stock and unit price from the product detail page.
    await page.goto(`/products/${sku}`);
    const detailRows = page.locator('dl > div');
    const stockRow = detailRows.filter({ hasText: 'Stock' });
    const priceRow = detailRows.filter({ hasText: 'Price' });
    await expect(stockRow).toBeVisible();
    await expect(priceRow).toBeVisible();

    const initialStock = Number(await stockRow.locator('dd').innerText());
    const unitPriceText = (await priceRow.locator('dd').innerText()).replace('$', '');
    const unitPrice = Number(unitPriceText);
    const expectedTotal = (unitPrice * 2).toFixed(2);

    await page.goto('/search');
    await page
      .getByPlaceholder('Search by name, description, or SKU…')
      .fill('Professional Basketball');

    const card = page.getByRole('article').filter({ hasText: sku });
    await expect(card).toBeVisible();
    await expect(card).toContainText(unitPriceText);

    await card.getByRole('button', { name: 'Add to Cart' }).click();
    await expect(page.getByText('Added to cart successfully.')).toBeVisible();

    await page.getByRole('link', { name: 'Cart' }).click();
    await page.waitForURL((url) => url.pathname === '/cart');
    await expect(page.getByRole('heading', { name: 'Your Cart', exact: true })).toBeVisible();

    const cartRow = page.locator('tr').filter({ hasText: sku });
    await expect(cartRow).toBeVisible();
    await expect(cartRow).toContainText(unitPriceText);

    // Bump quantity 1 -> 2 via the cart item's increment button; total must double.
    await cartRow.getByRole('button', { name: '+' }).click();
    await expect(cartRow).toContainText(expectedTotal);
    await expect(page.getByText(`Total: $${expectedTotal}`)).toBeVisible();

    await page.getByRole('button', { name: 'Proceed to Checkout' }).click();
    await page.waitForURL((url) => url.pathname.startsWith('/checkout/confirmation/'));

    await expect(page.getByText('Order placed successfully!')).toBeVisible();

    // Order line: SKU | name | quantity | unit price | subtotal (see order-confirmation.html).
    const orderRow = page.locator('tr').filter({ hasText: sku });
    await expect(orderRow).toBeVisible();
    await expect(orderRow.locator('td').nth(2)).toHaveText('2');
    await expect(orderRow.locator('td').nth(3)).toHaveText(`$${unitPriceText}`);
    await expect(orderRow.locator('td').nth(4)).toHaveText(`$${expectedTotal}`);
    await expect(page.getByText(`Total: $${expectedTotal}`)).toBeVisible();

    // The cart is cleared server-side on checkout (CartService.checkout()), so the item-count
    // badge (only rendered while cartItemCount() > 0, see app.html) must be gone, leaving the
    // nav link's accessible name as exactly "Cart".
    await expect(page.getByRole('link', { name: 'Cart', exact: true })).toBeVisible();

    // Stock must have decremented by exactly the 2 units purchased.
    await page.goto(`/products/${sku}`);
    const updatedStockRow = page.locator('dl > div').filter({ hasText: 'Stock' });
    await expect(updatedStockRow.locator('dd')).toHaveText(String(initialStock - 2));
  });
});
