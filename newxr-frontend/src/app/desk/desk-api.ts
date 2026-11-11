import { Injectable, inject } from '@angular/core';
import { Api } from '../core/api';
import { duration } from '../core/format';
import { UserRef } from '../core/models';

export interface Sla { name: string; due?: string; state: 'NONE' | 'RUNNING' | 'BREACHED' | 'PAUSED' | 'MET' | 'MISSED'; minutes: number; }
export interface DeskSummary { id: number; key: string; name: string; description?: string; color: string; icon: string; open: number; }
export interface TypeView { id: number; name: string; description?: string; icon: string; category?: string; deskId: number; deskKey: string; deskName: string; }
export interface Queue { id: string; name: string; count: number; }
export interface SlaPolicy { priority: string; label: string; firstResponseMinutes: number; resolutionMinutes: number; }
export interface DeskView { desk: DeskSummary; queues: Queue[]; types: TypeView[]; policies: SlaPolicy[]; agents: UserRef[]; }
export interface Stats { open: number; newToday: number; avgFirstResponseMinutes?: number; breachingSoon: number; csat?: number; csatDelta?: number; }
export interface RequestRow {
  id: number; key: string; summary: string; reporter: UserRef | null; assignee: UserRef | null; type?: string; category?: string;
  status: string; priority: string; firstResponse: Sla; resolution: Sla; createdAt: string; updatedAt: string;
}
export interface CommentView { id: number; author: UserRef | null; body: string; internal: boolean; createdAt: string; }
export interface Similar { key: string; summary: string; status: string; resolvedAt?: string; resolution?: string; }
export interface CustomerView { user: UserRef; jobTitle?: string; organization?: string; open: number; total: number; csat?: number; }
export interface RequestView {
  id: number; key: string; summary: string; description?: string; deskKey: string; deskName: string; type?: TypeView;
  reporter: UserRef | null; assignee: UserRef | null; status: string; priority: string; organization?: string; component?: string; linked?: string;
  firstResponse: Sla; resolution: Sla; csat?: number; createdAt: string; updatedAt: string; resolvedAt?: string;
  comments: CommentView[]; similar: Similar[]; customer?: CustomerView;
}
export interface HelpHome { desks: DeskSummary[]; popular: TypeView[]; mine: RequestRow[]; }

export const REQUEST_STATUSES = ['WAITING_FOR_SUPPORT', 'IN_PROGRESS', 'WAITING_FOR_CUSTOMER', 'ESCALATED', 'RESOLVED', 'CLOSED'];
export const PRIORITIES = ['P1', 'P2', 'P3', 'P4'];

/** "-12m", "1h 40m", "Paused", "Met" — the queue's time-to-resolution cell. */
export function slaText(s: Sla): string {
  switch (s.state) {
    case 'NONE': return '—';
    case 'PAUSED': return 'Paused';
    case 'MET': return 'Met';
    case 'MISSED': return 'Missed';
    case 'BREACHED': return '-' + duration(s.minutes);
    default: return duration(s.minutes);
  }
}

/** Tone for an SLA: breached/missed are danger, under 30 minutes warning, paused neutral. */
export function slaTone(s: Sla): string {
  if (s.state === 'BREACHED' || s.state === 'MISSED') return 'danger';
  if (s.state === 'MET') return 'success';
  if (s.state === 'RUNNING') return s.minutes <= 30 ? 'warning' : 'success';
  return '';
}

@Injectable({ providedIn: 'root' })
export class DeskApi {
  private api = inject(Api);
  desks() { return this.api.get<DeskSummary[]>('/api/desk/desks'); }
  desk(key: string) { return this.api.get<DeskView>(`/api/desk/desks/${key}`); }
  queue(key: string, f: Record<string, string | number | null | undefined>) { return this.api.get<RequestRow[]>(`/api/desk/desks/${key}/requests`, f); }
  stats(key: string) { return this.api.get<Stats>(`/api/desk/desks/${key}/stats`); }
  customers(key: string) { return this.api.get<CustomerView[]>(`/api/desk/desks/${key}/customers`); }
  raise(key: string, b: unknown) { return this.api.post<RequestView>(`/api/desk/desks/${key}/requests`, b); }
  request(key: string) { return this.api.get<RequestView>(`/api/desk/requests/${key}`); }
  edit(key: string, b: unknown) { return this.api.put<RequestView>(`/api/desk/requests/${key}`, b); }
  comment(key: string, body: string, internal: boolean) { return this.api.post<RequestView>(`/api/desk/requests/${key}/comments`, { body, internal }); }

  help() { return this.api.get<HelpHome>('/api/desk/help'); }
  helpTypes(deskKey: string) { return this.api.get<TypeView[]>(`/api/desk/help/desks/${deskKey}`); }
  myRequests() { return this.api.get<RequestRow[]>('/api/desk/help/requests'); }
  helpRaise(deskKey: string, b: unknown) { return this.api.post<RequestView>(`/api/desk/help/desks/${deskKey}/requests`, b); }
  helpRequest(key: string) { return this.api.get<RequestView>(`/api/desk/help/requests/${key}`); }
  helpComment(key: string, body: string) { return this.api.post<RequestView>(`/api/desk/help/requests/${key}/comments`, { body }); }
  helpResolve(key: string) { return this.api.post<RequestView>(`/api/desk/help/requests/${key}/resolve`); }
  rate(key: string, rating: number) { return this.api.post<RequestView>(`/api/desk/help/requests/${key}/csat`, { rating }); }
}
