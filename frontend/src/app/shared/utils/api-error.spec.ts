import type { HttpErrorResponse } from '@angular/common/http';
import { extractApiErrorMessage } from './api-error';

function makeError(overrides: Partial<HttpErrorResponse>): HttpErrorResponse {
  return {
    status: 500,
    error: null,
    ...overrides,
  } as HttpErrorResponse;
}

describe('extractApiErrorMessage', () => {
  it('should return the API-provided message when present', () => {
    const err = makeError({
      status: 400,
      error: { error: { code: 'VALIDATION_ERROR', message: 'Bad input' } },
    });

    expect(extractApiErrorMessage(err)).toBe('Bad input');
  });

  it('should return a network message when status is 0', () => {
    const err = makeError({ status: 0, error: null });

    expect(extractApiErrorMessage(err)).toBe(
      'Unable to reach the server. Please check your connection.',
    );
  });

  it('should fall back to a generic message for unrecognized error bodies', () => {
    const err = makeError({ status: 500, error: null });

    expect(extractApiErrorMessage(err)).toBe('Request failed with status 500.');
  });
});
