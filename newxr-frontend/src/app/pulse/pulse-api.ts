import { Injectable, inject } from '@angular/core';
import { Api } from '../core/api';
import { UserRef } from '../core/models';

export interface TeamSummary { id: number; key: string; name: string; description?: string; color: string; open: number; unacknowledged: number; onCall: UserRef | null; statusSlug?: string; statusPublic: boolean; members: UserRef[]; }
export interface AlertRow { id: number; title: string; priority: string; status: string; source?: string; owner: UserRef | null; count: number; createdAt: string; lastOccurredAt?: string; snoozedUntil?: string; incidentKey?: string; }
export interface EventView { id: number; actor: UserRef | null; kind: string; text: string; at: string; }
export interface AlertView {
  id: number; teamKey: string; teamName: string; title: string; description?: string; priority: string; status: string; source?: string; dedupKey?: string;
  owner: UserRef | null; count: number; createdAt: string; lastOccurredAt?: string; acknowledgedAt?: string; acknowledgedBy?: UserRef | null; closedAt?: string;
  snoozedUntil?: string; nextEscalationAt?: string; incidentKey?: string; incidentTitle?: string; timeline: EventView[];
}
export interface Stats { open: number; unacknowledged: number; mttaSeconds?: number; mttaDeltaSeconds?: number; mttrMinutes?: number; mttrDeltaMinutes?: number; onCall: UserRef | null; onCallUntil?: string; nextOnCall?: UserRef | null; }
export interface ComponentRef { id: number; name: string; }
export interface IncidentRow { id: number; key: string; title: string; status: string; severity: string; impact: string; commander: UserRef | null; publicIncident: boolean; components: ComponentRef[]; startedAt: string; resolvedAt?: string; alerts: number; }
export interface UpdateView { id: number; author: UserRef | null; status: string; body: string; publicUpdate: boolean; at: string; }
export interface IncidentView extends Omit<IncidentRow, 'alerts'> { teamKey: string; teamName: string; updates: UpdateView[]; alerts: AlertRow[]; statusSlug?: string; }
export interface ShiftView { start: string; end: string; user: UserRef | null; override: boolean; }
export interface LayerView { id: number; name: string; rotationDays: number; rotationStart: string; members: UserRef[]; shifts: ShiftView[]; }
export interface OverrideView { id: number; user: UserRef; startsAt: string; endsAt: string; reason?: string; }
export interface StepView { id: number; position: number; target: string; user: UserRef | null; delayMinutes: number; label: string; }
export interface Schedule { from: string; to: string; now: string; current: ShiftView | null; next: UserRef | null; layers: LayerView[]; overrides: OverrideView[]; finalShifts: ShiftView[]; escalation: StepView[]; }
export interface Day { date: string; status: string; downtimeMinutes: number; incidents: string[]; }
export interface ComponentView { id: number; name: string; description?: string; position: number; manualStatus: string; status: string; uptime: number; days: Day[]; }
export interface PublicUpdate { status: string; body: string; at: string; }
export interface PublicIncident { key: string; title: string; status: string; impact: string; components: string[]; startedAt: string; resolvedAt?: string; updates: PublicUpdate[]; }
export interface PageView { slug?: string; title: string; published: boolean; overall: string; components: ComponentView[]; active: PublicIncident[]; recent: PublicIncident[]; updatedAt: string; }
export interface IntegrationView { id: number; name: string; format: string; icon: string; defaultPriority: string; tokenHint?: string; eventCount: number; lastEventAt?: string; enabled: boolean; createdAt: string; }
export interface Created { integration: IntegrationView; webhookPath: string; }

export const ALERT_PRIORITIES = ['P1', 'P2', 'P3', 'P4', 'P5'];
export const INCIDENT_STATUSES = ['INVESTIGATING', 'IDENTIFIED', 'MONITORING', 'RESOLVED'];
export const IMPACTS = ['DEGRADED', 'PARTIAL_OUTAGE', 'MAJOR_OUTAGE'];
export const COMPONENT_STATUSES = ['OPERATIONAL', 'MAINTENANCE', 'DEGRADED', 'PARTIAL_OUTAGE', 'MAJOR_OUTAGE'];

/** Day-bar colour for a component status. */
export const statusColor = (s: string) =>
  s === 'MAJOR_OUTAGE' ? 'var(--danger)' : s === 'PARTIAL_OUTAGE' ? 'var(--orange)' : s === 'DEGRADED' ? 'var(--warning)' : s === 'MAINTENANCE' ? 'var(--info)' : 'var(--success)';

