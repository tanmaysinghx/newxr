import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Api } from '../core/api';
import { Auth } from '../core/auth';
import { ago, dateTime } from '../core/format';
import { Hub } from '../core/hub';
import { markdown } from '../core/markdown';
import { Toasts } from '../core/toasts';
import { Avatar, Icon, Modal, Status } from '../shared/ui';
import { PageView, PagesApi, VersionView } from './pages-api';

@Component({
  selector: 'nx-page-view',
  imports: [FormsModule, RouterLink, Avatar, Icon, Modal, Status],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [`.doc{max-width:820px} h1.title{font-size:28px;margin:4px 0 8px} .meta{display:flex;align-items:center;gap:8px;font-size:12px;color:var(--text-muted);flex-wrap:wrap}
    .reactions{display:flex;gap:6px;flex-wrap:wrap;margin-top:24px} .react{display:inline-flex;align-items:center;gap:4px;height:28px;padding:0 10px;border:1px solid var(--border);border-radius:14px;background:var(--surface);cursor:pointer;font:13px var(--font);color:var(--text)}
    .react.mine{border-color:var(--accent);background:var(--accent-soft)} .composer{display:flex;gap:10px} .comment{display:flex;gap:10px;padding:12px 0;border-bottom:1px solid var(--border)}
    .draft-note{margin-bottom:14px}`],
  template: `@if (p(); as p) {
  <div class="page doc">
    <div class="row between">
      <div class="crumbs"><a [routerLink]="['/pages', p.space.key]">{{ p.space.name }}</a>
        @for (c of p.crumbs; track c.id) { <span class="sep">/</span><a [routerLink]="['/pages', p.space.key, c.id]">{{ c.title }}</a> }</div>
      <div class="row gap-4">
        <a class="btn sm" [routerLink]="['/pages', p.space.key, p.id, 'edit']"><nx-icon name="edit" [size]="14" /> Edit</a>
        <button class="btn sm primary" (click)="share()"><nx-icon name="share" [size]="14" /> Share</button>
        <button class="btn ghost sm icon" (click)="star()" aria-label="Star"><nx-icon name="star" [style.color]="starred() ? '#e8a317' : null" /></button>
        <button class="btn ghost sm icon" (click)="openHistory()" aria-label="Page history"><nx-icon name="clock" /></button>
        <button class="btn ghost sm icon" (click)="addChild()" aria-label="Add child page"><nx-icon name="plus" /></button>
        <button class="btn ghost sm icon" (click)="confirmDelete.set(true)" aria-label="Delete page"><nx-icon name="x-circle" /></button>
      </div>
    </div>
    @if (p.hasDraft) { <div class="banner warning draft-note"><nx-icon name="edit" /><div>This page has unpublished changes. <a [routerLink]="['/pages', p.space.key, p.id, 'edit']">Continue editing</a></div></div> }
    <h1 class="title">{{ p.status === 'DRAFT' ? (p.draftTitle ?? p.title) : p.title }}</h1>
    <div class="meta"><nx-avatar [user]="p.author" [size]="20" /><span>Owned by {{ p.author?.name }}</span><span>·</span><span>Last updated {{ ago(p.updatedAt) }}</span>
      <span>·</span><span>{{ p.readMinutes }} min read</span><nx-status [value]="p.status" /><span class="subtle">v{{ p.version }}</span></div>
    <div class="prose mt-16" [innerHTML]="html()"></div>

    <div class="reactions">
      @for (r of p.reactions; track r.emoji) { <button class="react" [class.mine]="r.mine" (click)="react(r.emoji)">{{ r.emoji }} {{ r.count }}</button> }
      @for (e of emojis; track e) { @if (!has(e)) { <button class="react" (click)="react(e)" [attr.aria-label]="'React ' + e" style="opacity:.6">{{ e }}</button> } }
    </div>

    <h3 class="mt-16" style="margin-top:28px">{{ p.comments.length }} comment{{ p.comments.length === 1 ? '' : 's' }}</h3>
    <div class="composer mt-8"><nx-avatar [user]="auth.me()" [size]="28" /><div class="grow col">
      <textarea class="input" rows="3" [(ngModel)]="newComment" placeholder="Add a comment…"></textarea>
      <div class="row" style="justify-content:flex-end"><button class="btn primary sm" (click)="comment()" [disabled]="!newComment.trim()">Save</button></div></div></div>
    @for (c of p.comments; track c.id) {
      <div class="comment"><nx-avatar [user]="c.author" [size]="28" /><div class="grow">
        <div class="row"><span class="strong">{{ c.author?.name }}</span><span class="tiny subtle">{{ ago(c.createdAt) }}</span></div>
        <div class="prose" style="font-size:13.5px" [innerHTML]="md(c.body)"></div>
        @if (c.author?.id === auth.me()?.id) { <button class="link-btn tiny" (click)="deleteComment(c.id)">Delete</button> }</div></div>
    }
  </div>
  }
  @if (history(); as h) {
    <nx-modal title="Page history" (closed)="history.set(null)">
      <div class="modal-body">@for (v of h; track v.id) {
        <div class="row"><span class="strong" style="width:40px">v{{ v.version }}</span><nx-avatar [user]="v.author" [size]="20" /><span class="grow">{{ v.author?.name }}</span>
          <span class="small subtle">{{ dateTime(v.createdAt) }}</span>@if (v.version !== p()?.version) { <button class="btn sm" (click)="restore(v)">Restore</button> } @else { <span class="chip">Current</span> }</div> }</div>
    </nx-modal>
  }
  @if (confirmDelete()) {
    <nx-modal title="Delete this page?" (closed)="confirmDelete.set(false)">
      <div class="modal-body"><p>The page, its comments and history are deleted permanently. Child pages move up to this page's parent.</p></div>
      <div class="modal-foot"><button class="btn ghost" (click)="confirmDelete.set(false)">Cancel</button><button class="btn danger" (click)="remove()">Delete</button></div>
    </nx-modal>
  }`,
})
export class PageViewPage {
  private pagesApi = inject(PagesApi);
  private api = inject(Api);
  private hub = inject(Hub);
  private toasts = inject(Toasts);
  private router = inject(Router);
  readonly auth = inject(Auth);
  readonly key = input.required<string>();
  readonly id = input.required<string>();
  readonly p = signal<PageView | null>(null);
  readonly starred = signal(false);
  readonly history = signal<VersionView[] | null>(null);
  readonly confirmDelete = signal(false);
  readonly emojis = ['👍', '🎉', '👀', '❤️'];
  readonly html = computed(() => { const p = this.p(); return markdown(p ? (p.status === 'DRAFT' ? p.draftBody : p.body) : ''); });
  ago = ago; dateTime = dateTime;
  newComment = '';

