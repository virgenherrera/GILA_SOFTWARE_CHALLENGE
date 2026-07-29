-- ============================================================================
-- E2E test seed data
-- ============================================================================
-- Loaded via docker-entrypoint-initdb.d AFTER all 12 migrations have run
-- (see docker-compose.test.yml, mounted as /docker-entrypoint-initdb.d/02-seed.sql —
-- the "02-" prefix sorts after the "001".."012" migration filenames).
--
-- Scope: PRODUCTS ONLY. Carts, orders, and import jobs are deliberately NOT
-- seeded here — E2E tests create those themselves as part of the flows they
-- exercise (add-to-cart, checkout, CSV upload).
--
-- search_vector (products) is populated by the products_search_vector_trigger
-- (migration 002) on every INSERT — it is NEVER set explicitly in this file.
--
-- Column order matches the current products schema after migration 011:
--   sku VARCHAR(50) PK, name VARCHAR(255), description TEXT (nullable),
--   price NUMERIC(10,2) CHECK (0 < price <= 99999999.99 at the DB layer;
--     the application-level Malli schema additionally caps price at 99999.99 —
--     seed values respect the tighter application cap so they remain valid
--     end-to-end through the API, not just at the DB layer),
--   category VARCHAR(100) (nullable), stock INTEGER (>= 0),
--   weight_kg NUMERIC(7,2) (nullable, 0 <= weight_kg <= 9999.99),
--   created_at, updated_at TIMESTAMPTZ.
-- ============================================================================

INSERT INTO products
  (sku, name, description, price, category, stock, weight_kg, created_at, updated_at)
VALUES

-- 1) Plain baseline product — Footwear category, mid-range price, healthy stock.
(
  'SHOE-001',
  'Classic Running Shoes',
  'Lightweight everyday running shoe with breathable mesh upper.',
  59.99,
  'Footwear',
  150,
  0.45,
  '2026-01-01 10:00:00+00',
  '2026-01-01 10:00:00+00'
),

-- 2) Electronics, higher price point, heavier weight_kg.
(
  'ELEC-TV-55',
  '55-Inch 4K Ultra HD Smart TV',
  'Smart television with HDR support and built-in streaming apps.',
  499.99,
  'Electronics',
  25,
  18.50,
  '2026-01-01 10:05:00+00',
  '2026-01-01 10:05:00+00'
),

-- 3) MIN PRICE BOUNDARY — smallest price the application-level Product schema
--    accepts (price must be > 0; 0.01 is the smallest two-decimal value above zero).
(
  'ELEC-MOUSE-01',
  'Wireless Ergonomic Mouse',
  'Rechargeable wireless mouse with adjustable DPI.',
  0.01,
  'Electronics',
  500,
  0.10,
  '2026-01-01 10:10:00+00',
  '2026-01-01 10:10:00+00'
),

-- 4) MAX PRICE BOUNDARY — exactly 99999.99, the application-level Product
--    schema's upper price cap (see src/ecommerce/validation.clj Product schema).
(
  'BOOK-RARE-001',
  'Rare First-Edition Manuscript Collection',
  'Limited collector''s edition, individually numbered.',
  99999.99,
  'Books',
  3,
  1.20,
  '2026-01-01 10:15:00+00',
  '2026-01-01 10:15:00+00'
),

-- 5) ZERO STOCK EDGE CASE — an out-of-stock product that must still be
--    visible/searchable in the catalog but rejected at add-to-cart time.
(
  'TOY-BLOCKS-001',
  'Deluxe Wooden Building Blocks Set',
  '120-piece wooden block set for creative play.',
  34.50,
  'Toys',
  0,
  2.30,
  '2026-01-01 10:20:00+00',
  '2026-01-01 10:20:00+00'
),

-- 6) NULL OPTIONAL FIELDS — description, category, and weight_kg are all
--    absent, exercising the nullable columns introduced/loosened in
--    migration 011 (category DROP NOT NULL; weight_kg is optional-by-design).
(
  'MISC-NULLABLE-001',
  'Unlabeled Mystery Box',
  NULL,
  19.99,
  NULL,
  42,
  NULL,
  '2026-01-01 10:25:00+00',
  '2026-01-01 10:25:00+00'
),

-- 7) MAX-LENGTH NAME — exactly 255 characters, the VARCHAR(255) limit set by
--    migration 011 ("name: VARCHAR(256) -> VARCHAR(255)").
(
  'LONGNAME-001',
  'Premium Industrial-Grade Extra-Heavy-Duty Stainless-Steel Adjustable Multi-Purpose Workbench with Reinforced Corners, Locking Caster Wheels, a Weather-Resistant Powder-Coated Finish, and Modular Shelving for Home, Garage, Warehouse, and Workshop Daily Use',
  'Boundary-testing product with a name at the exact 255-character VARCHAR limit.',
  15.00,
  'Home',
  10,
  0.50,
  '2026-01-01 10:30:00+00',
  '2026-01-01 10:30:00+00'
),

-- 8) Garden category, heavier weight_kg, mid stock.
(
  'GARDEN-HOSE-50FT',
  '50 ft Expandable Garden Hose',
  'Lightweight expandable hose with an 8-pattern spray nozzle.',
  24.99,
  'Garden',
  60,
  3.75,
  '2026-01-01 10:35:00+00',
  '2026-01-01 10:35:00+00'
),

-- 9) MIN POSITIVE STOCK BOUNDARY — stock of exactly 1, useful for testing
--    "last unit" add-to-cart and post-checkout stock-decrement-to-zero logic.
(
  'FASH-HAT-001',
  'Wide-Brim Straw Sun Hat',
  'UV-protective straw hat, one size fits most.',
  12.99,
  'Fashion',
  1,
  0.05,
  '2026-01-01 10:40:00+00',
  '2026-01-01 10:40:00+00'
),

-- 10) Cheapest catalog item, very high stock — useful for pagination/sort tests.
(
  'ELEC-CABLE-USB',
  '6ft USB-C Charging Cable',
  'Braided USB-C to USB-C fast-charging cable.',
  5.99,
  'Electronics',
  1000,
  0.02,
  '2026-01-01 10:45:00+00',
  '2026-01-01 10:45:00+00'
);

-- ============================================================================
-- Coverage summary (for reviewers):
--   Categories:      Footwear, Electronics (x3), Books, Toys, NULL, Home,
--                     Garden, Fashion — 7 distinct non-null categories + NULL.
--   Price range:     0.01 (min) .. 499.99 (mid-high) .. 99999.99 (max boundary).
--   Stock range:     0 (zero/out-of-stock) .. 1 (min positive) .. 1000 (high).
--   Nullable fields: MISC-NULLABLE-001 has NULL description, category, weight_kg.
--   Max-length name: LONGNAME-001 is exactly 255 characters.
--   weight_kg set:   present on 9 of 10 rows (all except MISC-NULLABLE-001).
-- ============================================================================
