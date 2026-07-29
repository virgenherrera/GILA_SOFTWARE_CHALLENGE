import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ImportService } from './import.service';
import type { ImportJob, PagedErrors, UploadResponse } from './import.service';

describe('ImportService', () => {
  let service: ImportService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(ImportService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('uploadCsv should POST a multipart form containing the file', () => {
    const file = new File(['sku,name'], 'products.csv', { type: 'text/csv' });
    const mockResponse: UploadResponse = {
      job_id: 'f47ac10b-58cc-4372-a567-0e02b2c3d479',
      status: 'Pending',
      message: 'Import job created. Poll /api/imports/{id} for status',
    };
    let result: UploadResponse | undefined;

    service.uploadCsv(file).subscribe((res) => (result = res));

    const req = httpMock.expectOne('/api/imports');

    expect(req.request.method).toBe('POST');
    expect(req.request.body instanceof FormData).toBe(true);
    expect((req.request.body as FormData).get('file')).toBe(file);
    req.flush(mockResponse);

    expect(result).toEqual(mockResponse);
  });

  it('getJobStatus should GET the job by id', () => {
    const mockJob: ImportJob = {
      id: 'job-1',
      source_filename: 'products.csv',
      status: 'CompletedWithErrors',
      started_at: '2026-07-27T14:30:00Z',
      completed_at: '2026-07-27T14:30:05Z',
      total_rows: 100,
      accepted_rows: 92,
      rejected_rows: 8,
    };
    let result: ImportJob | undefined;

    service.getJobStatus('job-1').subscribe((res) => (result = res));

    const req = httpMock.expectOne('/api/imports/job-1');

    expect(req.request.method).toBe('GET');
    req.flush(mockJob);

    expect(result).toEqual(mockJob);
  });

  it('getJobErrors should GET the paged errors with query params', () => {
    const mockResponse: PagedErrors = {
      items: [
        {
          row_number: 12,
          raw_row_data: 'Sneakers,SN-001,,Footwear,$29.99,50,0.4',
          field_name: 'price',
          error_reason: 'Must be a positive number; currency symbols are not accepted',
          product_sku: 'SN-001',
        },
      ],
      paging: { page: 1, perPage: 20, total: 8, prev: null, next: null },
    };
    let result: PagedErrors | undefined;

    service.getJobErrors('job-1', 1, 20).subscribe((res) => (result = res));

    const req = httpMock.expectOne(
      (r) => r.url === '/api/imports/job-1/errors' && r.method === 'GET',
    );

    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('perPage')).toBe('20');
    req.flush(mockResponse);

    expect(result).toEqual(mockResponse);
  });

  it('getJobErrors should default to page 1 and perPage 20', () => {
    service.getJobErrors('job-1').subscribe();

    const req = httpMock.expectOne((r) => r.url === '/api/imports/job-1/errors');

    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('perPage')).toBe('20');
    req.flush({ items: [], paging: { page: 1, perPage: 20, total: 0, prev: null, next: null } });
  });
});
