import { ChangeDetectionStrategy, Component, Injectable, computed, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink, RouterOutlet } from '@angular/router';
import { ApiError } from '../core/api';
import { ago, dateTime, duration, label } from '../core/format';
import { Hub } from '../core/hub';
import { People } from '../core/people';
import { Toasts } from '../core/toasts';
import { AppFrame } from '../shell/shell';
import { NavItem, Sidebar } from '../shell/sidebar';
import { Avatar, Empty, Icon, Modal, Status } from '../shared/ui';
import {
  ALERT_PRIORITIES, AlertRow, AlertView, ComponentView, IMPACTS, INCIDENT_STATUSES, IncidentRow, IncidentView, PulseApi, Stats, TeamSummary,
} from './pulse-api';

/** The open operations team, shared by the layout and its pages. */
@Injectable()
export class PulseContext {
  private pulseApi = inject(PulseApi);
  readonly key = signal('');
  readonly team = signal<TeamSummary | null>(null);
  readonly changed = signal(0);
  async reload() { if (this.key()) this.team.set(await this.pulseApi.team(this.key())); }
  touch() { this.changed.update(n => n + 1); this.reload(); }
}

const SOURCE_ICON: Record<string, string> = { Prometheus: 'chart', Uptime: 'globe', CloudWatch: 'server', 'Crash reports': 'bug', Agent: 'monitor', Manual: 'user', Page: 'phone' };
export const sourceIcon = (s?: string) => SOURCE_ICON[s ?? ''] ?? 'bolt';

// ---- team directory --------------------------------------------------------------

@Component({
  selector: 'nx-pulse-teams',
  imports: [RouterLink, Icon, Avatar, AppFrame, Sidebar],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [`.tile{display:flex;gap:12px;align-items:flex-start;color:var(--text)} .tile:hover{text-decoration:none;border-color:var(--border-strong)}
    .ic{width:36px;height:36px;border-radius:9px;display:inline-flex;align-items:center;justify-content:center;color:#fff;flex:none}`],
  template: `<nx-app-frame><nx-sidebar [context]="{ title: 'Newxr Pulse', subtitle: 'Operations', app: 'pulse' }"
      [items]="[{ label: 'Teams', icon: 'users', link: '/pulse', exact: true }]" />
    <div class="page">
      <div class="title-bar"><div><h1>Operations teams</h1><div class="sub">Alerts, on-call, incidents and status pages.</div></div></div>
      <div class="grid grid-3">
        @for (t of teams(); track t.id) {
          <a class="card tile" [routerLink]="['/pulse', t.key]"><span class="ic" [style.background]="t.color"><nx-icon name="bolt" [size]="20" /></span>
            <div class="grow"><div class="strong">{{ t.name }}</div><div class="small muted">{{ t.description }}</div>
              <div class="small mt-8"><b>{{ t.open }}</b> open · <span [class.danger-text]="t.unacknowledged > 0">{{ t.unacknowledged }} unacknowledged</span></div>
              @if (t.onCall) { <div class="row small mt-8"><nx-avatar [user]="t.onCall" [size]="18" /> {{ t.onCall.name }} on call</div> }</div></a>
        } @empty { <div class="card"><div class="empty">No operations teams yet. Tick “Add sample content” during setup to try Platform Ops.</div></div> }
      </div>
    </div></nx-app-frame>`,
})
export class PulseTeams {
  readonly teams = signal<TeamSummary[]>([]);
  constructor() { inject(PulseApi).teams().then(t => this.teams.set(t)); }
}

// ---- layout ----------------------------------------------------------------------

