import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Api } from '../core/api';
import { ago } from '../core/format';
import { Toasts } from '../core/toasts';
import { Avatar, Empty, Icon } from '../shared/ui';
import { PageSummary, PagesApi, TEMPLATES } from './pages-api';

@Component({
  selector: 'nx-space-blog',
  imports: [RouterLink, Avatar, Empty, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="page narrow"><div class="title-bar"><h1>Blog</h1><div class="actions"><button class="btn primary" (click)="write()"><nx-icon name="edit" /> Write a post</button></div></div>
    @for (b of posts(); track b.id) {
      <a class="card col" [routerLink]="['/pages', key(), b.id]" style="color:var(--text);text-decoration:none"><h3>{{ b.title }}</h3><div class="small muted">{{ b.excerpt }}</div>
        <div class="row small subtle"><nx-avatar [user]="b.lastEditor" [size]="18" />{{ b.lastEditor?.name }} · {{ ago(b.updatedAt) }}</div></a>
    } @empty { <div class="card"><nx-empty icon="megaphone" title="No posts yet" text="Share news and updates with the team." /></div> }</div>`,
})
export class SpaceBlog {
  private pagesApi = inject(PagesApi);
  private router = inject(Router);
  readonly key = input.required<string>();
  readonly posts = signal<PageSummary[]>([]);
  ago = ago;
  constructor() { effect(() => this.pagesApi.blog(this.key()).then(p => this.posts.set(p))); }
  async write() { const p = await this.pagesApi.create({ spaceKey: this.key(), title: 'Untitled post', kind: 'BLOG', body: '' }); this.router.navigate(['/pages', this.key(), p.id, 'edit']); }
}

@Component({
  selector: 'nx-space-templates',
  imports: [Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="page"><div class="title-bar"><div><h1>Templates</h1><div class="sub">Start a page with a structure.</div></div></div>
    <div class="grid grid-3">@for (t of templates; track t.name) {
      <button class="card col" style="text-align:left;cursor:pointer;font:inherit;color:var(--text)" (click)="use(t)">
        <nx-icon [name]="t.icon" [size]="20" style="color:var(--pages)" /><div class="strong">{{ t.name }}</div><div class="small muted">{{ t.description }}</div></button> }</div></div>`,
})
export class SpaceTemplates {
  private pagesApi = inject(PagesApi);
  private router = inject(Router);
  readonly key = input.required<string>();
  readonly templates = TEMPLATES;
  async use(t: (typeof TEMPLATES)[number]) {
    const p = await this.pagesApi.create({ spaceKey: this.key(), title: t.title, body: t.body });
    this.pagesApi.touch();
    this.router.navigate(['/pages', this.key(), p.id, 'edit']);
  }
}

@Component({
  selector: 'nx-space-settings',
  imports: [FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="page narrow"><div class="title-bar"><h1>Space settings</h1></div>
    <div class="card col gap-12" style="max-width:600px">
      <div class="field"><label>Name</label><input class="input" [(ngModel)]="name" /></div>
      <div class="field"><label>Description</label><textarea class="input" rows="3" [(ngModel)]="description"></textarea></div>
      <div class="field"><label>Linked Track project</label><input class="input mono" [(ngModel)]="linkedProject" placeholder="ORB" /></div>
      <div class="row"><button class="btn primary" (click)="save()">Save</button><span class="grow"></span><button class="btn" (click)="archive()">{{ archived ? 'Restore space' : 'Archive space' }}</button></div>
    </div></div>`,
})
export class SpaceSettings {
  private pagesApi = inject(PagesApi);
  private api = inject(Api);
  private toasts = inject(Toasts);
  private router = inject(Router);
  readonly key = input.required<string>();
  name = ''; description = ''; linkedProject = ''; archived = false;
  constructor() {
    effect(() => this.pagesApi.overview(this.key()).then(o => {
      this.name = o.space.name; this.description = o.space.description ?? ''; this.linkedProject = o.space.linkedProject ?? ''; this.archived = o.space.archived;
    }));
  }
  async save() { if (await this.api.quiet(this.pagesApi.updateSpace(this.key(), { name: this.name, description: this.description, linkedProject: this.linkedProject }))) { this.toasts.info('Space saved'); this.pagesApi.touch(); } }
  async archive() { await this.api.quiet(this.pagesApi.archive(this.key(), !this.archived)); this.router.navigateByUrl('/pages'); }
}
