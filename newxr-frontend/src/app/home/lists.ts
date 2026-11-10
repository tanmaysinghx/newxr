import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Api } from '../core/api';
import { Auth } from '../core/auth';
import { ago, label } from '../core/format';
import { RecentItem, StarItem, WorkItem } from '../core/hub';
import { APPS, UserRef } from '../core/models';
import { AppTile, Avatar, Empty, Icon, Status } from '../shared/ui';

@Component({
  selector: 'nx-recent',
  imports: [RouterLink, AppTile, Empty],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="page narrow"><div class="title-bar"><h1>Recent</h1></div>
    <div class="card flush">@for (r of items(); track r.link) {
      <a class="list-row" [routerLink]="r.link"><nx-app-tile [app]="r.app" [size]="22" />
        <div class="grow truncate"><div class="truncate">{{ r.title }}</div><div class="tiny subtle">{{ r.subtitle }}</div></div>
        <span class="small subtle nowrap">{{ ago(r.viewedAt) }}</span></a>
    } @empty { <nx-empty icon="clock" title="Nothing viewed yet" /> }</div></div>`,
})
export class Recent {
  private api = inject(Api);
  readonly items = signal<RecentItem[]>([]);
  ago = ago;
  constructor() { this.api.quiet(this.api.get<RecentItem[]>('/api/hub/recent', { limit: 100 })).then(r => r && this.items.set(r)); }
}

@Component({
  selector: 'nx-starred',
  imports: [RouterLink, AppTile, Empty, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="page narrow"><div class="title-bar"><h1>Starred</h1></div>
    <div class="card flush">@for (s of items(); track s.id) {
      <a class="list-row" [routerLink]="s.link"><nx-app-tile [app]="s.app" [size]="22" /><span class="grow truncate">{{ s.title }}</span>
        <span class="chip">{{ label(s.kind) }}</span><nx-icon name="star" [size]="15" style="color:#e8a317" /></a>
    } @empty { <nx-empty icon="star" title="No stars yet" text="Use the star on a project, space, board or page to keep it here." /> }</div></div>`,
})
export class Starred {
  private api = inject(Api);
  readonly items = signal<StarItem[]>([]);
  label = label;
  constructor() { this.api.quiet(this.api.get<StarItem[]>('/api/hub/stars')).then(r => r && this.items.set(r)); }
}

@Component({
  selector: 'nx-apps',
  imports: [RouterLink, AppTile],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="page"><div class="title-bar"><div><h1>Apps</h1><div class="sub">Everything your organization has switched on.</div></div></div>
    <div class="grid grid-3">@for (a of apps(); track a.code) {
      <a class="card row gap-12" [routerLink]="a.home" style="color:var(--text);text-decoration:none"><nx-app-tile [app]="a.code" [size]="40" />
        <div><div class="strong">{{ a.name }}</div><div class="small muted">{{ a.tagline }}</div></div></a>
    }</div></div>`,
})
export class Apps {
  private auth = inject(Auth);
  readonly apps = computed(() => APPS.filter(a => this.auth.me()?.apps.includes(a.code)));
}

interface Person extends UserRef { jobTitle?: string; role: string; lastActiveAt?: string; }

@Component({
  selector: 'nx-people',
  imports: [FormsModule, Avatar, Icon, Status],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="page"><div class="title-bar"><h1>People</h1></div>
    <div class="search-input" style="max-width:320px"><nx-icon name="search" /><input class="input" placeholder="Search people" [(ngModel)]="q" (ngModelChange)="load()" /></div>
    <div class="grid grid-4">@for (p of people(); track p.id) {
      <div class="card col" style="align-items:center;text-align:center"><nx-avatar [user]="p" [size]="56" />
        <div class="strong">{{ p.name }}</div><div class="small muted">{{ p.jobTitle || ' ' }}</div><div class="tiny subtle truncate" style="max-width:100%">{{ p.email }}</div>
        @if (p.role !== 'MEMBER') { <nx-status [value]="p.role" /> }</div>
    }</div></div>`,
})
export class People {
  private api = inject(Api);
  readonly people = signal<Person[]>([]);
  q = '';
  constructor() { this.load(); }
  async load() { const r = await this.api.quiet(this.api.get<Person[]>('/api/people', { q: this.q })); if (r) this.people.set(r); }
}

interface SearchResult { query: string; total: number; counts: Record<string, number>; items: WorkItem[]; }

@Component({
  selector: 'nx-search',
  imports: [FormsModule, RouterLink, AppTile, Icon, Status, Empty],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [`.hit { display:block; padding:12px 16px; border:1px solid var(--border); border-radius:10px; background:var(--surface); color:var(--text); text-decoration:none !important; }
    .hit:hover { border-color: var(--border-strong); box-shadow: var(--shadow-1); } .hit .t { color: var(--link); font-weight: 500; }
    .big { height: 42px; font-size: 15px; border-color: var(--accent); }`],
  template: `<div class="page narrow">
    <form class="search-input" (submit)="go($event)"><nx-icon name="search" [size]="18" /><input class="input big" name="q" [(ngModel)]="q" placeholder="Search everything" autofocus />
      @if (res()) { <span class="small subtle" style="position:absolute;right:12px">{{ res()!.total }} results</span> }</form>
    <div class="tabs">
      <button [class.active]="!app()" (click)="setApp('')">All <span class="count">{{ res()?.total ?? 0 }}</span></button>
      @for (c of countList(); track c.app) { <button [class.active]="app() === c.app" (click)="setApp(c.app)">{{ c.label }} <span class="count">{{ c.n }}</span></button> }
    </div>
    <div class="col gap-12">
      @for (w of visible(); track w.link) {
        <a class="hit" [routerLink]="w.link"><div class="row"><nx-app-tile [app]="w.app === 'people' ? 'goals' : w.app" [size]="18" />
          <span class="t truncate">{{ w.key && w.app !== 'people' ? w.key + ' · ' : '' }}{{ w.title }}</span>
          @if (w.status) { <nx-status [value]="w.status" /> }</div>
          @if (w.snippet) { <div class="small muted mt-8 truncate">{{ w.snippet }}</div> }
          <div class="tiny subtle mt-8">{{ w.subtitle }}</div></a>
      } @empty { @if (res()) { <nx-empty icon="search" title="No results" [text]="'Nothing matches “' + res()!.query + '”.'" /> } }
    </div></div>`,
})
export class Search {
  private api = inject(Api);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  readonly res = signal<SearchResult | null>(null);
  readonly app = signal('');
  q = '';
  readonly visible = computed(() => (this.res()?.items ?? []).filter(i => !this.app() || i.app === this.app()));
  readonly countList = computed(() => Object.entries(this.res()?.counts ?? {}).map(([app, n]) => ({
    app, n, label: app === 'people' ? 'People' : APPS.find(a => a.code === app)?.short ?? app })));

  constructor() {
    this.route.queryParamMap.subscribe(p => { this.q = p.get('q') ?? ''; if (this.q) this.run(); });
  }
  go(e: Event) { e.preventDefault(); this.router.navigate([], { queryParams: { q: this.q } }); }
  setApp(a: string) { this.app.set(a); }
  async run() { const r = await this.api.quiet(this.api.get<SearchResult>('/api/hub/search', { q: this.q })); if (r) this.res.set(r); }
}
