import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink, RouterOutlet } from '@angular/router';
import { Api, ApiError } from '../core/api';
import { ago, label, longDate, shortDate } from '../core/format';
import { Hub } from '../core/hub';
import { People } from '../core/people';
import { Toasts } from '../core/toasts';
import { AppFrame } from '../shell/shell';
import { Sidebar } from '../shell/sidebar';
import { AppTile, Avatar, Avatars, Empty, Icon, Modal, Status } from '../shared/ui';
import { GOAL_STATUSES, GoalRow, GoalView, GoalsApi, UpdateView } from './goals-api';

const tone = (s: string) => s === 'ON_TRACK' ? '' : s === 'AT_RISK' ? 'warning' : s === 'OFF_TRACK' ? 'danger' : s === 'COMPLETED' ? 'accent' : '';

@Component({
  selector: 'nx-goals-layout',
  imports: [RouterOutlet, AppFrame, Sidebar],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<nx-app-frame><nx-sidebar [context]="{ title: 'Newxr Goals', subtitle: 'Workspace', app: 'goals' }"
    [items]="[{ label: 'Goals', icon: 'target', link: '/goals', exact: true }, { label: 'Updates', icon: 'megaphone', link: '/goals/updates' }]" />
    <router-outlet /></nx-app-frame>`,
})
export class GoalsLayout {}

/** Create-goal dialog, shared by the list and "add sub-goal". */
@Component({
  selector: 'nx-create-goal',
  imports: [FormsModule, Modal, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<nx-modal title="Create goal" (closed)="closed.emit(null)">
    <div class="modal-body">
      @if (error()) { <div class="banner danger"><nx-icon name="alert" /><div>{{ error() }}</div></div> }
      <div class="field"><label>Goal <span class="req">*</span></label><input class="input" [(ngModel)]="f.name" placeholder="Cut crash rate below 0.2%" autofocus /></div>
      <div class="form-grid">
        <div class="field"><label>Owner</label><select class="input" [(ngModel)]="f.ownerId"><option [ngValue]="null">Me</option>@for (u of people.all(); track u.id) { <option [ngValue]="u.id">{{ u.name }}</option> }</select></div>
        <div class="field"><label>Team</label><input class="input" [(ngModel)]="f.team" placeholder="Mobile platform" /></div>
        <div class="field"><label>Parent goal</label><select class="input" [(ngModel)]="f.parentId"><option [ngValue]="null">None</option>@for (g of flat(); track g.id) { <option [ngValue]="g.id">{{ g.name }}</option> }</select></div>
        <div class="field"><label>Target date</label><input class="input" type="date" [(ngModel)]="f.targetDate" /></div>
        <div class="field full"><label>Metric (optional)</label><input class="input" [(ngModel)]="f.metricName" placeholder="Crash rate (7-day)" /></div>
        <div class="field"><label>Start value</label><input class="input" type="number" [(ngModel)]="f.metricStart" /></div>
        <div class="field"><label>Target value</label><input class="input" type="number" [(ngModel)]="f.metricTarget" /></div>
        <div class="field"><label>Unit</label><input class="input" [(ngModel)]="f.metricUnit" placeholder="%" /></div>
      </div>
    </div>
    <div class="modal-foot"><button class="btn ghost" (click)="closed.emit(null)">Cancel</button><button class="btn primary" (click)="create()">Create</button></div>
  </nx-modal>`,
})
export class CreateGoal {
  private goalsApi = inject(GoalsApi);
  readonly people = inject(People);
  readonly parentId = input<number | null>(null);
  readonly closed = output<GoalView | null>();
  readonly flat = signal<GoalRow[]>([]);
  readonly error = signal<string | null>(null);
  f = { name: '', ownerId: null as number | null, team: '', parentId: null as number | null, targetDate: '', metricName: '', metricStart: null as number | null, metricTarget: null as number | null, metricUnit: '' };
  constructor() {
    this.people.load();
    this.goalsApi.tree().then(t => { const out: GoalRow[] = []; const walk = (ns: GoalRow[]) => ns.forEach(n => { out.push(n); walk(n.children); }); walk(t); this.flat.set(out); });
    queueMicrotask(() => this.f.parentId = this.parentId());
  }
  async create() {
    try { this.closed.emit(await this.goalsApi.create({ ...this.f, targetDate: this.f.targetDate || null })); }
    catch (e) { this.error.set((e as ApiError).message); }
  }
}