@Injectable({ providedIn: 'root' })
export class PulseApi {
  private api = inject(Api);
  teams() { return this.api.get<TeamSummary[]>('/api/pulse/teams'); }
  team(key: string) { return this.api.get<TeamSummary>(`/api/pulse/teams/${key}`); }
  alerts(key: string, f: Record<string, string | number | null | undefined>) { return this.api.get<AlertRow[]>(`/api/pulse/teams/${key}/alerts`, f); }
  stats(key: string) { return this.api.get<Stats>(`/api/pulse/teams/${key}/stats`); }
  sources(key: string) { return this.api.get<string[]>(`/api/pulse/teams/${key}/sources`); }
  createAlert(key: string, b: unknown) { return this.api.post<AlertView>(`/api/pulse/teams/${key}/alerts`, b); }
  alert(id: number) { return this.api.get<AlertView>(`/api/pulse/alerts/${id}`); }
  act(id: number, b: { action: string; minutes?: number; ownerId?: number | null; note?: string }) { return this.api.post<AlertView>(`/api/pulse/alerts/${id}`, b); }
  bulk(ids: number[], action: string, minutes?: number) { return this.api.post<{ updated: number }>('/api/pulse/alerts/bulk', { ids, action, minutes }); }
  note(id: number, body: string) { return this.api.post<AlertView>(`/api/pulse/alerts/${id}/notes`, { body }); }

  incidents(key: string, resolved = false) { return this.api.get<IncidentRow[]>(`/api/pulse/teams/${key}/incidents`, { resolved }); }
  declare(key: string, b: unknown) { return this.api.post<IncidentView>(`/api/pulse/teams/${key}/incidents`, b); }
  incident(key: string) { return this.api.get<IncidentView>(`/api/pulse/incidents/${key}`); }
  editIncident(key: string, b: unknown) { return this.api.put<IncidentView>(`/api/pulse/incidents/${key}`, b); }
  postUpdate(key: string, b: unknown) { return this.api.post<IncidentView>(`/api/pulse/incidents/${key}/updates`, b); }
  linkAlert(key: string, alertId: number) { return this.api.post<IncidentView>(`/api/pulse/incidents/${key}/alerts/${alertId}`); }

  schedule(key: string, from?: string) { return this.api.get<Schedule>(`/api/pulse/teams/${key}/oncall`, { from }); }
  addOverride(key: string, b: unknown) { return this.api.post<OverrideView>(`/api/pulse/teams/${key}/oncall/overrides`, b); }
  removeOverride(key: string, id: number) { return this.api.delete(`/api/pulse/teams/${key}/oncall/overrides/${id}`); }
  editLayer(key: string, id: number, b: unknown) { return this.api.put<LayerView>(`/api/pulse/teams/${key}/oncall/layers/${id}`, b); }
  page(key: string, b: unknown) { return this.api.post<AlertRow>(`/api/pulse/teams/${key}/page`, b); }
  message(key: string, b: unknown) { return this.api.post<{ sentTo: string }>(`/api/pulse/teams/${key}/message`, b); }
  escalation(key: string) { return this.api.get<StepView[]>(`/api/pulse/teams/${key}/escalation`); }
  saveEscalation(key: string, steps: unknown[]) { return this.api.put<StepView[]>(`/api/pulse/teams/${key}/escalation`, steps); }

  statusPage(key: string) { return this.api.get<PageView>(`/api/pulse/teams/${key}/status-page`); }
  saveStatusPage(key: string, b: unknown) { return this.api.put<PageView>(`/api/pulse/teams/${key}/status-page`, b); }
  addComponent(key: string, b: unknown) { return this.api.post<PageView>(`/api/pulse/teams/${key}/components`, b); }
  editComponent(id: number, b: unknown) { return this.api.put<PageView>(`/api/pulse/components/${id}`, b); }
  removeComponent(id: number) { return this.api.delete<PageView>(`/api/pulse/components/${id}`); }
  publicStatus(slug: string) { return this.api.get<PageView>(`/api/public/status/${slug}`); }

  integrations(key: string) { return this.api.get<IntegrationView[]>(`/api/pulse/teams/${key}/integrations`); }
  addIntegration(key: string, b: unknown) { return this.api.post<Created>(`/api/pulse/teams/${key}/integrations`, b); }
  editIntegration(id: number, b: unknown) { return this.api.put<IntegrationView>(`/api/pulse/integrations/${id}`, b); }
  rotate(id: number) { return this.api.post<Created>(`/api/pulse/integrations/${id}/rotate`); }
  removeIntegration(id: number) { return this.api.delete(`/api/pulse/integrations/${id}`); }
}
