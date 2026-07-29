CREATE TABLE import_errors (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  job_id UUID NOT NULL REFERENCES csv_import_jobs(id) ON DELETE CASCADE,
  row_number INTEGER NOT NULL,
  field VARCHAR(100),
  value TEXT,
  error_code VARCHAR(50) NOT NULL,
  message TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_import_errors_job_id ON import_errors (job_id);
