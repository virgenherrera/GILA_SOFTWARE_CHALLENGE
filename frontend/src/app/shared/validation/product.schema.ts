import { z } from 'zod';

export const ProductSchema = z.object({
  sku: z.string().min(1).max(50),
  name: z.string().min(1).max(256),
  description: z.string().max(4096).optional(),
  price: z.number().positive().max(99999.99),
  category: z.string().min(1).max(100),
  stock: z.number().int().nonnegative(),
});

export type Product = z.infer<typeof ProductSchema>;

export const CreateProductSchema = ProductSchema.omit({ sku: true });
export type CreateProduct = z.infer<typeof CreateProductSchema>;

// The backend's UpdateProduct contract (Malli) has the exact same shape as CreateProductSchema
// above (every field except `sku`, since PUT never accepts sku in the body). Reuse it instead
// of duplicating the shape.
export const UpdateProductSchema = CreateProductSchema;
export type UpdateProduct = CreateProduct;

// Full product record as returned by the API (GET/POST/PUT responses).
export const ProductResponseSchema = ProductSchema.extend({
  weight_kg: z.number().nonnegative(),
  created_at: z.string(),
  updated_at: z.string(),
});
export type ProductResponse = z.infer<typeof ProductResponseSchema>;

export const PagingSchema = z.object({
  page: z.number().int().positive(),
  perPage: z.number().int().positive(),
  total: z.number().int().nonnegative(),
  prev: z.string().nullable(),
  next: z.string().nullable(),
});
export type Paging = z.infer<typeof PagingSchema>;
