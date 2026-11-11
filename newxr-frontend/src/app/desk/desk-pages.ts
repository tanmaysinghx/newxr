import { ChangeDetectionStrategy, Component, Injectable, computed, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink, RouterOutlet } from '@angular/router';
import { ApiError } from '../core/api';
import { Auth } from '../core/auth';
import { ago, dateTime, duration, label } from '../core/format';
import { Hub } from '../core/hub';
import { People } from '../core/people';
import { Toasts } from '../core/toasts';
import { AppFrame } from '../shell/shell';
import { NavGroup, NavItem, Sidebar } from '../shell/sidebar';
import { Avatar, Empty, Icon, Modal, Status } from '../shared/ui';
import {
  CustomerView, DeskApi, DeskSummary, DeskView, PRIORITIES, REQUEST_STATUSES, RequestRow, RequestView, Stats, slaText, slaTone,
} from './desk-api';

/** The open service project, shared by the layout and its pages. */
@Injectable()
export class DeskContext {
  private deskApi = inject(DeskApi);
  readonly key = signal('');
  readonly view = signal<DeskView | null>(null);
  readonly changed = signal(0);
  async reload() {
    if (!this.key()) return;
    this.view.set(await this.deskApi.desk(this.key()));
  }
  touch() { this.changed.update(n => n + 1); this.reload(); }
}

// ---- service project directory -------------------------------------------------

@Component({
  selector: 'nx-desk-projects',
  imports: [RouterLink, Icon, AppFrame, Sidebar],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [`.tile{display:flex;gap:12px;align-items:flex-start;color:var(--text)} .tile:hover{text-decoration:none;border-color:var(--border-strong)}
    .ic{width:36px;height:36px;border-radius:9px;display:inline-flex;align-items:center;justify-content:center;color:#fff;flex:none}`],
  template: `<nx-app-frame><nx-sidebar [context]="{ title: 'Newxr Desk', subtitle: 'Service projects', app: 'desk' }"
      [items]="[{ label: 'Service projects', icon: 'headset', link: '/desk', exact: true }, { label: 'Help center', icon: 'help', link: '/help' }]" />
    <div class="page">
      <div class="title-bar"><div><h1>Service projects</h1><div class="sub">Queues, SLAs and requests from the help center.</div></div>
        <div class="actions"><a class="btn" routerLink="/help"><nx-icon name="globe" /> Open help center</a></div></div>
      <div class="grid grid-3">
        @for (d of desks(); track d.id) {
          <a class="card tile" [routerLink]="['/desk', d.key]"><span class="ic" [style.background]="d.color"><nx-icon [name]="d.icon" [size]="20" /></span>
            <div class="grow"><div class="strong">{{ d.name }}</div><div class="small muted">{{ d.description }}</div>
              <div class="small mt-8"><b>{{ d.open }}</b> open · <span class="subtle">{{ d.key }}</span></div></div></a>
        } @empty { <div class="card"><div class="empty">No service projects yet. Tick “Add sample content” during setup to try the IT Help desk.</div></div> }
      </div>
    </div></nx-app-frame>`,
})
export class DeskProjects {
  readonly desks = signal<DeskSummary[]>([]);
  constructor() { inject(DeskApi).desks().then(d => this.desks.set(d)); }
}

// ---- project layout ------------------------------------------------------------

const QUEUE_ICON: Record<string, string> = { all: 'inbox', mine: 'user', unassigned: 'users', breaching: 'clock', resolved: 'check-circle' };

