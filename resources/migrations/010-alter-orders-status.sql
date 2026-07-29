-- Allow 'Paid', 'Failed', 'Fulfilled' statuses for checkout flow
ALTER TABLE orders DROP CONSTRAINT orders_status_check;
ALTER TABLE orders ADD CONSTRAINT orders_status_check CHECK (status IN ('Pending', 'Paid', 'Failed', 'Fulfilled'));
