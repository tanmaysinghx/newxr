import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiError } from '../core/api';
import { ago, dateTime, label, longDate } from '../core/format';
import { Toasts } from '../core/toasts';
import { Empty, Icon, Logo, Modal, Status } from '../shared/ui';
import { COMPONENT_STATUSES, ComponentView, Created, Day, IntegrationView, PageView, PulseApi, statusColor } from './pulse-api';
import { PulseContext } from './pulse-pages';

/** 90 day bars for one component, shared by the admin and the public page. */
@Component({
  selector: 'nx-uptime-bars',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [`.bars{display:flex;gap:2px;height:30px} .bars span{flex:1;border-radius:2px;min-width:2px}
    .ends{display:flex;justify-content:space-between;font-size:11px;color:var(--text-subtle);margin-top:4px}`],
  template: `<div class="bars" role="img" [attr.aria-label]="c().name + ': ' + c().uptime + '% uptime over 90 days'">
      @for (d of c().days; track d.date) { <span [style.background]="color(d.status)" [title]="tip(d)"></span> }</div>
    <div class="ends"><span>90 days ago</span><span>Today</span></div>`,
})
export class UptimeBars {
  readonly c = input.required<ComponentView>();
  color = statusColor;
  tip(d: Day) {
    const date = new Date(d.date + 'T00:00:00Z').toLocaleDateString(undefined, { day: 'numeric', month: 'short', timeZone: 'UTC' });
    return d.incidents.length ? `${date}: ${d.incidents.join(', ')}${d.downtimeMinutes ? ` (${d.downtimeMinutes} min downtime)` : ''}` : `${date}: no incidents`;
  }
}

// ---- status page admin ---------------------------------------------------------------

@Component({
  selector: 'nx-pulse-status',
  imports: [FormsModule, RouterLink, Icon, Status, UptimeBars],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [`.comp{padding:14px 16px;border-bottom:1px solid var(--border)} .comp:last-child{border-bottom:0}
    .settings{display:grid;grid-template-columns:1fr 1fr auto;gap:12px;align-items:end} @media(max-width:800px){.settings{grid-template-columns:1fr}}`],
  template: `<div class="page narrow">
    <div class="crumbs"><a routerLink="/pulse">Operations</a><span class="sep">/</span><span>{{ ctx.team()?.name }}</span></div>
    <div class="title-bar"><div><h1>Status page</h1><div class="sub">Components, their status and 90-day history from public incidents.</div></div>
      @if (p()?.published && p()?.slug) { <div class="actions"><a class="btn" [routerLink]="['/status', p()!.slug]" target="_blank"><nx-icon name="globe" /> View public page</a></div> }</div>
    @if (p(); as p) {
      <div class="card"><h3>Page</h3>
        <div class="settings mt-8">
          <div class="field"><label>Title</label><input class="input" [(ngModel)]="title" placeholder="Orbit status" /></div>
          <div class="field"><label>Address</label><div class="row"><span class="small muted nowrap">/status/</span><input class="input" [(ngModel)]="slug" placeholder="orbit" /></div></div>
          <button class="btn" (click)="saveSettings()">Save</button>
        </div>
        <label class="row small mt-8"><input type="checkbox" [ngModel]="p.published" (ngModelChange)="publish($event)" />
          Published — anyone with the link can see component status and public incident updates, without signing in.</label>
      </div>
      <div class="card flush">
        <div class="card-head" style="padding:14px 16px 0"><h3>Components</h3><nx-status [value]="p.overall" /></div>
        @for (c of p.components; track c.id) {
          <div class="comp">
            <div class="row"><div class="grow"><div class="strong small">{{ c.name }}</div><div class="tiny muted">{{ c.description }}</div></div>
              <span class="small muted">{{ c.uptime.toFixed(2) }}% uptime</span><nx-status [value]="c.status" />
              <select class="input" style="width:auto;height:28px" [ngModel]="c.manualStatus" (ngModelChange)="setManual(c, $event)" [attr.aria-label]="'Manual status for ' + c.name">
                @for (s of statuses; track s) { <option [value]="s">{{ s === 'OPERATIONAL' ? 'No manual status' : label(s) }}</option> }</select>
              <button class="btn sm ghost icon" (click)="remove(c)" [attr.aria-label]="'Remove ' + c.name"><nx-icon name="x" /></button></div>
            <div class="mt-8"><nx-uptime-bars [c]="c" /></div>
          </div>
        }
        <div class="comp row"><input class="input" [(ngModel)]="newName" placeholder="New component, e.g. Partner API" (keydown.enter)="add()" /><button class="btn" (click)="add()"><nx-icon name="plus" /> Add</button></div>
      </div>
      <div class="small muted">A component shows the worst of its manual status and the impact of open incidents affecting it. Major outages count fully as downtime, partial outages 30%.</div>
    }
  </div>`,
})
export class PulseStatus {
  private pulseApi = inject(PulseApi);
  private toasts = inject(Toasts);
  readonly ctx = inject(PulseContext);
  readonly p = signal<PageView | null>(null);
  readonly statuses = COMPONENT_STATUSES;
  label = label;
  title = ''; slug = ''; newName = '';
  constructor() { effect(() => { const k = this.ctx.key(); if (k) this.pulseApi.statusPage(k).then(p => this.set(p)); }); }
  private set(p: PageView) { this.p.set(p); this.title = p.title; this.slug = p.slug ?? ''; }
  private async run(f: () => Promise<PageView>, msg?: string) {
    try { this.set(await f()); if (msg) this.toasts.info(msg); } catch (e) { this.toasts.error((e as ApiError).message); }
  }
  saveSettings() { this.run(() => this.pulseApi.saveStatusPage(this.ctx.key(), { title: this.title, slug: this.slug }), 'Status page saved'); }
  publish(on: boolean) { this.run(() => this.pulseApi.saveStatusPage(this.ctx.key(), { published: on, slug: this.slug || null }), on ? 'Status page published' : 'Status page hidden'); }
  setManual(c: ComponentView, s: string) { this.run(() => this.pulseApi.editComponent(c.id, { manualStatus: s })); }
  add() { if (!this.newName.trim()) return; const n = this.newName; this.newName = ''; this.run(() => this.pulseApi.addComponent(this.ctx.key(), { name: n })); }
  remove(c: ComponentView) { this.run(() => this.pulseApi.removeComponent(c.id), `${c.name} removed`); }
}