@Component({
  selector: 'nx-desk-layout',
  imports: [RouterOutlet, AppFrame, Sidebar],
  providers: [DeskContext],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<nx-app-frame>
    <nx-sidebar [context]="{ title: ctx.view()?.desk?.name ?? key(), subtitle: 'Service project', icon: ctx.view()?.desk?.icon ?? 'headset', color: ctx.view()?.desk?.color }"
      [items]="items()" [groups]="groups()" />
    <router-outlet /></nx-app-frame>`,
})
export class DeskLayout {
  readonly ctx = inject(DeskContext);
  private router = inject(Router);
  private hub = inject(Hub);
  readonly key = input.required<string>();
  readonly items = computed<NavItem[]>(() => {
    const k = this.key();
    return [
      { label: 'Queues', icon: 'inbox', link: `/desk/${k}/queues` },
      { label: 'Customers', icon: 'users', link: `/desk/${k}/customers` },
      { label: 'SLAs', icon: 'clock', link: `/desk/${k}/slas` },
      { label: 'Help center', icon: 'globe', link: `/help/${k}` },
    ];
  });
  readonly groups = computed<NavGroup[]>(() => {
    const v = this.ctx.view();
    if (!v) return [];
    return [{ title: 'Queues', items: v.queues.map(q => ({ label: q.name, icon: QUEUE_ICON[q.id] ?? 'tag', link: ['/desk', this.key(), 'queues', q.id], count: q.count })) }];
  });
  constructor() {
    effect(() => {
      const k = this.key();
      this.ctx.key.set(k);
      this.ctx.reload().then(() => {
        const d = this.ctx.view()!.desk;
        this.hub.viewed({ app: 'desk', kind: 'project', key: d.key, title: d.name, subtitle: 'Desk · Service project', link: `/desk/${d.key}` });
      }).catch(() => this.router.navigateByUrl('/desk'));
    });
  }
}

// ---- raise request -------------------------------------------------------------

@Component({
  selector: 'nx-raise-request',
  imports: [FormsModule, Modal, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<nx-modal title="Raise request" (closed)="closed.emit(null)">
    <div class="modal-body">
      @if (error()) { <div class="banner danger"><nx-icon name="alert" /><div>{{ error() }}</div></div> }
      <div class="form-grid">
        <div class="field"><label>Request type</label><select class="input" [(ngModel)]="f.typeId"><option [ngValue]="null">None</option>
          @for (t of ctx.view()?.types ?? []; track t.id) { <option [ngValue]="t.id">{{ t.name }}</option> }</select></div>
        <div class="field"><label>Raise on behalf of</label><select class="input" [(ngModel)]="f.reporterId"><option [ngValue]="null">Me</option>
          @for (u of people.all(); track u.id) { <option [ngValue]="u.id">{{ u.name }}</option> }</select></div>
        <div class="field full"><label>Summary <span class="req">*</span></label><input class="input" [(ngModel)]="f.summary" placeholder="VPN not connecting from Lisbon office" autofocus /></div>
        <div class="field full"><label>Description</label><textarea class="input" rows="5" [(ngModel)]="f.description"></textarea></div>
        <div class="field"><label>Priority</label><select class="input" [(ngModel)]="f.priority">@for (p of priorities; track p) { <option [value]="p">{{ p }}</option> }</select></div>
        <div class="field"><label>Organisation</label><input class="input" [(ngModel)]="f.organization" placeholder="Lisbon office" /></div>
      </div>
    </div>
    <div class="modal-foot"><button class="btn ghost" (click)="closed.emit(null)">Cancel</button><button class="btn primary" (click)="raise()">Raise</button></div>
  </nx-modal>`,
})
export class RaiseRequest {
  private deskApi = inject(DeskApi);
  readonly ctx = inject(DeskContext);
  readonly people = inject(People);
  readonly closed = output<RequestView | null>();
  readonly error = signal<string | null>(null);
  readonly priorities = PRIORITIES;
  f = { typeId: null as number | null, reporterId: null as number | null, summary: '', description: '', priority: 'P3', organization: '' };
  constructor() { this.people.load(); }
  async raise() {
    try { this.closed.emit(await this.deskApi.raise(this.ctx.key(), this.f)); }
    catch (e) { this.error.set((e as ApiError).message); }
  }
}

// ---- queue ---------------------------------------------------------------------

