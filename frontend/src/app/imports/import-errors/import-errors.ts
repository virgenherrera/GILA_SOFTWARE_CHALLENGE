import type { OnInit } from '@angular/core';
import { Component, DestroyRef, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import type { HttpErrorResponse } from '@angular/common/http';
import { ImportService } from '../import.service';
import { extractApiErrorMessage } from '../../shared/utils/api-error';
import type { ImportError } from '../import.service';
import type { Paging } from '../../shared/validation/product.schema';

const DEFAULT_PER_PAGE = 20;
const RAW_ROW_TRUNCATE_LENGTH = 60;

@Component({
  selector: 'app-import-errors',
  templateUrl: './import-errors.html',
  styleUrl: './import-errors.css',
})
export class ImportErrors implements OnInit {
  readonly jobId = input.required<string>();

  private readonly importService = inject(ImportService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly errors = signal<ImportError[]>([]);
  protected readonly paging = signal<Paging | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  private readonly page = signal(1);

  ngOnInit(): void {
    this.loadErrors();
  }

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
    this.loadErrors();
  }

  protected onNextPage(): void {
    if (!this.paging()?.next) {
      return;
    }

    this.page.update((current) => current + 1);
    this.loadErrors();
  }

  private loadErrors(): void {
    this.loading.set(true);
    this.error.set(null);

    this.importService
      .getJobErrors(this.jobId(), this.page(), DEFAULT_PER_PAGE)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.errors.set(response.items);
          this.paging.set(response.paging);
          this.loading.set(false);
        },
        error: (err: HttpErrorResponse) => {
          this.error.set(extractApiErrorMessage(err));
          this.loading.set(false);
        },
      });
  }
}
