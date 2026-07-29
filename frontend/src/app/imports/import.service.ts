import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import type { Observable } from 'rxjs';
import type { Paging } from '../shared/validation/product.schema';

export type ImportStatus =
  | 'Pending'
  | 'Processing'
  | 'Completed'
  | 'CompletedWithErrors'
  | 'Failed';

/** Statuses after which the job no longer changes — polling must stop here. */
export const TERMINAL_IMPORT_STATUSES: ReadonlySet<ImportStatus> = new Set([
  'Completed',
  'CompletedWithErrors',
  'Failed',
]);

export interface UploadResponse {
  job_id: string;
  status: ImportStatus;
  message: string;
}

export interface ImportJob {
  id: string;
  source_filename: string;
  status: ImportStatus;
  started_at: string;
  completed_at: string | null;
  total_rows: number;
  accepted_rows: number;
  rejected_rows: number;
}

export interface ImportError {
  row_number: number;
  raw_row_data: string;
  field_name: string;
  error_reason: string;
  product_sku: string | null;
}

export interface PagedErrors {
  items: ImportError[];
  paging: Paging;
}

@Injectable({ providedIn: 'root' })
export class ImportService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/imports';

  uploadCsv(file: File): Observable<UploadResponse> {
    const formData = new FormData();

    formData.append('file', file);

    return this.http.post<UploadResponse>(this.baseUrl, formData);
  }

  getJobStatus(id: string): Observable<ImportJob> {
    return this.http.get<ImportJob>(`${this.baseUrl}/${id}`);
  }

  getJobErrors(id: string, page = 1, perPage = 20): Observable<PagedErrors> {
    const params = new HttpParams().set('page', page).set('perPage', perPage);

    return this.http.get<PagedErrors>(`${this.baseUrl}/${id}/errors`, { params });
  }
}