@Component({
  selector: 'nx-goals-list',
  imports: [FormsModule, RouterLink, Avatar, Icon, Status, CreateGoal],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [`.gt{display:grid;grid-template-columns:minmax(0,1fr) 120px 170px 170px 80px;align-items:center;gap:12px;padding:9px 14px;border-bottom:1px solid var(--border);font-size:13px}
    .gt.head{font:600 12px var(--font);color:var(--text-muted)} .gt a{color:var(--text)} .tw{border:0;background:none;cursor:pointer;color:var(--text-subtle);display:inline-flex;padding:0}
    .gi{color:var(--goals);flex:none} @media(max-width:900px){.gt{grid-template-columns:minmax(0,1fr) 110px}.gt>:nth-child(n+3){display:none}}`],
  template: `<div class="page">
    <div class="title-bar"><div><h1>Goals</h1><div class="sub">Company and team goals, with weekly updates.</div></div>
      <div class="actions"><button class="btn primary" (click)="creating.set(true)"><nx-icon name="plus" /> Create goal</button></div></div>
    <div class="row wrap">
      <div class="search-input"><nx-icon name="search" /><input class="input" placeholder="Search goals" [(ngModel)]="q" (ngModelChange)="load()" style="width:200px" /></div>
      <select class="input" style="width:auto" [(ngModel)]="status" (ngModelChange)="load()"><option value="">Status</option>@for (s of statuses; track s) { <option [value]="s">{{ label(s) }}</option> }</select>
      <select class="input" style="width:auto" [(ngModel)]="owner" (ngModelChange)="load()"><option [ngValue]="null">Owner</option>@for (u of people.all(); track u.id) { <option [ngValue]="u.id">{{ u.name }}</option> }</select>
    </div>
    <div class="card flush">
      <div class="gt head"><span>Goal</span><span>Status</span><span>Progress</span><span>Owner</span><span>Target</span></div>
      @for (r of rows(); track r.g.id) {
        <div class="gt">
          <div class="row" [style.padding-left.px]="r.depth * 22">
            @if (r.g.children.length) { <button class="tw" (click)="toggle(r.g.id)"><nx-icon [name]="closedSet().has(r.g.id) ? 'chevron-right' : 'chevron-down'" [size]="14" /></button> } @else { <span style="width:14px"></span> }
            <nx-icon name="target" [size]="15" class="gi" /><a [routerLink]="['/goals', r.g.id]" [class.strong]="r.depth === 0" class="truncate">{{ r.g.name }}</a></div>
          <nx-status [value]="r.g.status" />
          <div class="row"><div class="progress" [class]="'progress ' + tone(r.g.status)" style="width:100px"><span [style.width.%]="r.g.progress"></span></div><span class="small muted">{{ r.g.progress }}%</span></div>
          <div class="row"><nx-avatar [user]="r.g.owner" [size]="20" /><span class="truncate">{{ r.g.owner?.name }}</span></div>
          <span class="muted">{{ shortDate(r.g.targetDate) }}</span>
        </div>
      } @empty { <div class="empty">No goals yet.</div> }
    </div>
  </div>
  @if (creating()) { <nx-create-goal (closed)="created($event)" /> }`,
})
export class GoalsList {
  private goalsApi = inject(GoalsApi);
  private router = inject(Router);
  readonly people = inject(People);
  readonly tree = signal<GoalRow[]>([]);
  readonly closedSet = signal(new Set<number>());
  readonly creating = signal(false);
  readonly statuses = GOAL_STATUSES;
  label = label; shortDate = shortDate; tone = tone;
  q = ''; status = ''; owner: number | null = null;
  readonly rows = computed(() => {
    const out: { g: GoalRow; depth: number }[] = [];
    const walk = (ns: GoalRow[], d: number) => ns.forEach(g => { out.push({ g, depth: d }); if (!this.closedSet().has(g.id)) walk(g.children, d + 1); });
    walk(this.tree(), 0);
    return out;
  });
  constructor() {
    this.people.load();
    this.load();
    inject(ActivatedRoute).queryParamMap.subscribe(q => { if (q.get('create')) this.creating.set(true); });
  }
  async load() { this.tree.set(await this.goalsApi.tree({ q: this.q, status: this.status, owner: this.owner })); }
  toggle(id: number) { const s = new Set(this.closedSet()); s.has(id) ? s.delete(id) : s.add(id); this.closedSet.set(s); }
  created(g: GoalView | null) { this.creating.set(false); if (g) this.router.navigate(['/goals', g.id]); }
}

@Component({
  selector: 'nx-goal-page',
  imports: [FormsModule, RouterLink, Avatar, Avatars, AppTile, Icon, Status, Modal],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './goal-page.scss',
  templateUrl: './goal-page.html',
})
export class GoalPage {
  private goalsApi = inject(GoalsApi);
  private api = inject(Api);
  private hub = inject(Hub);
  private toasts = inject(Toasts);
  readonly people = inject(People);
  readonly id = input.required<string>();
  readonly g = signal<GoalView | null>(null);
  readonly posting = signal(false);
  readonly linking = signal(false);
  readonly hover = signal<number | null>(null);
  readonly statuses = GOAL_STATUSES;
  label = label; ago = ago; longDate = longDate; shortDate = shortDate; tone = tone;
  up = { status: 'ON_TRACK', progress: null as number | null, metricValue: null as number | null, body: '' };
  link = { app: 'track', title: '', link: '' };
  readonly W = 900; readonly H = 260; readonly P = { l: 40, r: 12, t: 12, b: 24 };

