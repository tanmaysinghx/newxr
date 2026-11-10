import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { ago } from '../core/format';
import { Hub } from '../core/hub';
import { Avatar, Avatars, Icon } from '../shared/ui';
import { Overview, PagesApi } from './pages-api';

@Component({
  selector: 'nx-space-overview',
  imports: [RouterLink, Avatar, Avatars, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [`.hero{padding:30px 32px 26px;color:#fff} .hero .eyebrow{font:700 11px var(--font);letter-spacing:.08em;opacity:.85} .hero h1{font-size:28px;margin:6px 0 4px;color:#fff}
    .hero p{opacity:.9;margin:0 0 10px} .pin{display:flex;flex-direction:column;gap:6px;color:var(--text);text-decoration:none!important}
    .pin-ic{width:30px;height:30px;border-radius:50%;background:var(--surface-sunken);display:flex;align-items:center;justify-content:center;color:var(--pages);margin-bottom:6px}
    .cols{display:grid;grid-template-columns:minmax(0,1fr) 300px;gap:16px;align-items:start} @media(max-width:1000px){.cols{grid-template-columns:1fr}}
    dl{display:grid;grid-template-columns:110px 1fr;gap:8px;margin:0;font-size:13px} dt{color:var(--text-muted)} dd{margin:0}`],
  template: `@if (o(); as o) {
    <div class="hero" [style.background]="'linear-gradient(120deg,' + o.space.color + ', color-mix(in srgb,' + o.space.color + ' 55%, #2f6fde))'">
      <div class="eyebrow">SPACE</div><h1>{{ o.space.name }}</h1><p>{{ o.space.description }}</p>
      <div class="row"><nx-avatars [users]="o.contributors" [size]="22" /><span class="small">{{ o.contributors.length }} contributors · {{ o.space.pageCount }} pages</span></div>
    </div>
    <div class="page">
      <div class="row wrap"><button class="btn primary" (click)="create()"><nx-icon name="plus" /> Create</button>
        <a class="btn" [routerLink]="['/pages', o.space.key, 'templates']"><nx-icon name="grid" /> Templates</a>
        <button class="btn" (click)="star()"><nx-icon name="star" [style.color]="starred() ? '#e8a317' : null" /> {{ starred() ? 'Starred' : 'Star' }}</button></div>
      @if (o.pinned.length) {
        <h3>Pinned</h3>
        <div class="grid grid-3">@for (p of o.pinned; track p.id) {
          <a class="card pin" [routerLink]="['/pages', o.space.key, p.id]"><span class="pin-ic"><nx-icon name="page" [size]="15" /></span>
            <span class="strong">{{ p.title }}</span><span class="small muted truncate">{{ p.excerpt }}</span></a> }</div>
      }
      <div class="cols">
        <div class="card flush"><div class="card-head" style="padding:14px 16px 4px;margin:0"><h3>Recently updated</h3></div>
          @for (p of o.recent; track p.id) {
            <a class="list-row" [routerLink]="['/pages', o.space.key, p.id]"><nx-icon [name]="p.kind === 'BLOG' ? 'megaphone' : 'page'" [size]="16" style="color:var(--pages)" />
              <div class="grow truncate"><div class="truncate">{{ p.title }}</div><div class="tiny subtle">{{ p.lastEditor?.name }} updated {{ ago(p.updatedAt) }}</div></div>
              <nx-avatar [user]="p.lastEditor" [size]="22" /></a> }</div>
        <div class="card"><h3 class="mb-8">About this space</h3>
          <dl><dt>Owner</dt><dd>{{ o.space.owner?.name }}</dd><dt>Key</dt><dd class="mono">{{ o.space.key }}</dd><dt>Visibility</dt><dd>Everyone in the organization</dd>
            <dt>Linked project</dt><dd>@if (o.space.linkedProject) { <a [routerLink]="['/track', o.space.linkedProject, 'board']">{{ o.space.linkedProject }}</a> } @else { — }</dd></dl></div>
      </div>
    </div>
  }`,
})
export class SpaceOverview {
  private pagesApi = inject(PagesApi);
  private hub = inject(Hub);
  private router = inject(Router);
  readonly key = input.required<string>();
  readonly o = signal<Overview | null>(null);
  readonly starred = signal(false);
  ago = ago;
  constructor() {
    effect(() => {
      const k = this.key();
      this.pagesApi.treeChanged();
      this.pagesApi.overview(k).then(o => this.o.set(o));
      this.hub.isStarred(`/pages/${k}`).then(s => this.starred.set(s));
    });
  }
  async create() {
    const p = await this.pagesApi.create({ spaceKey: this.key(), title: 'Untitled', body: '' });
    this.pagesApi.touch();
    this.router.navigate(['/pages', this.key(), p.id, 'edit']);
  }
  async star() { this.starred.set(await this.hub.toggleStar({ app: 'pages', kind: 'space', title: this.o()!.space.name, link: `/pages/${this.key()}` })); }
}
