import { Injectable, inject, signal } from '@angular/core';
import { Api } from './api';
import { UserRef } from './models';

export interface Person extends UserRef { jobTitle?: string; role: string; }

/** Active people, loaded once per page load, for pickers and mentions. */
@Injectable({ providedIn: 'root' })
export class People {
  private api = inject(Api);
  readonly all = signal<Person[]>([]);
  private loading: Promise<Person[]> | null = null;

  load(): Promise<Person[]> {
    if (!this.loading) {
      this.loading = this.api.get<Person[]>('/api/people').then(p => { this.all.set(p); return p; }).catch(() => { this.loading = null; return []; });
    }
    return this.loading;
  }
}
