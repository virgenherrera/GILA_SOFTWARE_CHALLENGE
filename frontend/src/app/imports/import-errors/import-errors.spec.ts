import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ImportErrors } from './import-errors';
import { findButtonByText } from '../../shared/testing/dom-test-utils';
import type { PagedErrors } from '../import.service';

const mockPage: PagedErrors = {
  items: [
    {
      row_number: 12,
      raw_row_data: 'Sneakers,SN-001,,Footwear,$29.99,50,0.4',
      field_name: 'price',
      error_reason: 'Must be a positive number; currency symbols are not accepted',
      product_sku: 'SN-001',
    },
  ],
  paging: { page: 1, perPage: 20, total: 1, prev: null, next: null },
};

describe('ImportErrors', () => {
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  function createAndFlush(page = mockPage) {
    const fixture = TestBed.createComponent(ImportErrors);

    fixture.componentRef.setInput('jobId', 'job-1');
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === '/api/imports/job-1/errors').flush(page);
    fixture.detectChanges();

    return fixture;
  }

  it('should create and fetch the first page of errors', () => {
    const fixture = createAndFlush();

    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render the error rows', () => {
    const fixture = createAndFlush();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('12');
    expect(compiled.textContent).toContain('price');
    expect(compiled.textContent).toContain('Must be a positive number');
  });

  it('should truncate long raw row data', () => {
    const longRow = 'x'.repeat(80);
    const fixture = createAndFlush({
      items: [{ ...mockPage.items[0], raw_row_data: longRow }],
      paging: mockPage.paging,
    });
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('…');
    expect(compiled.textContent).not.toContain(longRow);
  });

  it('should show an error state when the request fails', () => {
    const fixture = TestBed.createComponent(ImportErrors);

    fixture.componentRef.setInput('jobId', 'job-1');
    fixture.detectChanges();
    httpMock
      .expectOne((r) => r.url === '/api/imports/job-1/errors')
      .flush(
        { error: { code: 'INTERNAL_ERROR', message: 'Something went wrong' } },
        { status: 500, statusText: 'Internal Server Error' },
      );
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('Something went wrong');
  });

  it('should request the next page when Next is clicked', () => {
    const fixture = createAndFlush({
      items: mockPage.items,
      paging: {
        page: 1,
        perPage: 20,
        total: 40,
        prev: null,
        next: '/api/imports/job-1/errors?page=2',
      },
    });

    findButtonByText(fixture.nativeElement as HTMLElement, 'Next')?.click();

    const req = httpMock.expectOne((r) => r.url === '/api/imports/job-1/errors');

    expect(req.request.params.get('page')).toBe('2');
    req.flush(mockPage);
  });

  it('should not request a previous page when there is none', () => {
    const fixture = createAndFlush();

    findButtonByText(fixture.nativeElement as HTMLElement, 'Previous')?.click();

    httpMock.expectNone((r) => r.url === '/api/imports/job-1/errors');
  });
});
