import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Api } from '../core/api';
import { Auth } from '../core/auth';
import { ago, greeting, longDate } from '../core/format';
import { RecentItem, StarItem, WorkItem } from '../core/hub';
import { APPS } from '../core/models';
import { AppTile, Empty, Icon } from '../shared/ui';
import { WorkRow } from './work-row';

interface HomeData { assigned: WorkItem[]; waiting: WorkItem[]; recent: RecentItem[]; starred: StarItem[]; }

@Component({
  selector: 'nx-home',
  imports: [RouterLink, AppTile, Icon, Empty, WorkRow],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './home.scss',
  template: `
  <div class="page">
    <div class="title-bar">
      <div><h1>{{ hello }}, {{ firstName() }}</h1>
        <div class="sub">{{ today }} · You have {{ openCount() }} open item{{ openCount() === 1 ? '' : 's' }}@if (data()?.waiting?.length) { and {{ data()!.waiting.length }} waiting on you }</div></div>
    </div>

    <section>
      <h3 class="section-title">Your apps</h3>
      <div class="apps">
        @for (a of myApps(); track a.code) {
          <a class="card app" [routerLink]="a.home"><nx-app-tile [app]="a.code" [size]="28" /><div class="strong">{{ a.short }}</div><div class="tiny muted">{{ a.tagline }}</div></a>
        }
      </div>
    </section>

    <div class="cols">
      <div class="col gap-16 main-col">
        <div class="card flush">
          <div class="card-head pad"><h3>Assigned to me</h3>
            <div class="seg">@for (t of tabs; track t) { <button [class.on]="tab() === t" (click)="tab.set(t)">{{ t }}</button> }</div></div>
          @for (w of assignedView(); track w.link) { <nx-work-row [item]="w" /> }
          @empty { <nx-empty icon="check-circle" [title]="tab() === 'Done' ? 'Nothing finished yet' : 'Nothing assigned to you'" text="Work assigned to you in any app shows up here." /> }
        </div>
        <div class="card flush">
          <div class="card-head pad"><h3>Recently viewed</h3><a routerLink="/recent" class="small">View all</a></div>
          @for (r of data()?.recent ?? []; track r.link) {
            <a class="list-row" [routerLink]="r.link"><nx-app-tile [app]="r.app" [size]="20" />
              <div class="grow truncate"><div class="truncate">{{ r.title }}</div><div class="tiny subtle truncate">{{ r.subtitle }}</div></div>
              <span class="small subtle nowrap">{{ ago(r.viewedAt) }}</span></a>
          } @empty { <nx-empty icon="clock" title="Nothing viewed yet" text="Issues, pages and requests you open appear here." /> }
        </div>
      </div>
      <div class="col gap-16 side-col">
        <div class="card flush">
          <div class="card-head pad"><h3>Waiting on you</h3><span class="count">{{ data()?.waiting?.length ?? 0 }}</span></div>
          <div class="waiting">
          @for (w of data()?.waiting ?? []; track w.link) {
            <a class="wait" [routerLink]="w.link"><nx-app-tile [app]="w.app" [size]="18" />
              <div class="truncate"><div class="truncate small">{{ w.title }}</div><div class="tiny subtle truncate">{{ w.subtitle }}</div></div></a>
          } @empty { <div class="small muted pad-b">No reviews or approvals waiting.</div> }
          </div>
        </div>
        <div class="card flush">
          <div class="card-head pad"><h3>Starred</h3></div>
          @for (s of data()?.starred ?? []; track s.id) {
            <a class="list-row small" [routerLink]="s.link"><nx-app-tile [app]="s.app" [size]="18" /><span class="grow truncate">{{ s.title }}</span><nx-icon name="star" [size]="14" class="star" /></a>
          } @empty { <div class="small muted pad-b">Star projects, spaces and boards to keep them here.</div> }
        </div>
      </div>
    </div>
  </div>`,
})
export class Home {
  private api = inject(Api);
  private auth = inject(Auth);
  readonly data = signal<HomeData | null>(null);
  readonly tab = signal<'Open' | 'Due soon' | 'Done'>('Open');
  readonly tabs = ['Open', 'Due soon', 'Done'] as const;
  readonly hello = greeting();
  readonly today = longDate(new Date().toISOString());
  readonly firstName = computed(() => this.auth.me()?.name.split(' ')[0] ?? '');
  readonly myApps = computed(() => APPS.filter(a => this.auth.me()?.apps.includes(a.code)));
  readonly openCount = computed(() => (this.data()?.assigned ?? []).filter(w => !isDone(w.status)).length);
  readonly assignedView = computed(() => {
    const all = this.data()?.assigned ?? [];
    const soon = Date.now() + 7 * 86400000;
    switch (this.tab()) {
      case 'Done': return all.filter(w => isDone(w.status));
      case 'Due soon': return all.filter(w => !isDone(w.status) && w.due && new Date(w.due).getTime() <= soon);
      default: return all.filter(w => !isDone(w.status));
    }
  });
  ago = ago;

  constructor() {
    this.api.quiet(this.api.get<HomeData>('/api/hub/home')).then(d => d && this.data.set(d));
  }
}

function isDone(s: string) { return ['DONE', 'RESOLVED', 'CLOSED', 'MERGED', 'COMPLETED', 'DECLINED'].includes(s); }
