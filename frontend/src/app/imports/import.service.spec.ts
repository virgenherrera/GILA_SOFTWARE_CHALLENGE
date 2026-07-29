import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ImportService } from './import.service';
import type { JobErrorsRequest, PagedErrors, UploadResponse } from './import.service';
import type { ImportJob } from '../shared/validation/import.schema';

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

  it('jobStatusResource should GET the job by id', async () => {
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
    const jobId = signal<string | undefined>('job-1');
    const resource = TestBed.runInInjectionContext(() => service.jobStatusResource(jobId));

    TestBed.tick();

    const req = httpMock.expectOne('/api/imports/job-1');

    expect(req.request.method).toBe('GET');
    req.flush(mockJob);
    await Promise.resolve();
    TestBed.tick();

    expect(resource.value()).toEqual(mockJob);
  });

  it('jobStatusResource should reload when the id signal changes', async () => {
    const jobId = signal('job-1');
    const resource = TestBed.runInInjectionContext(() => service.jobStatusResource(jobId));

    TestBed.tick();
    httpMock.expectOne('/api/imports/job-1').flush({ id: 'job-1' });
    await Promise.resolve();

    jobId.set('job-2');
    TestBed.tick();

    const req = httpMock.expectOne('/api/imports/job-2');

    expect(req.request.method).toBe('GET');
    req.flush({ id: 'job-2' });
    await Promise.resolve();
    TestBed.tick();

    expect(resource.value()).toEqual({ id: 'job-2' });
  });

  it('jobErrorsResource should GET the paged errors with query params', async () => {
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
    const request = signal<JobErrorsRequest | undefined>({ id: 'job-1', page: 1, perPage: 20 });
    const resource = TestBed.runInInjectionContext(() => service.jobErrorsResource(request));

    TestBed.tick();

    const req = httpMock.expectOne(
      (r) => r.url === '/api/imports/job-1/errors' && r.method === 'GET',
    );

    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('perPage')).toBe('20');
    req.flush(mockResponse);
    await Promise.resolve();
    TestBed.tick();

    expect(resource.value()).toEqual(mockResponse);
  });

  it('jobErrorsResource should not request when the params signal returns undefined', () => {
    const request = signal<JobErrorsRequest | undefined>(undefined);
    const resource = TestBed.runInInjectionContext(() => service.jobErrorsResource(request));

    TestBed.tick();

    httpMock.expectNone((r) => r.url.includes('/errors'));
    expect(resource.value()).toBeUndefined();
  });
});
