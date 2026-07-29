import { z } from 'zod';

export const OrderItemSchema = z.object({
  product_sku: z.string().min(1).max(50),
  name: z.string().min(1).max(256),
  quantity: z.number().int().positive(),
  unit_price: z.number().positive(),
  line_subtotal: z.number().nonnegative(),
});

export type OrderItem = z.infer<typeof OrderItemSchema>;

export const OrderSchema = z.object({
  id: z.string(),
  status: z.string(),
  placed_at: z.string(),
  items: z.array(OrderItemSchema),
  total_amount: z.number().nonnegative(),
});

export type Order = z.infer<typeof OrderSchema>;