@Component({
  selector: 'nx-desk-queue',
  imports: [FormsModule, RouterLink, Avatar, Icon, Status, RaiseRequest],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [`.sla{display:inline-flex;align-items:center;gap:4px;font:600 12px var(--font);white-space:nowrap}
    .sla.danger{color:var(--danger)} .sla.warning{color:var(--warning)} .sla.success{color:var(--success)} .sla{color:var(--text-muted)}`],
  template: `<div class="page">
    <div class="crumbs"><a routerLink="/desk">Service projects</a><span class="sep">/</span><span>{{ ctx.view()?.desk?.name }}</span></div>
    <div class="title-bar"><h1>{{ queueName() }}</h1>
      <div class="actions"><button class="btn" (click)="exportCsv()"><nx-icon name="download" /> Export</button>
        <button class="btn primary" (click)="raising.set(true)"><nx-icon name="plus" /> Raise request</button></div></div>
    <div class="grid grid-4">
      <div class="card"><div class="stat-label"><nx-icon name="inbox" [size]="14" /> Open</div><div class="stat-value">{{ stats()?.open ?? '—' }}</div>
        <div class="stat-note muted">{{ stats()?.newToday ?? 0 }} new today</div></div>
      <div class="card"><div class="stat-label"><nx-icon name="clock" [size]="14" /> Time to first response</div>
        <div class="stat-value">{{ stats()?.avgFirstResponseMinutes != null ? duration(stats()!.avgFirstResponseMinutes!) : '—' }}</div>
        <div class="stat-note muted">Average, last 30 days</div></div>
      <div class="card"><div class="stat-label"><nx-icon name="alert" [size]="14" /> Breaching soon</div><div class="stat-value">{{ stats()?.breachingSoon ?? '—' }}</div>
        <div class="stat-note" [class.danger-text]="(stats()?.breachingSoon ?? 0) > 0" [class.muted]="!stats()?.breachingSoon">within 30 min</div></div>
      <div class="card"><div class="stat-label"><nx-icon name="smile" [size]="14" /> CSAT (30 days)</div>
        <div class="stat-value">{{ stats()?.csat != null ? stats()!.csat + ' / 5' : '—' }}</div>
        @if (stats()?.csatDelta != null) { <div class="stat-note" [class.success-text]="stats()!.csatDelta! >= 0" [class.danger-text]="stats()!.csatDelta! < 0">{{ stats()!.csatDelta! >= 0 ? '+' : '' }}{{ stats()!.csatDelta }}</div> }</div>
    </div>
    <div class="row wrap">
      <div class="search-input"><nx-icon name="search" /><input class="input" placeholder="Search requests" [(ngModel)]="q" (ngModelChange)="load()" style="width:200px" /></div>
      <select class="input" style="width:auto" [(ngModel)]="type" (ngModelChange)="load()"><option [ngValue]="null">Request type</option>
        @for (t of ctx.view()?.types ?? []; track t.id) { <option [ngValue]="t.id">{{ t.name }}</option> }</select>
      <select class="input" style="width:auto" [(ngModel)]="status" (ngModelChange)="load()"><option value="">Status</option>@for (s of statuses; track s) { <option [value]="s">{{ label(s) }}</option> }</select>
      <select class="input" style="width:auto" [(ngModel)]="priority" (ngModelChange)="load()"><option value="">Priority</option>@for (p of priorities; track p) { <option [value]="p">{{ p }}</option> }</select>
    </div>
    <div class="card flush"><div class="table-wrap"><table class="table">
      <thead><tr><th>Key</th><th>Summary</th><th>Reporter</th><th>Type</th><th>Status</th><th>Priority</th><th>Assignee</th><th>Time to resolution</th></tr></thead>
      <tbody>@for (r of page(); track r.key) {
        <tr class="clickable" (click)="open(r.key)">
          <td class="nowrap"><a [routerLink]="['/desk', ctx.key(), r.key]">{{ r.key }}</a></td>
          <td class="truncate" style="max-width:340px">{{ r.summary }}</td>
          <td><div class="row nowrap"><nx-avatar [user]="r.reporter" [size]="20" /><span>{{ r.reporter?.name }}</span></div></td>
          <td class="muted nowrap">{{ r.category ?? r.type ?? '—' }}</td>
          <td><nx-status [value]="r.status" /></td><td><nx-status [value]="r.priority" /></td>
          <td><div class="row nowrap"><nx-avatar [user]="r.assignee" [size]="20" /><span [class.subtle]="!r.assignee">{{ r.assignee?.name ?? 'Unassigned' }}</span></div></td>
          <td><span class="sla" [class]="'sla ' + slaTone(r.resolution)"><nx-icon [name]="r.resolution.state === 'PAUSED' ? 'pause' : r.resolution.state === 'MET' ? 'check-circle' : 'clock'" [size]="13" />{{ slaText(r.resolution) }}</span></td>
        </tr>
      } @empty { <tr><td colspan="8"><div class="empty">No requests in this queue.</div></td></tr> }</tbody></table></div>
      <div class="table-foot"><span>{{ rows().length ? (pageIdx() * size + 1) + '–' + min((pageIdx() + 1) * size, rows().length) + ' of ' + rows().length : 'No requests' }}</span>
        <div class="row"><button class="btn sm icon" [disabled]="pageIdx() === 0" (click)="pageIdx.set(pageIdx() - 1)" aria-label="Previous page"><nx-icon name="chevron-left" /></button>
          <button class="btn sm icon" [disabled]="(pageIdx() + 1) * size >= rows().length" (click)="pageIdx.set(pageIdx() + 1)" aria-label="Next page"><nx-icon name="chevron-right" /></button></div></div>
    </div>
  </div>
  @if (raising()) { <nx-raise-request (closed)="raised($event)" /> }`,
})
export class DeskQueue {
  private deskApi = inject(DeskApi);
  private router = inject(Router);
  readonly ctx = inject(DeskContext);
  readonly queue = input<string>('all');
  readonly rows = signal<RequestRow[]>([]);
  readonly stats = signal<Stats | null>(null);
  readonly raising = signal(false);
  readonly pageIdx = signal(0);
  readonly size = 25;
  readonly statuses = REQUEST_STATUSES; readonly priorities = PRIORITIES;
  readonly page = computed(() => this.rows().slice(this.pageIdx() * this.size, (this.pageIdx() + 1) * this.size));
  readonly queueName = computed(() => this.ctx.view()?.queues.find(q => q.id === (this.queue() ?? 'all'))?.name ?? 'Queue');
  label = label; duration = duration; slaText = slaText; slaTone = slaTone; min = Math.min;
  q = ''; type: number | null = null; status = ''; priority = '';

