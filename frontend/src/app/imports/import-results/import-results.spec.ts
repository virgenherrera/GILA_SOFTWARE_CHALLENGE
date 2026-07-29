import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { ImportResults } from './import-results';
import type { ImportJob } from '../../shared/validation/import.schema';

function mockJob(overrides: Partial<ImportJob> = {}): ImportJob {
  return {
    id: 'job-1',
    source_filename: 'products.csv',
    status: 'Pending',
    started_at: '2026-07-27T14:30:00Z',
    completed_at: null,
    total_rows: 0,
    accepted_rows: 0,
    rejected_rows: 0,
    ...overrides,
  };
}

async function settle(fixture: ComponentFixture<unknown>): Promise<void> {
  fixture.detectChanges();
  await Promise.resolve();
  fixture.detectChanges();
}

describe('ImportResults', () => {
  let httpMock: HttpTestingController;

  function setup(id = 'job-1') {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { paramMap: of(convertToParamMap({ id })) },
        },
      ],
    });

    httpMock = TestBed.inject(HttpTestingController);

    return TestBed.createComponent(ImportResults);
  }

  afterEach(() => {
    httpMock.verify();
    vi.useRealTimers();
  });

  it('should create and fetch the job status', async () => {
    const fixture = setup();

    fixture.detectChanges();
    httpMock.expectOne('/api/imports/job-1').flush(mockJob({ status: 'Processing' }));
    await settle(fixture);

    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render a status indicator for the current status', async () => {
    const fixture = setup();

    fixture.detectChanges();
    httpMock.expectOne('/api/imports/job-1').flush(mockJob({ status: 'Processing' }));
    await settle(fixture);

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('Processing');
  });

  it('should poll again while the job is still processing', async () => {
    vi.useFakeTimers();

    const fixture = setup();

    fixture.detectChanges();
    httpMock.expectOne('/api/imports/job-1').flush(mockJob({ status: 'Processing' }));
    await settle(fixture);

    vi.advanceTimersByTime(2000);
    await settle(fixture);

    const req = httpMock.expectOne('/api/imports/job-1');

    req.flush(mockJob({ status: 'Processing' }));
  });

  it('should stop polling once the job reaches a terminal status', async () => {
    vi.useFakeTimers();

    const fixture = setup();

    fixture.detectChanges();
    httpMock
      .expectOne('/api/imports/job-1')
      .flush(mockJob({ status: 'Completed', total_rows: 10, accepted_rows: 10, rejected_rows: 0 }));
    await settle(fixture);

    vi.advanceTimersByTime(10000);
    await settle(fixture);

    httpMock.expectNone('/api/imports/job-1');
  });

  it('should show the results summary when the job completes', async () => {
    const fixture = setup();

    fixture.detectChanges();
    httpMock
      .expectOne('/api/imports/job-1')
      .flush(
        mockJob({ status: 'Completed', total_rows: 100, accepted_rows: 100, rejected_rows: 0 }),
      );
    await settle(fixture);

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('100');
    expect(compiled.textContent).not.toContain('Rejected Rows');
  });

  it('should show the import-errors table when there are rejected rows', async () => {
    const fixture = setup();

    fixture.detectChanges();
    httpMock.expectOne('/api/imports/job-1').flush(
      mockJob({
        status: 'CompletedWithErrors',
        total_rows: 100,
        accepted_rows: 92,
        rejected_rows: 8,
      }),
    );
    await settle(fixture);

    httpMock
      .expectOne((r) => r.url === '/api/imports/job-1/errors')
      .flush({
        items: [],
        paging: { page: 1, perPage: 20, total: 8, prev: null, next: null },
      });
    await settle(fixture);

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('92');
    expect(compiled.textContent).toContain('Rejected Rows');
  });

  it('should show stats and the error table when the job fails with rejected rows', async () => {
    const fixture = setup();

    fixture.detectChanges();
    httpMock.expectOne('/api/imports/job-1').flush(
      mockJob({
        status: 'Failed',
        total_rows: 100,
        accepted_rows: 0,
        rejected_rows: 100,
      }),
    );
    await settle(fixture);

    httpMock
      .expectOne((r) => r.url === '/api/imports/job-1/errors')
      .flush({
        items: [],
        paging: { page: 1, perPage: 20, total: 100, prev: null, next: null },
      });
    await settle(fixture);

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('The import job failed to process.');
    expect(compiled.textContent).toContain('100');
    expect(compiled.textContent).toContain('Rejected Rows');
  });

  it('should show an error state when the request fails', async () => {
    const fixture = setup();

    fixture.detectChanges();
    httpMock
      .expectOne('/api/imports/job-1')
      .flush(
        { error: { code: 'INTERNAL_ERROR', message: 'Something went wrong' } },
        { status: 500, statusText: 'Internal Server Error' },
      );
    await settle(fixture);

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('Something went wrong');
  });

  it('should render a link back to the products list', async () => {
    const fixture = setup();

    fixture.detectChanges();
    httpMock
      .expectOne('/api/imports/job-1')
      .flush(mockJob({ status: 'Completed', total_rows: 10, accepted_rows: 10, rejected_rows: 0 }));
    await settle(fixture);

    const link = (fixture.nativeElement as HTMLElement).querySelector('a[href="/products"]');

    expect(link?.textContent).toContain('Go to products');
  });
});
