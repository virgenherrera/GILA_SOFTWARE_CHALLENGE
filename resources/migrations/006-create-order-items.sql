CREATE TABLE order_items (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
  product_sku VARCHAR(50) NOT NULL REFERENCES products(sku),
  quantity INTEGER NOT NULL CHECK (quantity > 0),
  price_at_purchase NUMERIC(7,2) NOT NULL CHECK (price_at_purchase > 0),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_order_items_order_id ON order_items (order_id);
