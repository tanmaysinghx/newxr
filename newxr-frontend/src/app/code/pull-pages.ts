import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiError } from '../core/api';
import { ago, dateTime, label } from '../core/format';
import { Hub } from '../core/hub';
import { markdown } from '../core/markdown';
import { People } from '../core/people';
import { Toasts } from '../core/toasts';
import { Avatar, Avatars, Empty, Icon, Status } from '../shared/ui';
import { CodeApi, FileDiff, PullComment, PullRow, PullView } from './code-api';
import { RepoContext, firstLine, short } from './code-pages';

@Component({
  selector: 'nx-code-pulls',
  imports: [RouterLink, Avatar, Avatars, Icon, Status, Empty],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="page">
    <div class="title-bar"><h1>Pull requests</h1><div class="actions"><a class="btn primary" [routerLink]="['/code', ctx.slug(), 'pulls', 'new']"><nx-icon name="pr" /> Create pull request</a></div></div>
    <div class="tabs">@for (s of states; track s) { <button [class.active]="state() === s" (click)="state.set(s)">{{ s === 'ALL' ? 'All' : label(s) }}</button> }</div>
    <div class="card flush"><div class="table-wrap"><table class="table">
      <thead><tr><th>Pull request</th><th>Author</th><th>Reviewers</th><th>Build</th><th>Status</th><th>Updated</th></tr></thead>
      <tbody>@for (p of rows(); track p.number) {
        <tr class="clickable" [routerLink]="['/code', ctx.slug(), 'pulls', p.number]"><td><div><b>{{ p.title }}</b> <span class="subtle">#{{ p.number }}</span></div>
            <div class="tiny muted mono">{{ p.sourceBranch }} → {{ p.targetBranch }}@if (p.comments) { · {{ p.comments }} comments }</div></td>
          <td><div class="row nowrap"><nx-avatar [user]="p.author" [size]="20" />{{ p.author?.name }}</div></td>
          <td><nx-avatars [users]="reviewerUsers(p)" [size]="20" /></td>
          <td>@if (p.checks) { <nx-status [value]="p.checks" /> }</td><td><nx-status [value]="p.status" /></td><td class="muted nowrap">{{ ago(p.updatedAt) }}</td></tr>
      } @empty { <tr><td colspan="6"><nx-empty icon="pr" title="No pull requests here" /></td></tr> }</tbody></table></div></div>
  </div>`,
})
export class CodePulls {
  private codeApi = inject(CodeApi);
  readonly ctx = inject(RepoContext);
  readonly state = signal('OPEN');
  readonly rows = signal<PullRow[]>([]);
  readonly states = ['OPEN', 'MERGED', 'DECLINED', 'ALL'];
  ago = ago; label = label;
  constructor() { effect(() => { const s = this.state(); if (this.ctx.view()) this.codeApi.pulls(this.ctx.slug(), s).then(r => this.rows.set(r)); }); }
  reviewerUsers(p: PullRow) { return p.reviewers.map(r => r.user); }
}

@Component({
  selector: 'nx-code-new-pull',
  imports: [FormsModule, RouterLink, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="page narrow">
    <div class="crumbs"><a [routerLink]="['/code', ctx.slug(), 'pulls']">Pull requests</a><span class="sep">/</span><span>New</span></div>
    <div class="title-bar"><h1>Create pull request</h1></div>
    <div class="card col" style="gap:14px">
      @if (error()) { <div class="banner danger"><nx-icon name="alert" /><div>{{ error() }}</div></div> }
      <div class="row wrap"><select class="input mono" style="width:auto" [(ngModel)]="f.sourceBranch" (ngModelChange)="autoTitle()" aria-label="Source branch">
          <option value="">Pick a branch</option>@for (n of others(); track n) { <option [value]="n">{{ n }}</option> }</select>
        <nx-icon name="arrow-right" /><select class="input mono" style="width:auto" [(ngModel)]="f.targetBranch" aria-label="Target branch">@for (n of ctx.view()?.branchNames ?? []; track n) { <option [value]="n">{{ n }}</option> }</select></div>
      <div class="field"><label>Title</label><input class="input" [(ngModel)]="f.title" /></div>
      <div class="field"><label>Description</label><textarea class="input" rows="6" [(ngModel)]="f.description" placeholder="What changes and why. Mention issue keys (ORB-412) to link them."></textarea></div>
      <div class="field"><label>Reviewers</label><div class="row wrap">@for (u of people.all(); track u.id) {
        <label class="chip" style="cursor:pointer"><input type="checkbox" [checked]="reviewers.has(u.id)" (change)="toggle(u.id)" /> {{ u.name }}</label> }</div></div>
      <div class="row"><span class="grow"></span><a class="btn ghost" [routerLink]="['/code', ctx.slug(), 'pulls']">Cancel</a><button class="btn primary" (click)="create()">Create pull request</button></div>
    </div>
  </div>`,
})
export class CodeNewPull {
  private codeApi = inject(CodeApi);
  private router = inject(Router);
  readonly ctx = inject(RepoContext);
  readonly people = inject(People);
  readonly source = input<string | undefined>();
  readonly error = signal<string | null>(null);
  readonly others = computed(() => (this.ctx.view()?.branchNames ?? []).filter(n => n !== this.f.targetBranch));
  reviewers = new Set<number>();
  f = { sourceBranch: '', targetBranch: '', title: '', description: '' };
  constructor() {
    this.people.load();
    effect(() => {
      const v = this.ctx.view(); if (!v) return;
      this.f.targetBranch ||= v.repo.defaultBranch;
      if (this.source()) { this.f.sourceBranch = this.source()!; this.autoTitle(); }
    });
  }
  autoTitle() {
    if (this.f.title) return;
    const name = this.f.sourceBranch.split('/').pop() ?? '';
    this.f.title = name.replace(/[-_]/g, ' ').replace(/^\w/, c => c.toUpperCase());
  }
  toggle(id: number) { this.reviewers.has(id) ? this.reviewers.delete(id) : this.reviewers.add(id); }
  async create() {
    try { const p = await this.codeApi.createPull(this.ctx.slug(), { ...this.f, reviewerIds: [...this.reviewers] }); this.ctx.reload(); this.router.navigate(['/code', this.ctx.slug(), 'pulls', p.number]); }
    catch (e) { this.error.set((e as ApiError).message); }
  }
}