  readonly chart = computed(() => {
    const s = this.g()?.series ?? [];
    if (s.length < 2) return null;
    const vals = s.flatMap(p => [p.actual, p.plan]).filter((v): v is number => v != null);
    const lo = Math.min(...vals), hi = Math.max(...vals);
    const pad = (hi - lo) * 0.15 || 1;
    const y0 = lo - pad, y1 = hi + pad;
    const t0 = new Date(s[0].at).getTime(), t1 = new Date(s.at(-1)!.at).getTime();
    const x = (at: string) => this.P.l + (this.W - this.P.l - this.P.r) * (new Date(at).getTime() - t0) / Math.max(1, t1 - t0);
    const y = (v: number) => this.P.t + (this.H - this.P.t - this.P.b) * (1 - (v - y0) / (y1 - y0));
    const actual = s.filter(p => p.actual != null);
    const plan = s.filter(p => p.plan != null);
    const fmt = (v: number) => (Math.round(v * 100) / 100).toString();
    return {
      x, y, actual, ticks: [y0 + pad, (y0 + y1) / 2, y1 - pad].map(v => ({ v: fmt(v), y: y(v) })),
      actualPath: actual.map((p, i) => `${i ? 'L' : 'M'}${x(p.at).toFixed(1)},${y(p.actual!).toFixed(1)}`).join(' '),
      planPath: plan.map((p, i) => `${i ? 'L' : 'M'}${x(p.at).toFixed(1)},${y(p.plan!).toFixed(1)}`).join(' '),
      xl: [{ x: x(s[0].at), t: shortDate(s[0].at) }, { x: x(s.at(-1)!.at), t: shortDate(s.at(-1)!.at) }],
      last: actual.at(-1),
    };
  });

  constructor() { this.people.load(); effect(() => { this.id(); this.load(); }); }

  async load() {
    const g = await this.goalsApi.goal(+this.id());
    this.g.set(g);
    this.up.status = g.status === 'PENDING' ? 'ON_TRACK' : g.status;
    this.hub.viewed({ app: 'goals', kind: 'goal', title: g.name, subtitle: 'Goals', link: `/goals/${g.id}` });
  }
  move(e: PointerEvent, el: Element) {
    const c = this.chart(); if (!c) return;
    const r = el.getBoundingClientRect();
    const px = (e.clientX - r.left) * this.W / r.width;
    let best = 0, d = Infinity;
    c.actual.forEach((p, i) => { const dd = Math.abs(c.x(p.at) - px); if (dd < d) { d = dd; best = i; } });
    this.hover.set(best);
  }
  async post() {
    try { this.g.set(await this.goalsApi.postUpdate(this.g()!.id, this.up)); this.posting.set(false); this.up.body = ''; this.toasts.info('Update posted'); }
    catch (e) { this.toasts.error((e as ApiError).message); }
  }
  async cheer(u: UpdateView) { const r = await this.api.quiet(this.goalsApi.cheer(u.id)); if (r) this.g.update(g => g && { ...g, updates: g.updates.map(x => x.id === u.id ? { ...x, cheers: r.cheers } : x) }); }
  async follow() { const g = await this.api.quiet(this.goalsApi.follow(this.g()!.id)); if (g) this.g.set(g); }
  async addLink() { const g = await this.api.quiet(this.goalsApi.addLink(this.g()!.id, this.link)); if (g) { this.g.set(g); this.linking.set(false); this.link = { app: 'track', title: '', link: '' }; } }
  async removeLink(id: number) { await this.api.quiet(this.goalsApi.removeLink(id)); this.load(); }
  async setOwner(id: number) { const g = await this.api.quiet(this.goalsApi.update(this.g()!.id, { ownerId: id })); if (g) this.g.set(g); }
}

@Component({
  selector: 'nx-goal-updates',
  imports: [RouterLink, Avatar, Status, Empty],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="page narrow"><div class="title-bar"><div><h1>Updates</h1><div class="sub">The latest weekly updates across every goal.</div></div></div>
    @for (u of feed(); track u.id) {
      <div class="card col"><div class="row"><nx-avatar [user]="u.author" [size]="28" /><div class="grow"><div class="strong small">{{ u.author?.name }}</div>
        <a class="small" [routerLink]="['/goals', u.goalId]">{{ u.goalName }}</a> <span class="tiny subtle">· {{ ago(u.createdAt) }}</span></div><nx-status [value]="u.status" /></div>
        <div style="font-size:13.5px">{{ u.body }}</div></div>
    } @empty { <div class="card"><nx-empty icon="megaphone" title="No updates yet" /></div> }</div>`,
})
export class GoalUpdates {
  readonly feed = signal<UpdateView[]>([]);
  ago = ago;
  constructor() { inject(GoalsApi).feed().then(f => this.feed.set(f)); }
}
