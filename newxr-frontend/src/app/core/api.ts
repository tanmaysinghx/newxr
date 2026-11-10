import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { Toasts } from './toasts';

export interface ApiError { status: number; message: string; fields?: Record<string, string>; }

/** Thin promise-based wrapper around HttpClient. Errors become {@link ApiError}. */
@Injectable({ providedIn: 'root' })
export class Api {
  private http = inject(HttpClient);
  private toasts = inject(Toasts);

  get<T>(url: string, params?: Record<string, string | number | boolean | null | undefined>): Promise<T> {
    let p = new HttpParams();
    Object.entries(params ?? {}).forEach(([k, v]) => { if (v !== null && v !== undefined && v !== '') p = p.set(k, String(v)); });
    return this.run(this.http.get<T>(url, { params: p }));
  }
  post<T>(url: string, body: unknown = {}, headers?: Record<string, string>): Promise<T> {
    return this.run(this.http.post<T>(url, body, { headers }));
  }
  put<T>(url: string, body: unknown = {}): Promise<T> { return this.run(this.http.put<T>(url, body)); }
  patch<T>(url: string, body: unknown = {}): Promise<T> { return this.run(this.http.patch<T>(url, body)); }
  delete<T>(url: string): Promise<T> { return this.run(this.http.delete<T>(url)); }
  text(url: string): Promise<string> { return this.run(this.http.get(url, { responseType: 'text' })); }

  /** Same as the call, but shows the error as a toast and resolves to null. */
  async quiet<T>(p: Promise<T>): Promise<T | null> {
    try { return await p; } catch (e) { this.toasts.error((e as ApiError).message); return null; }
  }

  private async run<T>(obs: import('rxjs').Observable<T>): Promise<T> {
    try {
      return await firstValueFrom(obs);
    } catch (e) {
      const r = e as HttpErrorResponse;
      const body = r.error && typeof r.error === 'object' ? r.error : null;
      const err: ApiError = {
        status: r.status,
        message: body?.message ?? (r.status === 0 ? 'Cannot reach the Newxr server.' : `Request failed (${r.status}).`),
        fields: body?.fields,
      };
      if (r.status === 401 && !location.pathname.startsWith('/login') && !location.pathname.startsWith('/setup')) {
        location.assign('/login?next=' + encodeURIComponent(location.pathname + location.search));
      }
      throw err;
    }
  }
}