@Component({
  selector: 'nx-code-pull',
  imports: [FormsModule, RouterLink, Avatar, Icon, Status],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './pull-page.scss',
  templateUrl: './pull-page.html',
})
export class CodePull {
  private codeApi = inject(CodeApi);
  private toasts = inject(Toasts);
  private hub = inject(Hub);
  readonly ctx = inject(RepoContext);
  readonly people = inject(People);
  readonly n = input.required<string>();
  readonly p = signal<PullView | null>(null);
  readonly tab = signal<'overview' | 'commits' | 'files' | 'checks'>('overview');
  readonly collapsed = signal(new Set<string>());
  readonly lineBox = signal<{ path: string; line: number } | null>(null);
  readonly me = computed(() => this.p()?.reviewers);
  readonly general = computed(() => (this.p()?.comments ?? []).filter(c => !c.path));
  readonly desc = computed(() => markdown(this.p()?.description));
  ago = ago; dateTime = dateTime; label = label; short = short; firstLine = firstLine; md = markdown;
  body = ''; lineBody = '';

  constructor() { this.people.load(); effect(() => { const n = +this.n(); if (this.ctx.view()) this.load(n); }); }
  async load(n: number) {
    const p = await this.codeApi.pull(this.ctx.slug(), n);
    this.p.set(p);
    this.hub.viewed({ app: 'code', kind: 'pr', key: `#${p.number}`, title: p.title, subtitle: `Code · ${p.repoName}`, link: `/code/${p.repoSlug}/pulls/${p.number}` });
  }
  private async run(f: () => Promise<PullView>, msg?: string) {
    try { this.p.set(await f()); if (msg) this.toasts.info(msg); this.ctx.reload(); } catch (e) { this.toasts.error((e as ApiError).message); }
  }
  review(state: string) { this.run(() => this.codeApi.review(this.ctx.slug(), this.p()!.number, state), state === 'APPROVED' ? 'Approved' : state === 'NEEDS_WORK' ? 'Marked as needs work' : undefined); }
  merge() { this.run(() => this.codeApi.merge(this.ctx.slug(), this.p()!.number), 'Merged'); }
  decline() { this.run(() => this.codeApi.decline(this.ctx.slug(), this.p()!.number), 'Declined'); }
  reopen() { this.run(() => this.codeApi.reopen(this.ctx.slug(), this.p()!.number), 'Reopened'); }
  resolve(path: string) { this.run(() => this.codeApi.resolve(this.ctx.slug(), this.p()!.number, path), `Keeping the branch's ${path}`); }
  async updateBranch() {
    try { const r = await this.codeApi.updateBranch(this.ctx.slug(), this.p()!.number); this.p.set(r.pull); this.toasts.info(r.updated ? `${r.updated} file${r.updated === 1 ? '' : 's'} updated from ${r.pull.targetBranch}` : 'Already up to date'); }
    catch (e) { this.toasts.error((e as ApiError).message); }
  }
  async comment() {
    if (!this.body.trim()) return;
    await this.run(() => this.codeApi.comment(this.ctx.slug(), this.p()!.number, { body: this.body }));
    this.body = '';
  }
  async lineComment() {
    const at = this.lineBox(); if (!at || !this.lineBody.trim()) return;
    await this.run(() => this.codeApi.comment(this.ctx.slug(), this.p()!.number, { body: this.lineBody, path: at.path, line: at.line }));
    this.lineBody = ''; this.lineBox.set(null);
  }
  async setReviewers(ids: number[]) { await this.run(() => this.codeApi.editPull(this.ctx.slug(), this.p()!.number, { reviewerIds: ids })); }
  addReviewer(id: number | null) { if (id != null) this.setReviewers([...this.p()!.reviewers.map(r => r.user.id), id]); }
  toggleFile(path: string) { const s = new Set(this.collapsed()); s.has(path) ? s.delete(path) : s.add(path); this.collapsed.set(s); }
  commentsAt(f: FileDiff, line?: number): PullComment[] { return line == null ? [] : (this.p()?.comments ?? []).filter(c => c.path === f.path && c.line === line); }
  notReviewer(id: number) { const p = this.p(); return !!p && !p.reviewers.some(r => r.user.id === id) && p.author?.id !== id; }
  stageIcon(s: string) { return s === 'PASSED' ? 'check-circle' : s === 'FAILED' ? 'x-circle' : s === 'RUNNING' ? 'refresh' : 'clock'; }
}
