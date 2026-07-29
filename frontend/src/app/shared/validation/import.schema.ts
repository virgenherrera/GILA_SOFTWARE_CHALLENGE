import { z } from 'zod';

export const ImportStatusSchema = z.enum([
  'Pending',
  'Processing',
  'Completed',
  'CompletedWithErrors',
  'Failed',
]);

export type ImportStatus = z.infer<typeof ImportStatusSchema>;

export const ImportJobSchema = z.object({
  id: z.string(),
  source_filename: z.string(),
  status: ImportStatusSchema,
  started_at: z.string().nullable(),
  completed_at: z.string().nullable(),
  total_rows: z.number().int().nonnegative(),
  accepted_rows: z.number().int().nonnegative(),
  rejected_rows: z.number().int().nonnegative(),
});

export type ImportJob = z.infer<typeof ImportJobSchema>;

export const ImportErrorSchema = z.object({
  row_number: z.number().int().positive(),
  raw_row_data: z.string(),
  field_name: z.string().nullable(),
  error_reason: z.string(),
  product_sku: z.string().nullable(),
});

export type ImportError = z.infer<typeof ImportErrorSchema>;
