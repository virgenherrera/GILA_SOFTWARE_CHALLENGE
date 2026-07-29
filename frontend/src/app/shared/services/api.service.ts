import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api';

  healthCheck() {
    return this.http.get<{ status: string }>(`${this.baseUrl}/health`);
  }
}
