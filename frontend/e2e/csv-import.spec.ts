import path from 'node:path';
import { test, expect } from '@playwright/test';

const CSV_PATH = path.resolve(__dirname, '../test-fixtures/import-test.csv');

/**
 * The import job is processed asynchronously by a background worker
 * (src/ecommerce/import/worker.clj). The UI (ImportResults) polls
 * GET /api/imports/:id every 2s until the status reaches a terminal state, so this test
 * relies on Playwright's auto-retrying `expect(...).toHaveText(...)` against the live status
 * badge instead of a fixed sleep.
 *
 * Expected outcome for test/fixtures/import-test.csv (19 data rows, verified against the
 * backend's validator/worker logic in src/ecommerce/import/{validator,worker}.clj, and
 * confirmed directly against a live upload: GET /api/imports/:id returned
 * { total_rows: 18, accepted_rows: 10, rejected_rows: 8, skipped_rows: 1, status:
 * "CompletedWithErrors" }):
 *   - accepted_rows = 10 (5 clean rows, optional-category row, optional-weight row, the
 *     first occurrence of the in-file duplicate SKU, the SQL-injection-string row which is
 *     safely stored as plain text, and the SHOE-001 row which upserts the existing seed
 *     product)
 *   - rejected_rows = 8 (non-numeric price, negative stock, empty name, the second occurrence
 *     of the duplicate SKU, price over the 99999.99 cap, fractional stock, a 256-char name,
 *     and a <script> tag matched by the backend's XSS filter)
 *   - 1 fully-blank row is skipped — worker.clj's :blank branch increments :skipped, which is
 *     NOT folded into total_rows (total_rows = accepted + rejected = 18, not the raw 19 data
 *     rows) and is NOT counted as rejected either.
 *   - final status = "CompletedWithErrors" (accepted > 0 and rejected > 0, per worker.clj's
 *     `(pos? (:rejected results)) "CompletedWithErrors"` branch)
 *
 * This import is idempotent to re-runs: every accepted row's SKU is upserted (insert, or
 * update-on-409-conflict — see worker.clj's upsert-product!), so running this test more than
 * once against the same seeded DB still yields the same accepted/rejected/skipped counts.
 */
test.describe('CSV import', () => {
  test('uploads a CSV, polls until processing completes, and shows accepted/rejected results', async ({
    page,
  }) => {
    await page.goto('/imports');
    await expect(
      page.getByRole('heading', { name: 'Import Products from CSV', exact: true }),
    ).toBeVisible();

    await page.getByLabel('CSV file').setInputFiles(CSV_PATH);
    await expect(page.getByText('Selected: import-test.csv')).toBeVisible();

    await page.getByRole('button', { name: 'Upload' }).click();

    // Upload success navigates to /imports/:job_id.
    await page.waitForURL((url) => /^\/imports\/[^/]+$/.test(url.pathname));
    await expect(page.getByRole('heading', { name: 'Import Results', exact: true })).toBeVisible();

    // Poll (via retrying assertion) until the job reaches its terminal status. The exact string
    // is known ahead of time from the fixture's makeup (see doc comment above), so match it
    // directly rather than via a multi-branch regex over every possible status value.
    await expect(page.getByText('CompletedWithErrors')).toBeVisible({ timeout: 30_000 });

    const stats = page.locator('dl');
    await expect(
      stats.locator('div').filter({ hasText: 'Total rows' }).locator('dd'),
    ).toHaveText('18');
    await expect(stats.locator('div').filter({ hasText: 'Accepted' }).locator('dd')).toHaveText(
      '10',
    );
    await expect(stats.locator('div').filter({ hasText: 'Rejected' }).locator('dd')).toHaveText(
      '8',
    );

    // Rejected rows are rendered in the "Rejected Rows" error table with their reasons.
    await expect(page.getByRole('heading', { name: 'Rejected Rows', exact: true })).toBeVisible();

    const errorsTable = page.getByRole('table');
    await expect(errorsTable).toBeVisible();
    // Known-bad rows from the fixture must show up with an identifiable reason.
    await expect(errorsTable).toContainText('Broken Price Widget');
    await expect(errorsTable).toContainText('Duplicate Widget Repeat');
  });
});