  constructor() {
    effect(() => {
      this.queue(); this.ctx.changed();
      if (!this.ctx.key()) return;
      this.load();
      this.deskApi.stats(this.ctx.key()).then(s => this.stats.set(s));
    });
  }
  async load() {
    this.rows.set(await this.deskApi.queue(this.ctx.key(), { queue: this.queue() ?? 'all', q: this.q, type: this.type, status: this.status, priority: this.priority }));
    this.pageIdx.set(0);
  }
  open(k: string) { this.router.navigate(['/desk', this.ctx.key(), k]); }
  raised(r: RequestView | null) { this.raising.set(false); if (r) { this.ctx.touch(); this.open(r.key); } }
  exportCsv() {
    const esc = (v: unknown) => `"${String(v ?? '').replace(/"/g, '""')}"`;
    const lines = [['Key', 'Summary', 'Reporter', 'Type', 'Status', 'Priority', 'Assignee', 'Resolution SLA', 'Created'].join(',')]
      .concat(this.rows().map(r => [r.key, r.summary, r.reporter?.name, r.type, r.status, r.priority, r.assignee?.name, slaText(r.resolution), r.createdAt].map(esc).join(',')));
    const a = document.createElement('a');
    a.href = URL.createObjectURL(new Blob([lines.join('\n')], { type: 'text/csv' }));
    a.download = `${this.ctx.key()}-${this.queue() ?? 'all'}.csv`;
    a.click();
    URL.revokeObjectURL(a.href);
  }
}

// ---- request (agent view) ------------------------------------------------------

