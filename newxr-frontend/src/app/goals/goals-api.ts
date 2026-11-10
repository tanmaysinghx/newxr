import { Injectable, inject } from '@angular/core';
import { Api } from '../core/api';
import { UserRef } from '../core/models';

export interface GoalRow { id: number; name: string; status: string; progress: number; owner: UserRef | null; team?: string; targetDate?: string; parentId?: number; children: GoalRow[]; lastUpdateAt?: string; }
export interface UpdateView { id: number; author: UserRef | null; status: string; progress?: number; metricValue?: number; body: string; cheers: number; createdAt: string; goalId: number; goalName: string; }
export interface GoalView {
  id: number; name: string; description?: string; status: string; progress: number; owner: UserRef | null; team?: string; startDate?: string; targetDate?: string;
  parentId?: number; parentName?: string; metricName?: string; metricUnit?: string; metricStart?: number; metricTarget?: number; metricCurrent?: number;
  followers: UserRef[]; following: boolean; children: GoalRow[]; updates: UpdateView[];
  links: { id: number; app: string; title: string; link: string; status?: string }[]; series: { at: string; actual?: number; plan?: number }[];
}
export const GOAL_STATUSES = ['ON_TRACK', 'AT_RISK', 'OFF_TRACK', 'COMPLETED', 'PAUSED', 'PENDING'];

@Injectable({ providedIn: 'root' })
export class GoalsApi {
  private api = inject(Api);
  tree(f: Record<string, unknown> = {}) { return this.api.get<GoalRow[]>('/api/goals', f as never); }
  goal(id: number) { return this.api.get<GoalView>(`/api/goals/${id}`); }
  create(b: unknown) { return this.api.post<GoalView>('/api/goals', b); }
  update(id: number, b: unknown) { return this.api.put<GoalView>(`/api/goals/${id}`, b); }
  archive(id: number) { return this.api.delete(`/api/goals/${id}`); }
  postUpdate(id: number, b: unknown) { return this.api.post<GoalView>(`/api/goals/${id}/updates`, b); }
  cheer(id: number) { return this.api.post<{ cheers: number }>(`/api/goals/updates/${id}/cheer`); }
  follow(id: number) { return this.api.post<GoalView>(`/api/goals/${id}/follow`); }
  addLink(id: number, b: unknown) { return this.api.post<GoalView>(`/api/goals/${id}/links`, b); }
  removeLink(id: number) { return this.api.delete(`/api/goals/links/${id}`); }
  feed() { return this.api.get<UpdateView[]>('/api/goals/updates'); }
}