// ---- integrations ---------------------------------------------------------------------

@Component({
  selector: 'nx-pulse-integrations',
  imports: [FormsModule, RouterLink, Icon, Empty, Modal],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [`.ii{width:30px;height:30px;border-radius:8px;display:inline-flex;align-items:center;justify-content:center;background:var(--neutral-soft);color:var(--text-muted);flex:none}
    pre{background:var(--surface-hover);border:1px solid var(--border);border-radius:8px;padding:10px 12px;white-space:pre-wrap;word-break:break-all;margin:0}`],
  template: `<div class="page narrow">
    <div class="crumbs"><a routerLink="/pulse">Operations</a><span class="sep">/</span><span>{{ ctx.team()?.name }}</span></div>
    <div class="title-bar"><div><h1>Integrations</h1><div class="sub">Monitors send alerts to a webhook. Repeats with the same dedup key raise the count instead of a new alert.</div></div>
      <div class="actions"><button class="btn primary" (click)="adding.set(true)"><nx-icon name="plus" /> Add integration</button></div></div>
    <div class="card flush"><div class="table-wrap"><table class="table">
      <thead><tr><th>Integration</th><th>Format</th><th>Default priority</th><th>Events</th><th>Last event</th><th>Token</th><th></th></tr></thead>
      <tbody>@for (i of rows(); track i.id) {
        <tr><td><div class="row nowrap"><span class="ii"><nx-icon [name]="i.icon" [size]="15" /></span><span class="strong">{{ i.name }}</span>@if (!i.enabled) { <span class="lozenge">Off</span> }</div></td>
          <td class="muted">{{ i.format === 'PROMETHEUS' ? 'Alertmanager' : 'Generic JSON' }}</td><td>{{ i.defaultPriority }}</td><td>{{ i.eventCount }}</td>
          <td class="muted nowrap">{{ ago(i.lastEventAt) }}</td><td class="mono subtle">…{{ i.tokenHint }}</td>
          <td class="nowrap"><button class="btn sm" (click)="rotate(i)">New URL</button>
            <button class="btn sm ghost" (click)="toggle(i)">{{ i.enabled ? 'Turn off' : 'Turn on' }}</button>
            <button class="btn sm ghost icon" (click)="remove(i)" [attr.aria-label]="'Delete ' + i.name"><nx-icon name="x" /></button></td></tr>
      } @empty { <tr><td colspan="7"><nx-empty icon="link" title="No integrations yet" text="Add one to receive alerts from Prometheus, uptime checks or any tool that can send a webhook." /></td></tr> }</tbody></table></div></div>
  </div>
  @if (adding()) {
    <nx-modal title="Add integration" (closed)="adding.set(false)">
      <div class="modal-body"><div class="form-grid">
        <div class="field full"><label>Name</label><input class="input" [(ngModel)]="f.name" placeholder="Prometheus" autofocus /></div>
        <div class="field"><label>Format</label><select class="input" [(ngModel)]="f.format"><option value="GENERIC">Generic JSON</option><option value="PROMETHEUS">Prometheus Alertmanager</option></select></div>
        <div class="field"><label>Default priority</label><select class="input" [(ngModel)]="f.defaultPriority">@for (p of ['P1','P2','P3','P4','P5']; track p) { <option [value]="p">{{ p }}</option> }</select></div>
      </div></div>
      <div class="modal-foot"><button class="btn ghost" (click)="adding.set(false)">Cancel</button><button class="btn primary" (click)="add()">Create</button></div>
    </nx-modal>
  }
  @if (created(); as c) {
    <nx-modal title="Webhook URL for {{ c.integration.name }}" [wide]="true" (closed)="created.set(null)">
      <div class="modal-body">
        <div class="banner warning"><nx-icon name="lock" /><div>Copy this URL now. It contains a secret and won't be shown again; use “New URL” to replace it.</div></div>
        <pre class="mono">{{ url(c) }}</pre>
        <div class="row"><button class="btn" (click)="copy(url(c))"><nx-icon name="link" /> Copy URL</button></div>
        @if (c.integration.format === 'PROMETHEUS') {
          <div class="small strong">alertmanager.yml</div>
          <pre class="mono">receivers:
  - name: newxr
    webhook_configs:
      - url: {{ url(c) }}
        send_resolved: true</pre>
        } @else {
          <div class="small strong">Try it</div>
          <pre class="mono">curl -X POST {{ url(c) }} \\
  -H 'Content-Type: application/json' \\
  -d '{{ sample }}'</pre>
          <div class="small muted">Fields: <code>title</code> (required), <code>description</code>, <code>priority</code> (P1–P5 or critical/high/warning/info/low), <code>dedupKey</code>, <code>status</code> (<code>resolved</code> closes the open alert with that dedup key).</div>
        }
      </div>
      <div class="modal-foot"><button class="btn primary" (click)="created.set(null)">Done</button></div>
    </nx-modal>
  }`,
})
export class PulseIntegrations {
  private pulseApi = inject(PulseApi);
  private toasts = inject(Toasts);
  readonly ctx = inject(PulseContext);
  readonly rows = signal<IntegrationView[]>([]);
  readonly adding = signal(false);
  readonly created = signal<Created | null>(null);
  readonly sample = '{"title":"Disk 90% on db-1","priority":"warning","dedupKey":"disk-db-1"}';
  ago = ago;
  f = { name: '', format: 'GENERIC', defaultPriority: 'P3' };
  constructor() { effect(() => { if (this.ctx.key()) this.load(); }); }
  async load() { this.rows.set(await this.pulseApi.integrations(this.ctx.key())); }
  url(c: Created) { return location.origin + c.webhookPath; }
  copy(t: string) { navigator.clipboard?.writeText(t).then(() => this.toasts.info('Copied')).catch(() => this.toasts.error('Copy failed — select the URL instead.')); }
  async add() {
    try { this.created.set(await this.pulseApi.addIntegration(this.ctx.key(), this.f)); this.adding.set(false); this.f = { name: '', format: 'GENERIC', defaultPriority: 'P3' }; this.load(); }
    catch (e) { this.toasts.error((e as ApiError).message); }
  }
  async rotate(i: IntegrationView) {
    try { this.created.set(await this.pulseApi.rotate(i.id)); this.load(); } catch (e) { this.toasts.error((e as ApiError).message); }
  }
  async toggle(i: IntegrationView) {
    try { await this.pulseApi.editIntegration(i.id, { enabled: !i.enabled }); this.load(); } catch (e) { this.toasts.error((e as ApiError).message); }
  }
  async remove(i: IntegrationView) {
    try { await this.pulseApi.removeIntegration(i.id); this.toasts.info(`${i.name} deleted`); this.load(); } catch (e) { this.toasts.error((e as ApiError).message); }
  }
}