@Component({
  selector: 'nx-desk-request',
  imports: [FormsModule, RouterLink, Avatar, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './request-page.scss',
  templateUrl: './request-page.html',
})
export class DeskRequest {
  private deskApi = inject(DeskApi);
  private hub = inject(Hub);
  private toasts = inject(Toasts);
  readonly ctx = inject(DeskContext);
  readonly people = inject(People);
  readonly auth = inject(Auth);
  readonly req = input.required<string>();
  readonly r = signal<RequestView | null>(null);
  readonly tab = signal<'all' | 'replies' | 'notes'>('all');
  readonly composer = signal<'reply' | 'note' | null>(null);
  readonly dismissed = signal(false);
  readonly linking = signal(false);
  readonly statuses = REQUEST_STATUSES; readonly priorities = PRIORITIES;
  readonly comments = computed(() => {
    const cs = this.r()?.comments ?? [];
    return this.tab() === 'replies' ? cs.filter(c => !c.internal) : this.tab() === 'notes' ? cs.filter(c => c.internal) : cs;
  });
  label = label; ago = ago; dateTime = dateTime; duration = duration; slaText = slaText; slaTone = slaTone;
  body = ''; linked = '';

  constructor() { this.people.load(); effect(() => { this.req(); this.load(); }); }

  async load() {
    const r = await this.deskApi.request(this.req());
    this.r.set(r);
    this.dismissed.set(false);
    this.hub.viewed({ app: 'desk', kind: 'request', key: r.key, title: r.summary, subtitle: `Desk · ${r.deskName}`, link: `/desk/${r.deskKey}/${r.key}` });
  }
  async edit(b: Record<string, unknown>) {
    try { this.r.set(await this.deskApi.edit(this.r()!.key, b)); this.ctx.touch(); }
    catch (e) { this.toasts.error((e as ApiError).message); }
  }
  async send() {
    const internal = this.composer() === 'note';
    try {
      this.r.set(await this.deskApi.comment(this.r()!.key, this.body, internal));
      this.body = ''; this.composer.set(null); this.ctx.touch();
      this.toasts.info(internal ? 'Internal note added' : 'Reply sent to customer');
    } catch (e) { this.toasts.error((e as ApiError).message); }
  }
  insert(text?: string) { if (!text) return; this.composer.set('reply'); this.body = (this.body ? this.body + '\n\n' : '') + text; }
  async saveLink() { await this.edit({ linked: this.linked }); this.linking.set(false); }
  slaPct(s: { state: string; minutes: number }, total: number) {
    if (s.state !== 'RUNNING') return s.state === 'BREACHED' || s.state === 'MISSED' ? 100 : s.state === 'MET' ? 100 : 0;
    return Math.max(0, Math.min(100, 100 - (s.minutes / total) * 100));
  }
  policy() { return this.ctx.view()?.policies.find(p => p.priority === this.r()?.priority); }
}

// ---- customers & SLAs ----------------------------------------------------------

@Component({
  selector: 'nx-desk-customers',
  imports: [RouterLink, Avatar, Empty],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="page">
    <div class="crumbs"><a routerLink="/desk">Service projects</a><span class="sep">/</span><span>{{ ctx.view()?.desk?.name }}</span></div>
    <div class="title-bar"><div><h1>Customers</h1><div class="sub">Everyone who has raised a request in this service project.</div></div></div>
    <div class="card flush"><div class="table-wrap"><table class="table">
      <thead><tr><th>Customer</th><th>Organisation</th><th>Open</th><th>Total</th><th>CSAT</th></tr></thead>
      <tbody>@for (c of rows(); track c.user.id) {
        <tr><td><div class="row nowrap"><nx-avatar [user]="c.user" [size]="24" /><div><div class="strong">{{ c.user.name }}</div><div class="tiny subtle">{{ c.jobTitle ?? c.user.email }}</div></div></div></td>
          <td class="muted">{{ c.organization ?? '—' }}</td><td><b>{{ c.open }}</b></td><td>{{ c.total }}</td><td>{{ c.csat != null ? c.csat.toFixed(1) : '—' }}</td></tr>
      } @empty { <tr><td colspan="5"><nx-empty icon="users" title="No customers yet" /></td></tr> }</tbody></table></div></div>
  </div>`,
})
export class DeskCustomers {
  readonly ctx = inject(DeskContext);
  readonly rows = signal<CustomerView[]>([]);
  constructor() {
    const api = inject(DeskApi);
    effect(() => { const k = this.ctx.key(); if (k) api.customers(k).then(r => this.rows.set(r)); });
  }
}

@Component({
  selector: 'nx-desk-slas',
  imports: [RouterLink, Icon, Status],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="page narrow">
    <div class="crumbs"><a routerLink="/desk">Service projects</a><span class="sep">/</span><span>{{ ctx.view()?.desk?.name }}</span></div>
    <div class="title-bar"><div><h1>SLAs</h1><div class="sub">Targets by priority. The resolution clock pauses while a request is waiting for the customer.</div></div></div>
    <div class="card flush"><div class="table-wrap"><table class="table">
      <thead><tr><th>Priority</th><th></th><th>Time to first response</th><th>Time to resolution</th></tr></thead>
      <tbody>@for (p of ctx.view()?.policies ?? []; track p.priority) {
        <tr><td><nx-status [value]="p.priority" /></td><td class="strong">{{ p.label }}</td><td>{{ duration(p.firstResponseMinutes) }}</td><td>{{ duration(p.resolutionMinutes) }}</td></tr>
      }</tbody></table></div></div>
    <div class="banner"><nx-icon name="info" /><div>Breaching soon means less than 30 minutes left on the resolution clock. A reply to the customer stops the first-response clock; resolving or closing the request stops the resolution clock.</div></div>
  </div>`,
})
export class DeskSlas {
  readonly ctx = inject(DeskContext);
  duration = duration;
}
