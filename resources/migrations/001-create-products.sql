CREATE TABLE products (
  sku VARCHAR(50) PRIMARY KEY,
  name VARCHAR(256) NOT NULL,
  description TEXT,
  price NUMERIC(7,2) NOT NULL CHECK (price > 0 AND price <= 99999.99),
  category VARCHAR(100) NOT NULL,
  stock INTEGER NOT NULL DEFAULT 0 CHECK (stock >= 0),
  search_vector tsvector,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_products_category ON products (category);
