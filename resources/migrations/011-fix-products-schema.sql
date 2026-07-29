-- Fix product schema to match spec: name 255, category nullable, add weight_kg, price NUMERIC(10,2)

-- name: VARCHAR(256) → VARCHAR(255)
ALTER TABLE products ALTER COLUMN name TYPE VARCHAR(255);

-- category: NOT NULL → nullable
ALTER TABLE products ALTER COLUMN category DROP NOT NULL;

-- Add weight_kg column
ALTER TABLE products ADD COLUMN IF NOT EXISTS weight_kg NUMERIC(7,2) CHECK (weight_kg IS NULL OR (weight_kg >= 0 AND weight_kg <= 9999.99));

-- price: NUMERIC(7,2) → NUMERIC(10,2)
-- First drop the existing CHECK constraint, then alter type, then re-add constraint
ALTER TABLE products DROP CONSTRAINT IF EXISTS products_price_check;
ALTER TABLE products ALTER COLUMN price TYPE NUMERIC(10,2);
ALTER TABLE products ADD CONSTRAINT products_price_check CHECK (price > 0 AND price <= 99999999.99);

-- Also fix order_items price precision if it exists
ALTER TABLE order_items ALTER COLUMN price_at_purchase TYPE NUMERIC(10,2);
