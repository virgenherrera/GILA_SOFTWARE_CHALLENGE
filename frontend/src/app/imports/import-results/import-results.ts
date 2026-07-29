import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { interval, switchMap, takeWhile } from 'rxjs';
import type { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ImportService, TERMINAL_IMPORT_STATUSES } from '../import.service';
import type { ImportJob } from '../import.service';
import { extractApiErrorMessage } from '../../shared/utils/api-error';
import { ImportErrors } from '../import-errors/import-errors';

const POLL_INTERVAL_MS = 2000;

const STATUS_CLASSES: Record<ImportJob['status'], string> = {
  Pending: 'bg-gray-100 text-gray-700',
  Processing: 'bg-blue-100 text-blue-700',
  Completed: 'bg-green-100 text-green-700',
  CompletedWithErrors: 'bg-amber-100 text-amber-700',
  Failed: 'bg-red-100 text-red-700',
};

@Component({
  selector: 'app-import-results',
  imports: [RouterLink, ImportErrors],
  templateUrl: './import-results.html',
  styleUrl: './import-results.css',
})
export class ImportResults {
  private readonly route = inject(ActivatedRoute);
  private readonly importService = inject(ImportService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly job = signal<ImportJob | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  constructor() {
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const id = params.get('id');

      if (id) {
        this.fetchInitialStatus(id);
      }
    });
  }

  protected statusClasses(status: ImportJob['status']): string {
    return STATUS_CLASSES[status];
  }

  private fetchInitialStatus(id: string): void {
    this.loading.set(true);
    this.error.set(null);

    this.importService
      .getJobStatus(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (job) => {
          this.job.set(job);
          this.loading.set(false);

          if (!TERMINAL_IMPORT_STATUSES.has(job.status)) {
            this.startPolling(id);
          }
        },
        error: (err: HttpErrorResponse) => {
          this.loading.set(false);
          this.error.set(extractApiErrorMessage(err));
        },
      });
  }

  private startPolling(id: string): void {
    interval(POLL_INTERVAL_MS)
      .pipe(
        switchMap(() => this.importService.getJobStatus(id)),
        takeWhile((job) => !TERMINAL_IMPORT_STATUSES.has(job.status), true),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (job) => this.job.set(job),
        error: (err: HttpErrorResponse) => this.error.set(extractApiErrorMessage(err)),
      });
  }
}
