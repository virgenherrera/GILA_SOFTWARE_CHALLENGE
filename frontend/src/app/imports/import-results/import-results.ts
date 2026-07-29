import { Component, computed, effect, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';
import type { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ImportService, TERMINAL_IMPORT_STATUSES } from '../import.service';
import type { ImportJob } from '../../shared/validation/import.schema';
import { extractApiErrorMessage } from '../../shared/utils/api-error';
import { ImportErrors } from '../import-errors/import-errors';

const POLL_INTERVAL_MS = 2000;

const STATUS_CLASSES: Record<ImportJob['status'], string> = {
  Pending: 'bg-surface-100 text-surface-700',
  Processing: 'bg-brand-50 text-brand-700',
  Completed: 'bg-success-200 text-success-700',
  CompletedWithErrors: 'bg-danger-50 text-danger-700',
  Failed: 'bg-danger-100 text-danger-700',
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

  private readonly jobId = toSignal(
    this.route.paramMap.pipe(map((params) => params.get('id') ?? undefined)),
    { initialValue: undefined },
  );

  private readonly jobResource = this.importService.jobStatusResource(this.jobId);

  protected readonly job = computed<ImportJob | null>(() =>
    this.jobResource.hasValue() ? this.jobResource.value() : null,
  );
  protected readonly loading = computed(() => this.jobResource.isLoading());
  protected readonly error = computed<string | null>(() => {
    const err = this.jobResource.error();

    return err ? extractApiErrorMessage(err as HttpErrorResponse) : null;
  });

  private readonly refetchDelayMs = computed<number | undefined>(() => {
    const job = this.job();

    if (!job || TERMINAL_IMPORT_STATUSES.has(job.status)) {
      return undefined;
    }

    return POLL_INTERVAL_MS;
  });

  constructor() {
    effect((onCleanup) => {
      if (this.jobResource.status() === 'error') {
        return;
      }

      this.jobResource.value();
      const delayMs = this.refetchDelayMs();

      if (delayMs === undefined) {
        return;
      }

      const timeoutId = setTimeout(() => this.jobResource.reload(), delayMs);

      onCleanup(() => clearTimeout(timeoutId));
    });
  }

  protected statusClasses(status: ImportJob['status']): string {
    return STATUS_CLASSES[status];
  }
}