@Component({
  selector: 'nx-pulse-layout',
  imports: [RouterOutlet, AppFrame, Sidebar],
  providers: [PulseContext],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<nx-app-frame>
    <nx-sidebar [context]="{ title: ctx.team()?.name ?? key(), subtitle: 'Operations', app: 'pulse' }" [items]="items()" />
    <router-outlet /></nx-app-frame>`,
})
export class PulseLayout {
  readonly ctx = inject(PulseContext);
  private router = inject(Router);
  private hub = inject(Hub);
  readonly key = input.required<string>();
  readonly items = computed<NavItem[]>(() => {
    const k = this.key(), t = this.ctx.team();
    return [
      { label: 'Alerts', icon: 'bell', link: `/pulse/${k}/alerts`, count: t?.unacknowledged || null },
      { label: 'Incidents', icon: 'alert', link: `/pulse/${k}/incidents` },
      { label: 'On-call', icon: 'calendar', link: `/pulse/${k}/oncall` },
      { label: 'Status page', icon: 'globe', link: `/pulse/${k}/status` },
      { label: 'Integrations', icon: 'link', link: `/pulse/${k}/integrations` },
      { label: 'Escalations', icon: 'arrow-up', link: `/pulse/${k}/escalations` },
    ];
  });
  constructor() {
    effect(() => {
      const k = this.key();
      this.ctx.key.set(k);
      this.ctx.reload().then(() => {
        const t = this.ctx.team()!;
        this.hub.viewed({ app: 'pulse', kind: 'team', key: t.key, title: t.name, subtitle: 'Pulse · Operations', link: `/pulse/${t.key}` });
      }).catch(() => this.router.navigateByUrl('/pulse'));
    });
  }
}

// ---- create alert / declare incident ---------------------------------------------

@Component({
  selector: 'nx-create-alert',
  imports: [FormsModule, Modal, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<nx-modal title="Create alert" (closed)="closed.emit(null)">
    <div class="modal-body">
      @if (error()) { <div class="banner danger"><nx-icon name="alert" /><div>{{ error() }}</div></div> }
      <div class="field"><label>What's wrong? <span class="req">*</span></label><input class="input" [(ngModel)]="f.title" placeholder="Bookings API returning 500s" autofocus /></div>
      <div class="field"><label>Details</label><textarea class="input" rows="4" [(ngModel)]="f.description"></textarea></div>
      <div class="form-grid">
        <div class="field"><label>Priority</label><select class="input" [(ngModel)]="f.priority">@for (p of priorities; track p) { <option [value]="p">{{ p }}</option> }</select></div>
        <div class="field"><label>Owner</label><select class="input" [(ngModel)]="f.ownerId"><option [ngValue]="null">Whoever is on call</option>
          @for (u of people.all(); track u.id) { <option [ngValue]="u.id">{{ u.name }}</option> }</select></div>
      </div>
      <div class="small muted">The alert runs through the team's escalation policy until someone acknowledges it.</div>
    </div>
    <div class="modal-foot"><button class="btn ghost" (click)="closed.emit(null)">Cancel</button><button class="btn primary" (click)="create()">Create alert</button></div>
  </nx-modal>`,
})
export class CreateAlert {
  private pulseApi = inject(PulseApi);
  private ctx = inject(PulseContext);
  readonly people = inject(People);
  readonly closed = output<AlertView | null>();
  readonly error = signal<string | null>(null);
  readonly priorities = ALERT_PRIORITIES;
  f = { title: '', description: '', priority: 'P3', ownerId: null as number | null };
  constructor() { this.people.load(); }
  async create() {
    try { this.closed.emit(await this.pulseApi.createAlert(this.ctx.key(), this.f)); }
    catch (e) { this.error.set((e as ApiError).message); }
  }
}

