import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ApiError } from '../core/api';
import { Hub, StarItem } from '../core/hub';
import { Api } from '../core/api';
import { AppFrame } from '../shell/shell';
import { Sidebar } from '../shell/sidebar';
import { Icon, Modal } from '../shared/ui';
import { PagesApi, SpaceView } from './pages-api';

@Component({
  selector: 'nx-spaces',
  imports: [FormsModule, RouterLink, AppFrame, Sidebar, Icon, Modal],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [`.space{padding:0;overflow:hidden;color:var(--text);text-decoration:none!important;display:flex;flex-direction:column;transition:box-shadow .15s}
    .space:hover{box-shadow:var(--shadow-2)} .banner{height:44px} .body{padding:12px 14px;display:flex;flex-direction:column;gap:4px;position:relative}
    .mono-tile{position:absolute;top:-16px;left:14px;width:32px;height:32px;border-radius:8px;background:var(--surface);box-shadow:var(--shadow-1);display:flex;align-items:center;justify-content:center;font:700 12px var(--font)}
    .star{position:absolute;right:10px;bottom:10px;border:0;background:none;cursor:pointer;color:var(--text-subtle)} .star.on{color:#e8a317}`],
  template: `<nx-app-frame>
    <nx-sidebar [context]="{ title: 'Newxr Pages', subtitle: 'Spaces', app: 'pages' }" [items]="[{ label: 'Spaces', icon: 'folder', link: '/pages', exact: true }]" />
    <div class="page">
      <div class="title-bar"><div><h1>Spaces</h1><div class="sub">Every team's knowledge in one place.</div></div>
        <div class="actions"><button class="btn" (click)="toggleArchived()"><nx-icon name="folder" /> {{ archived() ? 'Active spaces' : 'Archived' }}</button>
          <button class="btn primary" (click)="creating.set(true)"><nx-icon name="plus" /> Create space</button></div></div>
      <div class="search-input" style="max-width:260px"><nx-icon name="search" /><input class="input" placeholder="Filter spaces" [ngModel]="q()" (ngModelChange)="q.set($event)" /></div>
      <div class="grid grid-4">
        @for (s of filtered(); track s.key) {
          <a class="card space" [routerLink]="['/pages', s.key]">
            <div class="banner" [style.background]="s.color"></div>
            <div class="body"><span class="mono-tile" [style.color]="s.color">{{ s.key.slice(0, 2) }}</span>
              <div class="strong" style="margin-top:14px">{{ s.name }}</div><div class="small muted truncate">{{ s.description }}</div>
              <div class="tiny subtle">{{ s.pageCount }} pages</div>
              <button class="star" [class.on]="starred().has('/pages/' + s.key)" (click)="$event.preventDefault(); star(s)"><nx-icon name="star" [size]="15" /></button></div>
          </a>
        }
      </div>
    </div>
  </nx-app-frame>
  @if (creating()) {
    <nx-modal title="Create space" (closed)="creating.set(false)">
      <div class="modal-body">
        @if (error()) { <div class="banner danger"><nx-icon name="alert" /><div>{{ error() }}</div></div> }
        <div class="field"><label>Name <span class="req">*</span></label><input class="input" [(ngModel)]="form.name" (ngModelChange)="suggest()" /></div>
        <div class="field"><label>Key <span class="req">*</span></label><input class="input mono" [(ngModel)]="form.key" maxlength="16" /></div>
        <div class="field"><label>Description</label><input class="input" [(ngModel)]="form.description" /></div>
        <div class="field"><label>Colour</label><div class="row">@for (c of colors; track c) {
          <button class="swatch" (click)="form.color = c" [style.background]="c" [style.outline]="form.color === c ? '2px solid var(--text)' : 'none'"
            style="width:26px;height:26px;border-radius:6px;border:0;cursor:pointer;outline-offset:2px" [attr.aria-label]="c"></button> }</div></div>
      </div>
      <div class="modal-foot"><button class="btn ghost" (click)="creating.set(false)">Cancel</button><button class="btn primary" (click)="create()">Create</button></div>
    </nx-modal>
  }`,
})
export class Spaces {
  private pagesApi = inject(PagesApi);
  private hub = inject(Hub);
  private api = inject(Api);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  readonly spaces = signal<SpaceView[]>([]);
  readonly starred = signal(new Set<string>());
  readonly archived = signal(false);
  readonly creating = signal(false);
  readonly error = signal<string | null>(null);
  readonly colors = ['#0e9f8e', '#5a4bf5', '#d9466f', '#2f6fde', '#e8892b', '#2f9a57', '#7c3aed', '#5b6170'];
  readonly q = signal('');
  form = { name: '', key: '', description: '', color: '#0e9f8e' };
  readonly filtered = computed(() => this.spaces().filter(s => this.visible(s)));

  constructor() {
    this.load();
    this.api.get<StarItem[]>('/api/hub/stars').then(s => this.starred.set(new Set(s.map(x => x.link)))).catch(() => null);
    this.route.queryParamMap.subscribe(q => { if (q.get('create')) this.creating.set(true); });
  }
  async load() { this.spaces.set(await this.pagesApi.spaces(this.archived())); }
  visible(s: SpaceView) { const q = this.q().toLowerCase(); return !q || (s.name + s.key + (s.description ?? '')).toLowerCase().includes(q); }
  toggleArchived() { this.archived.set(!this.archived()); this.load(); }
  suggest() { this.form.key = this.form.name.split(/\s+/).filter(Boolean).map(w => w[0]).join('').toUpperCase().slice(0, 6) || ''; if (this.form.key.length < 2) this.form.key = this.form.name.slice(0, 3).toUpperCase(); }
  async star(s: SpaceView) {
    const on = await this.hub.toggleStar({ app: 'pages', kind: 'space', title: s.name, link: `/pages/${s.key}` });
    const set = new Set(this.starred()); on ? set.add(`/pages/${s.key}`) : set.delete(`/pages/${s.key}`); this.starred.set(set);
  }
  async create() {
    this.error.set(null);
    try { const s = await this.pagesApi.createSpace(this.form); this.creating.set(false); this.router.navigate(['/pages', s.key]); }
    catch (e) { this.error.set((e as ApiError).message); }
  }
}
