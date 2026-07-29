import { Component, computed, inject, input, signal } from '@angular/core';
import type { HttpErrorResponse } from '@angular/common/http';
import { ImportService } from '../import.service';
import { extractApiErrorMessage } from '../../shared/utils/api-error';
import type { ImportError } from '../../shared/validation/import.schema';
import type { Paging } from '../../shared/validation/product.schema';

const DEFAULT_PER_PAGE = 20;
const RAW_ROW_TRUNCATE_LENGTH = 60;

@Component({
  selector: 'app-import-errors',
  templateUrl: './import-errors.html',
  styleUrl: './import-errors.css',
})
export class ImportErrors {
  readonly jobId = input.required<string>();

  private readonly importService = inject(ImportService);

  private readonly page = signal(1);

  private readonly errorsResource = this.importService.jobErrorsResource(() => ({
    id: this.jobId(),
    page: this.page(),
    perPage: DEFAULT_PER_PAGE,
  }));

  protected readonly errors = computed<ImportError[]>(() =>
    this.errorsResource.hasValue() ? this.errorsResource.value().items : [],
  );
  protected readonly paging = computed<Paging | null>(() =>
    this.errorsResource.hasValue() ? this.errorsResource.value().paging : null,
  );
  protected readonly loading = computed(() => this.errorsResource.isLoading());
  protected readonly error = computed<string | null>(() => {
    const err = this.errorsResource.error();

    return err ? extractApiErrorMessage(err as HttpErrorResponse) : null;
  });

  protected truncateRow(row: string): string {
    if (row.length <= RAW_ROW_TRUNCATE_LENGTH) {
      return row;
    }

    return `${row.slice(0, RAW_ROW_TRUNCATE_LENGTH)}…`;
  }

  protected onPrevPage(): void {
    if (!this.paging()?.prev) {
      return;
    }

    this.page.update((current) => Math.max(1, current - 1));
  }

  protected onNextPage(): void {
    if (!this.paging()?.next) {
      return;
    }

    this.page.update((current) => current + 1);
  }
}
