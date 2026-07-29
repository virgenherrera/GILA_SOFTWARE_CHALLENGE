import { HttpClient, HttpParams } from '@angular/common/http';
import type { ResourceRef } from '@angular/core';
import { Injectable, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import type { Observable } from 'rxjs';
import type { Paging } from '../shared/validation/product.schema';
import type { ImportError, ImportJob, ImportStatus } from '../shared/validation/import.schema';

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

export interface PagedErrors {
  items: ImportError[];
  paging: Paging;
}

export interface JobErrorsRequest {
  id: string;
  page: number;
  perPage: number;
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

  jobStatusResource(id: () => string | undefined): ResourceRef<ImportJob | undefined> {
    return rxResource({
      params: id,
      stream: ({ params: jobId }) => this.http.get<ImportJob>(`${this.baseUrl}/${jobId}`),
    });
  }

  jobErrorsResource(
    request: () => JobErrorsRequest | undefined,
  ): ResourceRef<PagedErrors | undefined> {
    return rxResource({
      params: request,
      stream: ({ params: { id, page, perPage } }) => {
        const httpParams = new HttpParams().set('page', page).set('perPage', perPage);

        return this.http.get<PagedErrors>(`${this.baseUrl}/${id}/errors`, {
          params: httpParams,
        });
      },
    });
  }
}
