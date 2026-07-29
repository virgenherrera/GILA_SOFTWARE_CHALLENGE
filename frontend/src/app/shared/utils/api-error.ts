import type { HttpErrorResponse } from '@angular/common/http';

export interface ApiErrorDetail {
  field: string;
  reason: string;
}

export interface ApiErrorBody {
  error: {
    code: string;
    message: string;
    details?: ApiErrorDetail[];
  };
}

/**
 * Extracts a human-readable message from a failed HttpClient request against this API.
 * Falls back to a generic message when the body doesn't match the documented error envelope
 * (e.g. network failure, where `status === 0`).
 */
export function extractApiErrorMessage(err: HttpErrorResponse): string {
  const body = err.error as Partial<ApiErrorBody> | null;

  if (body?.error?.message) {
    return body.error.message;
  }

  if (err.status === 0) {
    return 'Unable to reach the server. Please check your connection.';
  }

  return `Request failed with status ${err.status}.`;
}
