import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { ImportUpload } from './import-upload';
import { findButtonByText } from '../../shared/testing/dom-test-utils';
import type { UploadResponse } from '../import.service';

// jsdom does not implement DataTransfer, so the file list is stubbed directly on the input
// instead of going through a real DataTransfer/FileList construction.
function selectFile(fixture: ComponentFixture<ImportUpload>, file: File): void {
  const input = (fixture.nativeElement as HTMLElement).querySelector(
    'input[type="file"]',
  ) as HTMLInputElement;

  Object.defineProperty(input, 'files', { value: [file], configurable: true });
  input.dispatchEvent(new Event('change'));
}

describe('ImportUpload', () => {
  let httpMock: HttpTestingController;
  let navigateSpy: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });

    httpMock = TestBed.inject(HttpTestingController);
    navigateSpy = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(ImportUpload);

    fixture.detectChanges();

    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render a file input that only accepts .csv files', () => {
    const fixture = TestBed.createComponent(ImportUpload);

    fixture.detectChanges();

    const input = (fixture.nativeElement as HTMLElement).querySelector('input[type="file"]');

    expect(input?.getAttribute('accept')).toBe('.csv');
  });

  it('should disable the upload button until a file is selected', () => {
    const fixture = TestBed.createComponent(ImportUpload);

    fixture.detectChanges();

    const button = findButtonByText(fixture.nativeElement as HTMLElement, 'Upload');

    expect(button?.disabled).toBe(true);
  });

  it('should enable the upload button once a file is selected', () => {
    const fixture = TestBed.createComponent(ImportUpload);

    fixture.detectChanges();
    selectFile(fixture, new File(['sku,name'], 'products.csv', { type: 'text/csv' }));
    fixture.detectChanges();

    const button = findButtonByText(fixture.nativeElement as HTMLElement, 'Upload');

    expect(button?.disabled).toBe(false);
  });

  it('should upload the selected file and navigate to the results page', () => {
    const fixture = TestBed.createComponent(ImportUpload);

    fixture.detectChanges();
    selectFile(fixture, new File(['sku,name'], 'products.csv', { type: 'text/csv' }));
    fixture.detectChanges();

    findButtonByText(fixture.nativeElement as HTMLElement, 'Upload')?.click();

    const mockResponse: UploadResponse = {
      job_id: 'job-1',
      status: 'Pending',
      message: 'Import job created. Poll /api/imports/{id} for status',
    };

    httpMock.expectOne('/api/imports').flush(mockResponse);

    expect(navigateSpy).toHaveBeenCalledWith(['/imports', 'job-1']);
  });

  it('should show an error state when the upload fails', () => {
    const fixture = TestBed.createComponent(ImportUpload);

    fixture.detectChanges();
    selectFile(fixture, new File(['sku,name'], 'products.csv', { type: 'text/csv' }));
    fixture.detectChanges();

    findButtonByText(fixture.nativeElement as HTMLElement, 'Upload')?.click();

    httpMock
      .expectOne('/api/imports')
      .flush(
        { error: { code: 'VALIDATION_ERROR', message: 'Invalid file: expected a CSV file' } },
        { status: 400, statusText: 'Bad Request' },
      );
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('Invalid file: expected a CSV file');
    expect(navigateSpy).not.toHaveBeenCalled();
  });
});
