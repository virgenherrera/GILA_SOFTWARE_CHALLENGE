ALTER TABLE cart_items ADD COLUMN unit_price_snapshot NUMERIC(10,2) NOT NULL CHECK (unit_price_snapshot > 0);
