-- Add CompletedWithErrors to import job status CHECK constraint
ALTER TABLE csv_import_jobs DROP CONSTRAINT IF EXISTS csv_import_jobs_status_check;
ALTER TABLE csv_import_jobs ADD CONSTRAINT csv_import_jobs_status_check
  CHECK (status IN ('Pending', 'Processing', 'Completed', 'CompletedWithErrors', 'Failed'));
