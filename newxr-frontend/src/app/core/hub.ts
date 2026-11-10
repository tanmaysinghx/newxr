import { Injectable, inject } from '@angular/core';
import { Api } from './api';

export interface WorkItem { app: string; kind: string; key: string; title: string; subtitle: string; status: string; priority: string; link: string; updatedAt: string; due?: string; snippet?: string; }
export interface RecentItem { app: string; kind: string; key: string; title: string; subtitle: string; link: string; viewedAt: string; }
export interface StarItem { id: number; app: string; kind: string; title: string; link: string; }

/** Recent views and stars, shared by every app. */
@Injectable({ providedIn: 'root' })
export class Hub {
  private api = inject(Api);

  viewed(v: { app: string; kind: string; key?: string; title: string; subtitle?: string; link: string }) {
    this.api.post('/api/hub/recent', v).catch(() => null);
  }

  async isStarred(link: string): Promise<boolean> {
    try { return (await this.api.get<{ starred: boolean }>('/api/hub/stars/check', { link })).starred; } catch { return false; }
  }

  async toggleStar(s: { app: string; kind: string; title: string; link: string }): Promise<boolean> {
    return (await this.api.post<{ starred: boolean }>('/api/hub/stars', s)).starred;
  }
}