  constructor() { effect(() => { this.id(); this.load(); }); }

  async load() {
    try {
      const p = await this.pagesApi.page(+this.id());
      this.p.set(p);
      this.hub.viewed({ app: 'pages', kind: 'page', title: p.title, subtitle: `Pages · ${p.space.name}`, link: `/pages/${p.space.key}/${p.id}` });
      this.hub.isStarred(`/pages/${p.space.key}/${p.id}`).then(s => this.starred.set(s));
    } catch { this.router.navigate(['/pages', this.key()]); }
  }
  md(s: string) { return markdown(s); }
  has(e: string) { return this.p()?.reactions.some(r => r.emoji === e) ?? false; }
  async react(e: string) { const r = await this.api.quiet(this.pagesApi.react(this.p()!.id, e)); if (r) this.p.update(p => p && { ...p, reactions: r }); }
  async comment() {
    const c = await this.api.quiet(this.pagesApi.comment(this.p()!.id, this.newComment));
    if (c) { this.p.update(p => p && { ...p, comments: [...p.comments, c] }); this.newComment = ''; }
  }
  async deleteComment(id: number) { await this.api.quiet(this.pagesApi.deleteComment(id)); this.p.update(p => p && { ...p, comments: p.comments.filter(c => c.id !== id) }); }
  share() { navigator.clipboard?.writeText(location.href); this.toasts.info('Link copied — anyone in your organization can open it'); }
  async star() { const p = this.p()!; this.starred.set(await this.hub.toggleStar({ app: 'pages', kind: 'page', title: p.title, link: `/pages/${p.space.key}/${p.id}` })); }
  async openHistory() { this.history.set(await this.pagesApi.versions(this.p()!.id)); }
  async restore(v: VersionView) { const p = await this.api.quiet(this.pagesApi.restore(this.p()!.id, v.id)); if (p) { this.p.set(p); this.history.set(null); this.toasts.info(`Restored version ${v.version}`); } }
  async addChild() {
    const c = await this.pagesApi.create({ spaceKey: this.key(), parentId: this.p()!.id, title: 'Untitled', body: '' });
    this.pagesApi.touch();
    this.router.navigate(['/pages', this.key(), c.id, 'edit']);
  }
  async remove() {
    if (await this.api.quiet(this.pagesApi.remove(this.p()!.id)) !== null) { this.pagesApi.touch(); this.router.navigate(['/pages', this.key()]); }
  }
}