@Component({
  selector: 'nx-declare-incident',
  imports: [FormsModule, Modal, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<nx-modal title="Declare incident" (closed)="closed.emit(null)">
    <div class="modal-body">
      @if (error()) { <div class="banner danger"><nx-icon name="alert" /><div>{{ error() }}</div></div> }
      <div class="field"><label>Title <span class="req">*</span></label><input class="input" [(ngModel)]="f.title" placeholder="Elevated errors when paying for bookings" autofocus /></div>
      <div class="form-grid">
        <div class="field"><label>Severity</label><select class="input" [(ngModel)]="f.severity">@for (p of ['P1', 'P2', 'P3', 'P4']; track p) { <option [value]="p">{{ p }}</option> }</select></div>
        <div class="field"><label>Impact</label><select class="input" [(ngModel)]="f.impact">@for (i of impacts; track i) { <option [value]="i">{{ label(i) }}</option> }</select></div>
        <div class="field"><label>Commander</label><select class="input" [(ngModel)]="f.commanderId"><option [ngValue]="null">Me</option>
          @for (u of people.all(); track u.id) { <option [ngValue]="u.id">{{ u.name }}</option> }</select></div>
        <div class="field"><label class="row"><input type="checkbox" [(ngModel)]="f.publicIncident" /> Show on the status page</label></div>
      </div>
      @if (components().length) {
        <div class="field"><label>Affected components</label><div class="row wrap">
          @for (c of components(); track c.id) { <label class="chip" style="cursor:pointer"><input type="checkbox" [checked]="picked.has(c.id)" (change)="toggle(c.id)" /> {{ c.name }}</label> }</div></div>
      }
      <div class="field"><label>First update</label><textarea class="input" rows="3" [(ngModel)]="f.body" placeholder="We are investigating."></textarea></div>
      @if (alertIds().length) { <div class="small muted">{{ alertIds().length }} alert{{ alertIds().length === 1 ? '' : 's' }} will be linked to this incident.</div> }
    </div>
    <div class="modal-foot"><button class="btn ghost" (click)="closed.emit(null)">Cancel</button><button class="btn danger" (click)="declare()">Declare</button></div>
  </nx-modal>`,
})
export class DeclareIncident {
  private pulseApi = inject(PulseApi);
  private ctx = inject(PulseContext);
  readonly people = inject(People);
  readonly alertIds = input<number[]>([]);
  readonly title = input('');
  readonly closed = output<IncidentView | null>();
  readonly error = signal<string | null>(null);
  readonly components = signal<ComponentView[]>([]);
  readonly impacts = IMPACTS;
  label = label;
  picked = new Set<number>();
  f = { title: '', severity: 'P2', impact: 'DEGRADED', commanderId: null as number | null, publicIncident: false, body: '' };
  constructor() {
    this.people.load();
    this.pulseApi.statusPage(this.ctx.key()).then(p => this.components.set(p.components)).catch(() => null);
    queueMicrotask(() => this.f.title = this.title());
  }
  toggle(id: number) { this.picked.has(id) ? this.picked.delete(id) : this.picked.add(id); }
  async declare() {
    try { this.closed.emit(await this.pulseApi.declare(this.ctx.key(), { ...this.f, componentIds: [...this.picked], alertIds: this.alertIds() })); }
    catch (e) { this.error.set((e as ApiError).message); }
  }
}

// ---- alerts ----------------------------------------------------------------------

@Component({
  selector: 'nx-pulse-alerts',
  imports: [FormsModule, RouterLink, Avatar, Icon, Status, CreateAlert, DeclareIncident],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [`.src{display:inline-flex;align-items:center;gap:6px;white-space:nowrap} .seg{display:inline-flex;border:1px solid var(--border);border-radius:8px;overflow:hidden}
    .seg button{border:0;background:var(--surface);padding:6px 10px;font:500 12.5px var(--font);color:var(--text-muted);cursor:pointer} .seg button.on{background:var(--accent-soft);color:var(--accent-hover)}
    .bulk{display:flex;align-items:center;gap:8px;padding:8px 12px;background:var(--surface-selected);border-bottom:1px solid var(--border);font-size:13px}`],
  template: `<div class="page">
    <div class="crumbs"><a routerLink="/pulse">Operations</a><span class="sep">/</span><span>{{ ctx.team()?.name }}</span></div>
    <div class="title-bar"><h1>Alerts</h1>
      <div class="actions"><button class="btn primary" (click)="creating.set(true)"><nx-icon name="plus" /> Create alert</button></div></div>
    <div class="grid grid-4">
      <div class="card"><div class="stat-label"><nx-icon name="bell" [size]="14" /> Open</div><div class="stat-value">{{ stats()?.open ?? '—' }}</div>
        <div class="stat-note" [class.danger-text]="(stats()?.unacknowledged ?? 0) > 0" [class.muted]="!stats()?.unacknowledged">{{ stats()?.unacknowledged ?? 0 }} unacknowledged</div></div>
      <div class="card"><div class="stat-label"><nx-icon name="clock" [size]="14" /> MTTA (7 days)</div><div class="stat-value">{{ secs(stats()?.mttaSeconds) }}</div>
        @if (stats()?.mttaDeltaSeconds != null) { <div class="stat-note" [class.success-text]="stats()!.mttaDeltaSeconds! <= 0" [class.danger-text]="stats()!.mttaDeltaSeconds! > 0">{{ signed(stats()!.mttaDeltaSeconds!) }}s vs last week</div> }</div>
      <div class="card"><div class="stat-label"><nx-icon name="refresh" [size]="14" /> MTTR (7 days)</div><div class="stat-value">{{ stats()?.mttrMinutes != null ? duration(stats()!.mttrMinutes!) : '—' }}</div>
        @if (stats()?.mttrDeltaMinutes != null) { <div class="stat-note" [class.success-text]="stats()!.mttrDeltaMinutes! <= 0" [class.danger-text]="stats()!.mttrDeltaMinutes! > 0">{{ signed(stats()!.mttrDeltaMinutes!) }}m vs last week</div> }</div>
      <div class="card"><div class="stat-label"><nx-icon name="headset" [size]="14" /> On call now</div><div class="stat-value" style="font-size:20px">{{ stats()?.onCall?.name ?? 'Nobody' }}</div>
        @if (stats()?.onCallUntil) { <div class="stat-note muted">until {{ dateTime(stats()!.onCallUntil) }}</div> }</div>
    </div>
    <div class="row wrap">
      <div class="search-input"><nx-icon name="search" /><input class="input" placeholder="Search alerts" [(ngModel)]="q" (ngModelChange)="load()" style="width:200px" /></div>
      <select class="input" style="width:auto" [(ngModel)]="status" (ngModelChange)="load()" aria-label="Status">
        <option value="ACTIVE">Status: Active</option><option value="OPEN">Open</option><option value="ACKNOWLEDGED">Acknowledged</option><option value="CLOSED">Closed</option><option value="ALL">All</option></select>
      <select class="input" style="width:auto" [(ngModel)]="priority" (ngModelChange)="load()"><option value="">Priority</option>@for (p of priorities; track p) { <option [value]="p">{{ p }}</option> }</select>
      <select class="input" style="width:auto" [(ngModel)]="owner" (ngModelChange)="load()"><option [ngValue]="null">Owner</option><option [ngValue]="0">Unassigned</option>
        @for (u of ctx.team()?.members ?? []; track u.id) { <option [ngValue]="u.id">{{ u.name }}</option> }</select>
      <select class="input" style="width:auto" [(ngModel)]="source" (ngModelChange)="load()"><option value="">Source</option>@for (s of sources(); track s) { <option [value]="s">{{ s }}</option> }</select>
      <span class="grow"></span>
      <button class="btn" [class.primary]="status === 'SNOOZED'" (click)="status = status === 'SNOOZED' ? 'ACTIVE' : 'SNOOZED'; load()"><nx-icon name="pause" [size]="14" /> Snoozed</button>
    </div>
    <div class="card flush">
      @if (selected().size) {
        <div class="bulk"><b>{{ selected().size }} selected</b>
          <button class="btn sm" (click)="bulk('acknowledge')">Acknowledge</button><button class="btn sm" (click)="bulk('close')">Close</button>
          <button class="btn sm" (click)="bulk('snooze', 60)">Snooze 1 h</button><button class="btn sm" (click)="declaring.set(true)"><nx-icon name="alert" [size]="13" /> Declare incident</button>
          <span class="grow"></span><button class="btn sm ghost" (click)="clearSelection()">Clear</button></div>
      }
      <div class="table-wrap"><table class="table">
      <thead><tr><th style="width:28px"><input type="checkbox" [checked]="allSelected()" (change)="toggleAll()" aria-label="Select all" /></th>
        <th>Priority</th><th>Alert</th><th>Status</th><th>Source</th><th>Owner</th><th>Count</th><th>Created</th></tr></thead>
      <tbody>@for (a of rows(); track a.id) {
        <tr class="clickable" [class.selected]="selected().has(a.id)" (click)="open(a.id)">
          <td (click)="$event.stopPropagation()"><input type="checkbox" [checked]="selected().has(a.id)" (change)="toggle(a.id)" [attr.aria-label]="'Select ' + a.title" /></td>
          <td><nx-status [value]="a.priority" /></td>
          <td style="max-width:380px"><div class="truncate">{{ a.title }}</div>@if (a.incidentKey) { <div class="tiny subtle">Incident {{ a.incidentKey }}</div> }</td>
          <td><nx-status [value]="a.status" />@if (a.snoozedUntil) { <span class="tiny subtle"> · until {{ dateTime(a.snoozedUntil) }}</span> }</td>
          <td><span class="src"><nx-icon [name]="icon(a.source)" [size]="14" />{{ a.source }}</span></td>
          <td><div class="row nowrap"><nx-avatar [user]="a.owner" [size]="20" /><span [class.subtle]="!a.owner">{{ a.owner?.name ?? 'Unassigned' }}</span></div></td>
          <td>{{ a.count }}</td><td class="muted nowrap">{{ ago(a.createdAt) }}</td></tr>
      } @empty { <tr><td colspan="8"><div class="empty">{{ status === 'SNOOZED' ? 'No snoozed alerts.' : 'No alerts. All quiet.' }}</div></td></tr> }</tbody></table></div>
      <div class="table-foot"><span>{{ rows().length }} {{ status === 'ACTIVE' ? 'active' : label(status).toLowerCase() }} alert{{ rows().length === 1 ? '' : 's' }}</span></div>
    </div>
  </div>
  @if (creating()) { <nx-create-alert (closed)="created($event)" /> }
  @if (declaring()) { <nx-declare-incident [alertIds]="[...selected()]" [title]="firstSelectedTitle()" (closed)="declared($event)" /> }`,
})
export class PulseAlerts {
  private pulseApi = inject(PulseApi);
  private router = inject(Router);
  private toasts = inject(Toasts);
  readonly ctx = inject(PulseContext);
  readonly rows = signal<AlertRow[]>([]);
  readonly stats = signal<Stats | null>(null);
  readonly sources = signal<string[]>([]);
  readonly selected = signal(new Set<number>());
  readonly creating = signal(false);
  readonly declaring = signal(false);
  readonly priorities = ALERT_PRIORITIES;
  readonly allSelected = computed(() => this.rows().length > 0 && this.rows().every(a => this.selected().has(a.id)));
  ago = ago; dateTime = dateTime; duration = duration; label = label; icon = sourceIcon;
  q = ''; status = 'ACTIVE'; priority = ''; source = ''; owner: number | null = null;

  constructor() {
    effect(() => {
      this.ctx.changed();
      const k = this.ctx.key();
      if (!k) return;
      this.load();
      this.pulseApi.stats(k).then(s => this.stats.set(s));
      this.pulseApi.sources(k).then(s => this.sources.set(s));
    });
  }
  async load() {
    this.rows.set(await this.pulseApi.alerts(this.ctx.key(), { status: this.status, q: this.q, priority: this.priority, source: this.source, owner: this.owner }));
    this.selected.set(new Set());
  }
  open(id: number) { this.router.navigate(['/pulse', this.ctx.key(), 'alerts', id]); }
  toggle(id: number) { const s = new Set(this.selected()); s.has(id) ? s.delete(id) : s.add(id); this.selected.set(s); }
  toggleAll() { this.selected.set(this.allSelected() ? new Set() : new Set(this.rows().map(a => a.id))); }
  clearSelection() { this.selected.set(new Set()); }
  firstSelectedTitle() { return this.rows().find(a => this.selected().has(a.id))?.title ?? ''; }
  secs(s?: number | null) { if (s == null) return '—'; return s < 60 ? `${s}s` : `${Math.floor(s / 60)}m ${s % 60}s`; }
  signed(n: number) { return (n > 0 ? '+' : '') + n; }
  async bulk(action: string, minutes?: number) {
    try { const r = await this.pulseApi.bulk([...this.selected()], action, minutes); this.toasts.info(`${r.updated} alert${r.updated === 1 ? '' : 's'} updated`); this.ctx.touch(); }
    catch (e) { this.toasts.error((e as ApiError).message); }
  }
  created(a: AlertView | null) { this.creating.set(false); if (a) { this.ctx.touch(); this.open(a.id); } }
  declared(i: IncidentView | null) { this.declaring.set(false); if (i) { this.ctx.touch(); this.router.navigate(['/pulse', this.ctx.key(), 'incidents', i.key]); } }
}

// ---- alert detail ----------------------------------------------------------------

const EVENT_ICON: Record<string, string> = {
  RAISED: 'bell', REPEATED: 'refresh', ACKNOWLEDGED: 'check', CLOSED: 'check-circle', REOPENED: 'refresh', ESCALATED: 'arrow-up',
  SNOOZED: 'pause', UNSNOOZED: 'play', ASSIGNED: 'user', NOTE: 'message', INCIDENT: 'alert',
};

@Component({
  selector: 'nx-pulse-alert',
  imports: [FormsModule, RouterLink, Icon, Status, DeclareIncident],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [`.layout{display:grid;grid-template-columns:minmax(0,1fr) 300px;gap:28px;align-items:start} .main{display:flex;flex-direction:column;gap:16px;min-width:0}
    .ev{display:flex;gap:10px;padding:9px 0;border-bottom:1px solid var(--border);font-size:13px} .ev:last-child{border-bottom:0}
    .evi{width:24px;height:24px;border-radius:50%;background:var(--neutral-soft);display:inline-flex;align-items:center;justify-content:center;color:var(--text-muted);flex:none}
    .evi.ESCALATED{background:var(--warning-soft);color:var(--warning)} .evi.ACKNOWLEDGED,.evi.CLOSED{background:var(--success-soft);color:var(--success)} .evi.RAISED{background:var(--danger-soft);color:var(--danger)}
    dl{display:grid;grid-template-columns:100px minmax(0,1fr);gap:8px 10px;margin:0;font-size:13px;align-items:center} dt{color:var(--text-muted)} dd{margin:0;min-width:0}
    .desc{white-space:pre-wrap;font-size:13.5px} @media(max-width:1100px){.layout{grid-template-columns:minmax(0,1fr)}}`],
  template: `@if (a(); as a) { <div class="page">
    <div class="crumbs"><a [routerLink]="['/pulse', a.teamKey]">{{ a.teamName }}</a><span class="sep">/</span><a [routerLink]="['/pulse', a.teamKey, 'alerts']">Alerts</a><span class="sep">/</span><span>#{{ a.id }}</span></div>
    <div class="layout">
      <div class="main">
        <div class="row"><nx-status [value]="a.priority" /><h1 class="grow" style="font-size:21px">{{ a.title }}</h1></div>
        <div class="row wrap">
          @if (a.status === 'OPEN') { <button class="btn primary" (click)="act('acknowledge')"><nx-icon name="check" /> Acknowledge</button> }
          @if (a.status !== 'CLOSED') { <button class="btn" (click)="act('close')"><nx-icon name="check-circle" /> Close</button> }
          @else { <button class="btn" (click)="act('reopen')"><nx-icon name="refresh" /> Reopen</button> }
          @if (a.status !== 'CLOSED') {
            @if (a.snoozedUntil) { <button class="btn" (click)="act('unsnooze')"><nx-icon name="play" /> Unsnooze</button> }
            @else { <select class="input" style="width:auto" [ngModel]="''" (ngModelChange)="$event && act('snooze', +$event)" aria-label="Snooze">
              <option value="">Snooze…</option><option value="30">30 minutes</option><option value="60">1 hour</option><option value="240">4 hours</option><option value="1440">1 day</option></select> }
          }
          @if (!a.incidentKey && a.status !== 'CLOSED') { <button class="btn" (click)="declaring.set(true)"><nx-icon name="alert" /> Declare incident</button> }
        </div>
        @if (a.snoozedUntil) { <div class="banner"><nx-icon name="pause" /><div>Snoozed until {{ dateTime(a.snoozedUntil) }}. Escalation waits until then.</div></div> }
        @if (a.incidentKey) { <div class="banner danger"><nx-icon name="alert" /><div>Part of incident <a [routerLink]="['/pulse', a.teamKey, 'incidents', a.incidentKey]"><b>{{ a.incidentKey }}</b> {{ a.incidentTitle }}</a></div></div> }
        <div class="card"><h3>Details</h3><div class="desc mt-8">{{ a.description || 'No details were sent with this alert.' }}</div></div>
        <div class="card"><h3>Timeline</h3>
          @for (e of a.timeline; track e.id) {
            <div class="ev"><span class="evi" [class]="'evi ' + e.kind"><nx-icon [name]="eventIcon(e.kind)" [size]="13" /></span>
              <div class="grow"><div>@if (e.actor) { <b>{{ e.actor.name }}</b> · }{{ e.text }}</div><div class="tiny subtle">{{ dateTime(e.at) }}</div></div></div>
          }
          <div class="row mt-8"><input class="input" [(ngModel)]="noteBody" placeholder="Add a note to the timeline" (keydown.enter)="note()" /><button class="btn" (click)="note()">Add</button></div>
        </div>
      </div>
      <aside class="col" style="gap:12px">
        <div class="card"><dl>
          <dt>Status</dt><dd><nx-status [value]="a.status" /></dd>
          <dt>Owner</dt><dd><select class="input" style="height:28px" [ngModel]="a.owner?.id ?? null" (ngModelChange)="assign($event)">
            <option [ngValue]="null">Unassigned</option>@for (u of ctx.team()?.members ?? []; track u.id) { <option [ngValue]="u.id">{{ u.name }}</option> }</select></dd>
          <dt>Source</dt><dd>{{ a.source }}</dd>
          <dt>Occurrences</dt><dd>{{ a.count }} · last {{ ago(a.lastOccurredAt) }}</dd>
          <dt>Raised</dt><dd>{{ dateTime(a.createdAt) }}</dd>
          @if (a.acknowledgedAt) { <dt>Acknowledged</dt><dd>{{ ago(a.acknowledgedAt) }}@if (a.acknowledgedBy) { by {{ a.acknowledgedBy.name }} }</dd> }
          @if (a.closedAt) { <dt>Closed</dt><dd>{{ dateTime(a.closedAt) }}</dd> }
          @if (a.nextEscalationAt) { <dt>Next escalation</dt><dd class="warning-text">{{ dateTime(a.nextEscalationAt) }}</dd> }
          @if (a.dedupKey) { <dt>Dedup key</dt><dd class="mono tiny truncate" [title]="a.dedupKey">{{ a.dedupKey }}</dd> }
        </dl></div>
      </aside>
    </div>
  </div>
  @if (declaring()) { <nx-declare-incident [alertIds]="[a.id]" [title]="a.title" (closed)="declared($event)" /> } }`,
})
export class PulseAlert {
  private pulseApi = inject(PulseApi);
  private toasts = inject(Toasts);
  private router = inject(Router);
  private hub = inject(Hub);
  readonly ctx = inject(PulseContext);
  readonly id = input.required<string>();
  readonly a = signal<AlertView | null>(null);
  readonly declaring = signal(false);
  ago = ago; dateTime = dateTime;
  noteBody = '';
  constructor() {
    effect(() => {
      this.pulseApi.alert(+this.id()).then(a => {
        this.a.set(a);
        this.hub.viewed({ app: 'pulse', kind: 'alert', title: a.title, subtitle: `Pulse · ${a.teamName}`, link: `/pulse/${a.teamKey}/alerts/${a.id}` });
      });
    });
  }
  eventIcon(k: string) { return EVENT_ICON[k] ?? 'info'; }
  async act(action: string, minutes?: number) {
    try { this.a.set(await this.pulseApi.act(this.a()!.id, { action, minutes })); this.ctx.touch(); }
    catch (e) { this.toasts.error((e as ApiError).message); }
  }
  async assign(ownerId: number | null) {
    try { this.a.set(await this.pulseApi.act(this.a()!.id, { action: 'assign', ownerId })); }
    catch (e) { this.toasts.error((e as ApiError).message); }
  }
  async note() {
    if (!this.noteBody.trim()) return;
    try { this.a.set(await this.pulseApi.note(this.a()!.id, this.noteBody)); this.noteBody = ''; }
    catch (e) { this.toasts.error((e as ApiError).message); }
  }
  declared(i: IncidentView | null) { this.declaring.set(false); if (i) this.router.navigate(['/pulse', this.ctx.key(), 'incidents', i.key]); }
}

// ---- incidents -------------------------------------------------------------------

@Component({
  selector: 'nx-pulse-incidents',
  imports: [RouterLink, Avatar, Icon, Status, Empty, DeclareIncident],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="page">
    <div class="crumbs"><a routerLink="/pulse">Operations</a><span class="sep">/</span><span>{{ ctx.team()?.name }}</span></div>
    <div class="title-bar"><h1>Incidents</h1><div class="actions"><button class="btn danger" (click)="declaring.set(true)"><nx-icon name="alert" /> Declare incident</button></div></div>
    <div class="tabs"><button [class.active]="!resolved()" (click)="resolved.set(false)">Ongoing</button><button [class.active]="resolved()" (click)="resolved.set(true)">Resolved</button></div>
    <div class="card flush"><div class="table-wrap"><table class="table">
      <thead><tr><th>Key</th><th>Incident</th><th>Status</th><th>Severity</th><th>Impact</th><th>Commander</th><th>{{ resolved() ? 'Duration' : 'Started' }}</th></tr></thead>
      <tbody>@for (i of rows(); track i.id) {
        <tr class="clickable" (click)="open(i.key)">
          <td class="nowrap"><a [routerLink]="['/pulse', ctx.key(), 'incidents', i.key]">{{ i.key }}</a></td>
          <td><div>{{ i.title }}</div><div class="tiny subtle">@if (i.publicIncident) { <nx-icon name="globe" [size]="11" /> Public · }{{ i.components.length ? names(i) : 'No components' }} · {{ i.alerts }} alert{{ i.alerts === 1 ? '' : 's' }}</div></td>
          <td><nx-status [value]="i.status" /></td><td><nx-status [value]="i.severity" /></td><td class="muted nowrap">{{ label(i.impact) }}</td>
          <td><div class="row nowrap"><nx-avatar [user]="i.commander" [size]="20" />{{ i.commander?.name }}</div></td>
          <td class="muted nowrap">{{ resolved() ? duration(mins(i)) : ago(i.startedAt) }}</td></tr>
      } @empty { <tr><td colspan="7"><nx-empty icon="check-circle" [title]="resolved() ? 'No resolved incidents' : 'No ongoing incidents'" /></td></tr> }</tbody></table></div></div>
  </div>
  @if (declaring()) { <nx-declare-incident (closed)="declared($event)" /> }`,
})
export class PulseIncidents {
  private pulseApi = inject(PulseApi);
  private router = inject(Router);
  readonly ctx = inject(PulseContext);
  readonly rows = signal<IncidentRow[]>([]);
  readonly resolved = signal(false);
  readonly declaring = signal(false);
  ago = ago; label = label; duration = duration;
  constructor() {
    effect(() => { const r = this.resolved(); this.ctx.changed(); if (this.ctx.key()) this.pulseApi.incidents(this.ctx.key(), r).then(x => this.rows.set(x)); });
    if (inject(ActivatedRoute).snapshot.queryParamMap.get('declare')) this.declaring.set(true);
  }
  names(i: IncidentRow) { return i.components.map(c => c.name).join(', '); }
  mins(i: IncidentRow) { return Math.round((new Date(i.resolvedAt!).getTime() - new Date(i.startedAt).getTime()) / 60000); }
  open(k: string) { this.router.navigate(['/pulse', this.ctx.key(), 'incidents', k]); }
  declared(i: IncidentView | null) { this.declaring.set(false); if (i) { this.ctx.touch(); this.open(i.key); } }
}

@Component({
  selector: 'nx-pulse-incident',
  imports: [FormsModule, RouterLink, Icon, Status],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [`.layout{display:grid;grid-template-columns:minmax(0,1fr) 300px;gap:28px;align-items:start} .main{display:flex;flex-direction:column;gap:16px;min-width:0}
    .up{display:grid;grid-template-columns:110px minmax(0,1fr);gap:12px;padding:12px 0;border-bottom:1px solid var(--border)} .up:last-child{border-bottom:0}
    .body{white-space:pre-wrap;font-size:13.5px} dl{display:grid;grid-template-columns:100px minmax(0,1fr);gap:8px 10px;margin:0;font-size:13px;align-items:center}
    dt{color:var(--text-muted)} dd{margin:0;min-width:0} .composer{display:flex;flex-direction:column;gap:10px}
    @media(max-width:1100px){.layout{grid-template-columns:minmax(0,1fr)}}`],
  template: `@if (i(); as i) { <div class="page">
    <div class="crumbs"><a [routerLink]="['/pulse', i.teamKey]">{{ i.teamName }}</a><span class="sep">/</span><a [routerLink]="['/pulse', i.teamKey, 'incidents']">Incidents</a><span class="sep">/</span><span>{{ i.key }}</span></div>
    <div class="layout">
      <div class="main">
        <div class="row"><nx-status [value]="i.severity" /><h1 class="grow" style="font-size:21px">{{ i.title }}</h1><nx-status [value]="i.status" /></div>
        <div class="small muted">Started {{ dateTime(i.startedAt) }}@if (i.resolvedAt) { · resolved {{ dateTime(i.resolvedAt) }} }
          @if (i.publicIncident && i.statusSlug) { · <a [routerLink]="['/status', i.statusSlug]" target="_blank"><nx-icon name="globe" [size]="12" /> on the status page</a> }</div>
        <div class="card composer">
          <h3>Post an update</h3>
          <div class="row wrap"><select class="input" style="width:auto" [(ngModel)]="up.status" aria-label="Status">@for (s of statuses; track s) { <option [value]="s">{{ label(s) }}</option> }</select>
            <select class="input" style="width:auto" [(ngModel)]="up.impact" aria-label="Impact">@for (x of impacts; track x) { <option [value]="x">{{ label(x) }}</option> }</select>
            @if (i.publicIncident) { <label class="row small"><input type="checkbox" [(ngModel)]="up.publicUpdate" /> Show on status page</label> }</div>
          <textarea class="input" rows="3" [(ngModel)]="up.body" placeholder="What changed? Who is affected? What happens next?"></textarea>
          <div class="row"><span class="grow"></span><button class="btn primary" (click)="post()">Post update</button></div>
        </div>
        <div class="card"><h3>Updates</h3>
          @for (u of i.updates; track u.id) {
            <div class="up"><div><nx-status [value]="u.status" /><div class="tiny subtle mt-8">{{ dateTime(u.at) }}</div></div>
              <div><div class="body">{{ u.body }}</div><div class="tiny subtle mt-8">{{ u.author?.name }}@if (!u.publicUpdate) { · internal }</div></div></div>
          }
        </div>
        <div class="card flush"><div style="padding:14px 16px 6px"><h3>Alerts</h3></div>
          <table class="table"><tbody>@for (a of i.alerts; track a.id) {
            <tr class="clickable" [routerLink]="['/pulse', i.teamKey, 'alerts', a.id]"><td><nx-status [value]="a.priority" /></td><td>{{ a.title }}</td><td><nx-status [value]="a.status" /></td><td class="muted">{{ a.source }}</td></tr>
          } @empty { <tr><td class="subtle small">No alerts linked.</td></tr> }</tbody></table></div>
      </div>
      <aside class="col" style="gap:12px">
        <div class="card"><dl>
          <dt>Commander</dt><dd><select class="input" style="height:28px" [ngModel]="i.commander?.id" (ngModelChange)="edit({ commanderId: $event })">
            @for (u of ctx.team()?.members ?? []; track u.id) { <option [ngValue]="u.id">{{ u.name }}</option> }</select></dd>
          <dt>Severity</dt><dd><select class="input" style="height:28px" [ngModel]="i.severity" (ngModelChange)="edit({ severity: $event })">@for (p of ['P1', 'P2', 'P3', 'P4']; track p) { <option [value]="p">{{ p }}</option> }</select></dd>
          <dt>Impact</dt><dd>{{ label(i.impact) }}</dd>
          <dt>Public</dt><dd><label class="row small"><input type="checkbox" [ngModel]="i.publicIncident" (ngModelChange)="edit({ publicIncident: $event })" /> Status page</label></dd>
        </dl></div>
        <div class="card"><h4>Affected components</h4>
          @for (c of components(); track c.id) { <label class="row small" style="padding:3px 0"><input type="checkbox" [checked]="has(c.id)" (change)="toggleComponent(c.id)" /> {{ c.name }}</label> }
          @empty { <div class="small subtle">Add components on the status page first.</div> }</div>
      </aside>
    </div>
  </div> }`,
})
export class PulseIncident {
  private pulseApi = inject(PulseApi);
  private toasts = inject(Toasts);
  private hub = inject(Hub);
  readonly ctx = inject(PulseContext);
  readonly incidentKey = input.required<string>();
  readonly i = signal<IncidentView | null>(null);
  readonly components = signal<ComponentView[]>([]);
  readonly statuses = INCIDENT_STATUSES; readonly impacts = IMPACTS;
  label = label; dateTime = dateTime;
  up = { status: 'INVESTIGATING', impact: 'DEGRADED', body: '', publicUpdate: true };
  constructor() {
    effect(() => { this.load(this.incidentKey()); });
    effect(() => { const k = this.ctx.key(); if (k) this.pulseApi.statusPage(k).then(p => this.components.set(p.components)); });
  }
  async load(key: string) {
    const i = await this.pulseApi.incident(key);
    this.set(i);
    this.hub.viewed({ app: 'pulse', kind: 'incident', key: i.key, title: i.title, subtitle: `Pulse · ${i.teamName}`, link: `/pulse/${i.teamKey}/incidents/${i.key}` });
  }
  private set(i: IncidentView) { this.i.set(i); this.up = { status: i.status, impact: i.impact, body: '', publicUpdate: i.publicIncident }; }
  has(id: number) { return this.i()?.components.some(c => c.id === id) ?? false; }
  toggleComponent(id: number) {
    const ids = this.i()!.components.map(c => c.id);
    this.edit({ componentIds: ids.includes(id) ? ids.filter(x => x !== id) : [...ids, id] });
  }
  async edit(b: Record<string, unknown>) {
    try { this.set(await this.pulseApi.editIncident(this.i()!.key, b)); } catch (e) { this.toasts.error((e as ApiError).message); }
  }
  async post() {
    try { this.set(await this.pulseApi.postUpdate(this.i()!.key, this.up)); this.ctx.touch(); this.toasts.info('Update posted'); }
    catch (e) { this.toasts.error((e as ApiError).message); }
  }
}
