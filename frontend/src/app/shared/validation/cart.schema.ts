import { z } from 'zod';

export const CartItemSchema = z.object({
  product_sku: z.string().min(1).max(50),
  name: z.string().min(1).max(256),
  quantity: z.number().int().positive(),
  unit_price_snapshot: z.number().positive(),
  subtotal: z.number().nonnegative(),
});

export type CartItem = z.infer<typeof CartItemSchema>;

// The backend returns a minimal `{ items: [], total: 0 }` shape when no cart cookie is present
// yet (see api-contract), so the identity/status/timestamp fields are optional here.
export const CartSchema = z.object({
  id: z.string().optional(),
  status: z.string().optional(),
  items: z.array(CartItemSchema),
  total: z.number().nonnegative(),
  created_at: z.string().optional(),
  updated_at: z.string().optional(),
});

export type Cart = z.infer<typeof CartSchema>;
