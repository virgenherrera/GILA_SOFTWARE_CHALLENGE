import { Component, DestroyRef, ElementRef, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import type { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { ImportService } from '../import.service';
import { extractApiErrorMessage } from '../../shared/utils/api-error';

@Component({
  selector: 'app-import-upload',
  templateUrl: './import-upload.html',
  styleUrl: './import-upload.css',
})
export class ImportUpload {
  private readonly importService = inject(ImportService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly fileInput = viewChild.required<ElementRef<HTMLInputElement>>('fileInputRef');

  protected readonly selectedFile = signal<File | null>(null);
  protected readonly uploading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly dragActive = signal(false);

  protected onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;

    this.selectedFile.set(file);
    this.error.set(null);
  }

  protected onDropZoneClick(): void {
    this.fileInput().nativeElement.click();
  }

  protected onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.dragActive.set(true);
  }

  protected onDragLeave(event: DragEvent): void {
    event.preventDefault();
    this.dragActive.set(false);
  }

  protected onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragActive.set(false);

    const file = event.dataTransfer?.files?.[0] ?? null;

    if (!file) {
      return;
    }

    this.selectedFile.set(file);
    this.error.set(null);
  }

  protected onUpload(): void {
    const file = this.selectedFile();

    if (!file) {
      return;
    }

    this.uploading.set(true);
    this.error.set(null);

    this.importService
      .uploadCsv(file)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.uploading.set(false);
          void this.router.navigate(['/imports', response.job_id]);
        },
        error: (err: HttpErrorResponse) => {
          this.uploading.set(false);
          this.error.set(extractApiErrorMessage(err));
        },
      });
  }
}