// ---- public status page -----------------------------------------------------------------

@Component({
  selector: 'nx-public-status',
  imports: [Icon, Logo, Status, UptimeBars],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './public-status.scss',
  template: `<div class="wrap">
    @if (p(); as p) {
      <header><span class="mark">{{ p.title.charAt(0) }}</span><h1 class="grow">{{ p.title }}</h1>
        <a class="btn sm primary" [href]="feed()" title="Atom feed of incident updates"><nx-icon name="bell" [size]="14" /> Subscribe to updates</a></header>
      <div class="overall" [class]="'overall ' + p.overall"><nx-icon [name]="p.overall === 'OPERATIONAL' ? 'check-circle' : 'alert'" [size]="16" />
        <span class="grow">{{ headline() }}</span><span class="tiny">Updated {{ ago(p.updatedAt) }}</span></div>
      @for (i of p.active; track i.key) {
        <section class="incident"><h2>{{ i.title }}</h2>
          @for (u of i.updates; track u.at) { <div class="up"><b>{{ label(u.status) }}</b><div><div>{{ u.body }}</div><div class="tiny muted">{{ time(u.at) }}</div></div></div> }
        </section>
      }
      <section class="comps">
        @for (c of p.components; track c.id) {
          <div class="comp"><div class="row"><span class="strong grow">{{ c.name }}</span><span class="tiny muted">{{ c.uptime.toFixed(2) }}% uptime</span><nx-status [value]="c.status" /></div>
            <nx-uptime-bars [c]="c" /></div>
        }
      </section>
      @if (p.recent.length) {
        <section class="past"><h2>Past incidents (14 days)</h2>
          @for (i of p.recent; track i.key) { <div class="pi"><div class="strong small">{{ i.title }}</div><div class="tiny muted">{{ longDate(i.startedAt) }} · resolved after {{ lasted(i.startedAt, i.resolvedAt!) }}</div>
            @if (i.updates[0]) { <div class="small">{{ i.updates[0].body }}</div> }</div> }
        </section>
      }
      <footer>Powered by <nx-logo [mark]="14" /> Pulse</footer>
    } @else if (missing()) {
      <div class="missing"><h1>Status page not found</h1><p class="muted">This page doesn't exist or isn't published.</p></div>
    }
  </div>`,
})
export class PublicStatus {
  private pulseApi = inject(PulseApi);
  readonly slug = input.required<string>();
  readonly p = signal<PageView | null>(null);
  readonly missing = signal(false);
  readonly headline = computed(() => {
    const p = this.p()!;
    if (p.overall === 'OPERATIONAL') return 'All systems operational';
    const hit = p.components.filter(c => c.status !== 'OPERATIONAL').map(c => c.name).join(', ');
    return `${label(p.overall)} — ${hit}`;
  });
  ago = ago; label = label; longDate = longDate;
  constructor() {
    effect(() => {
      this.pulseApi.publicStatus(this.slug()).then(p => { this.p.set(p); document.title = p.title; }).catch(() => this.missing.set(true));
    });
  }
  feed() { return `/api/public/status/${this.slug()}/feed.atom`; }
  time(iso: string) { return dateTime(iso); }
  lasted(a: string, b: string) {
    const m = Math.round((new Date(b).getTime() - new Date(a).getTime()) / 60000);
    return m < 60 ? `${m} min` : `${Math.floor(m / 60)} h ${m % 60 ? (m % 60) + ' min' : ''}`.trim();
  }
}
