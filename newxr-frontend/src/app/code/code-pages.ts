import { ChangeDetectionStrategy, Component, Injectable, computed, effect, inject, input, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink, RouterOutlet } from '@angular/router';
import { ApiError } from '../core/api';
import { ago, dateTime, duration } from '../core/format';
import { Hub } from '../core/hub';
import { markdown } from '../core/markdown';
import { Toasts } from '../core/toasts';
import { AppFrame } from '../shell/shell';
import { NavItem, Sidebar } from '../shell/sidebar';
import { Avatar, Empty, Icon, Modal, Status } from '../shared/ui';
import { BranchView, CodeApi, CommitView, DeploymentView, FileView, PipelineRow, RepoSummary, RepoView, StageView, Tree } from './code-api';

/** The open repository, shared by the layout and its pages. */
@Injectable()
export class RepoContext {
  private codeApi = inject(CodeApi);
  readonly slug = signal('');
  readonly view = signal<RepoView | null>(null);
  async reload() { if (this.slug()) this.view.set(await this.codeApi.repo(this.slug())); }
}

export const short = (sha?: string | null) => (sha ?? '').slice(0, 7);
export const firstLine = (s?: string | null) => (s ?? '').split('\n')[0];

// ---- repositories ------------------------------------------------------------------

@Component({
  selector: 'nx-code-repos',
  imports: [FormsModule, RouterLink, Icon, Status, Modal, AppFrame, Sidebar],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<nx-app-frame><nx-sidebar [context]="{ title: 'Newxr Code', subtitle: 'Repositories', app: 'code' }"
      [items]="[{ label: 'Repositories', icon: 'code', link: '/code', exact: true }]" />
    <div class="page">
      <div class="title-bar"><div><h1>Repositories</h1><div class="sub">Source, pull requests, and pipelines reported by your CI.</div></div>
        <div class="actions"><button class="btn primary" (click)="creating.set(true)"><nx-icon name="plus" /> Create repository</button></div></div>
      <div class="card flush"><div class="table-wrap"><table class="table">
        <thead><tr><th>Repository</th><th>Language</th><th>Open PRs</th><th>Branches</th><th>Last commit</th><th>Build</th><th>Updated</th></tr></thead>
        <tbody>@for (r of repos(); track r.id) {
          <tr class="clickable" [routerLink]="['/code', r.slug]"><td><div class="strong">{{ r.namespace }} / {{ r.name }}</div><div class="tiny muted">{{ r.description }}</div></td>
            <td class="muted">{{ r.language ?? '—' }}</td><td>{{ r.openPulls }}</td><td>{{ r.branches }}</td><td class="truncate" style="max-width:260px">{{ r.lastCommitMessage }}</td>
            <td>@if (r.lastPipelineStatus) { <nx-status [value]="r.lastPipelineStatus" /> } @else { <span class="subtle">—</span> }</td><td class="muted nowrap">{{ ago(r.updatedAt) }}</td></tr>
        } @empty { <tr><td colspan="7"><div class="empty">No repositories yet.</div></td></tr> }</tbody></table></div></div>
    </div></nx-app-frame>
    @if (creating()) {
      <nx-modal title="Create repository" (closed)="creating.set(false)">
        <div class="modal-body">
          @if (error()) { <div class="banner danger"><nx-icon name="alert" /><div>{{ error() }}</div></div> }
          <div class="field"><label>Name <span class="req">*</span></label><input class="input" [(ngModel)]="f.name" placeholder="payments-api" autofocus /></div>
          <div class="field"><label>Description</label><input class="input" [(ngModel)]="f.description" /></div>
          <div class="form-grid"><div class="field"><label>Default branch</label><input class="input" [(ngModel)]="f.defaultBranch" /></div>
            <div class="field"><label>Language</label><input class="input" [(ngModel)]="f.language" placeholder="Kotlin" /></div></div>
          <div class="small muted">Starts with a README. Edit files in the browser; Git push and clone come in a later version.</div>
        </div>
        <div class="modal-foot"><button class="btn ghost" (click)="creating.set(false)">Cancel</button><button class="btn primary" (click)="create()">Create</button></div>
      </nx-modal>
    }`,
})
export class CodeRepos {
  private codeApi = inject(CodeApi);
  private router = inject(Router);
  readonly repos = signal<RepoSummary[]>([]);
  readonly creating = signal(false);
  readonly error = signal<string | null>(null);
  ago = ago;
  f = { name: '', description: '', defaultBranch: 'main', language: '' };
  constructor() { this.codeApi.repos().then(r => this.repos.set(r)); }
  async create() {
    try { const r = await this.codeApi.createRepo(this.f); this.router.navigate(['/code', r.repo.slug]); }
    catch (e) { this.error.set((e as ApiError).message); }
  }
}

// ---- layout --------------------------------------------------------------------------

@Component({
  selector: 'nx-code-layout',
  imports: [RouterOutlet, RouterLink, AppFrame, Sidebar, Icon],
  providers: [RepoContext],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [`.repo-head{display:flex;align-items:center;gap:10px;padding:16px 32px 0} .repo-head h1{font-size:18px} .repo-ic{width:26px;height:26px;border-radius:7px;background:var(--code);color:#fff;display:inline-flex;align-items:center;justify-content:center}`],
  template: `<nx-app-frame>
    <nx-sidebar [context]="{ title: ctx.view()?.repo?.name ?? slug(), subtitle: 'Repository', app: 'code' }" [items]="items()" />
    <div>
      @if (ctx.view(); as v) {
        <div class="repo-head"><span class="repo-ic"><nx-icon name="code" [size]="15" /></span>
          <h1 class="grow"><a routerLink="/code" class="muted">{{ v.repo.namespace }}</a> / <a [routerLink]="['/code', v.repo.slug]">{{ v.repo.name }}</a></h1>
          <button class="btn sm" (click)="watch()"><nx-icon name="eye" [size]="14" /> {{ v.watching ? 'Unwatch' : 'Watch' }} · {{ v.watchers.length }}</button></div>
      }
      <router-outlet />
    </div></nx-app-frame>`,
})
export class CodeLayout {
  readonly ctx = inject(RepoContext);
  private codeApi = inject(CodeApi);
  private router = inject(Router);
  private hub = inject(Hub);
  readonly slug = input.required<string>();
  readonly items = computed<NavItem[]>(() => {
    const s = this.slug(), v = this.ctx.view();
    return [
      { label: 'Source', icon: 'folder', link: `/code/${s}/source` },
      { label: 'Commits', icon: 'commit', link: `/code/${s}/commits` },
      { label: 'Branches', icon: 'branch', link: `/code/${s}/branches`, count: v?.branchNames.length ?? null },
      { label: 'Pull requests', icon: 'pr', link: `/code/${s}/pulls`, count: v?.repo.openPulls || null },
      { label: 'Pipelines', icon: 'play', link: `/code/${s}/pipelines` },
      { label: 'Deployments', icon: 'server', link: `/code/${s}/deployments` },
      { label: 'Repository settings', icon: 'settings', link: `/code/${s}/settings` },
    ];
  });
  constructor() {
    effect(() => {
      this.ctx.slug.set(this.slug());
      this.ctx.reload().then(() => {
        const r = this.ctx.view()!.repo;
        this.hub.viewed({ app: 'code', kind: 'repo', title: `${r.namespace} / ${r.name}`, subtitle: 'Code · Repository', link: `/code/${r.slug}` });
      }).catch(() => this.router.navigateByUrl('/code'));
    });
  }
  async watch() { this.ctx.view.set(await this.codeApi.watch(this.slug())); }
}

// ---- source ---------------------------------------------------------------------------

@Component({
  selector: 'nx-code-source',
  imports: [FormsModule, NgTemplateOutlet, RouterLink, Avatar, Icon, Status],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './code-source.scss',
  templateUrl: './code-source.html',
})
export class CodeSource {
  private codeApi = inject(CodeApi);
  private router = inject(Router);
  private toasts = inject(Toasts);
  readonly ctx = inject(RepoContext);
  readonly branch = input<string | undefined>();
  readonly path = input<string | undefined>();
  readonly file = input<string | undefined>();
  readonly tree = signal<Tree | null>(null);
  readonly f = signal<FileView | null>(null);
  readonly editing = signal(false);
  readonly creating = signal(false);
  ago = ago; short = short; firstLine = firstLine;
  readonly b = computed(() => this.branch() || this.ctx.view()?.repo.defaultBranch || 'main');
  readonly crumbs = computed(() => {
    const p = this.file() ?? this.path() ?? '';
    const parts = p ? p.split('/') : [];
    return parts.map((name, i) => ({ name, path: parts.slice(0, i + 1).join('/'), last: i === parts.length - 1 }));
  });
  readonly readme = computed(() => markdown(this.tree()?.readme));
  readonly lines = computed(() => (this.f()?.content ?? '').replace(/\n$/, '').split('\n'));
  draft = { content: '', message: '', mode: 'commit' as 'commit' | 'branch', newBranch: '', path: '' };

  constructor() {
    effect(() => {
      const slug = this.ctx.slug(), b = this.branch(), p = this.path(), file = this.file();
      if (!slug || !this.ctx.view()) return;
      this.editing.set(false);
      if (file) { this.tree.set(null); this.codeApi.file(slug, file, b).then(f => this.f.set(f)).catch(e => this.toasts.error((e as ApiError).message)); }
      else { this.f.set(null); this.codeApi.tree(slug, b, p ?? '').then(t => this.tree.set(t)).catch(e => this.toasts.error((e as ApiError).message)); }
    });
  }
  go(q: Record<string, string | null>) { this.router.navigate([], { queryParams: { branch: this.branch() ?? null, path: null, file: null, ...q } }); }
  open(e: { dir: boolean; path: string }) { this.go(e.dir ? { path: e.path } : { file: e.path }); }
  edit() {
    const f = this.f()!;
    this.draft = { content: f.content, message: `Update ${f.path.split('/').pop()}`, mode: 'commit', newBranch: '', path: f.path };
    this.editing.set(true);
  }
  startNew() {
    const dir = this.path() ? this.path() + '/' : '';
    this.draft = { content: '', message: '', mode: 'commit', newBranch: '', path: dir };
    this.creating.set(true);
  }
  async commit() {
    const d = this.draft;
    const message = d.message || (this.creating() ? `Add ${d.path.split('/').pop()}` : 'Update file');
    try {
      await this.codeApi.save(this.ctx.slug(), { branch: this.b(), path: d.path, content: d.content, message, newBranch: d.mode === 'branch' ? d.newBranch : undefined });
      this.toasts.info('Committed');
      this.editing.set(false); this.creating.set(false);
      const branch = d.mode === 'branch' ? d.newBranch : this.b();
      await this.ctx.reload();
      if (d.mode === 'branch') this.router.navigate(['/code', this.ctx.slug(), 'pulls', 'new'], { queryParams: { source: branch } });
      else this.router.navigate([], { queryParams: { branch, file: d.path, path: null } });
    } catch (e) { this.toasts.error((e as ApiError).message); }
  }
}

// ---- commits & branches -----------------------------------------------------------------

@Component({
  selector: 'nx-code-commits',
  imports: [FormsModule, Avatar, Status, Empty],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [`.c{display:flex;align-items:center;gap:10px;padding:10px 16px;border-bottom:1px solid var(--border)} .c:last-child{border-bottom:0}
    .sha{font-family:var(--mono);font-size:12px;color:var(--link)}`],
  template: `<div class="page">
    <div class="title-bar"><h1>Commits</h1>
      <select class="input" style="width:auto" [ngModel]="b()" (ngModelChange)="pick($event)" aria-label="Branch">@for (n of ctx.view()?.branchNames ?? []; track n) { <option [value]="n">{{ n }}</option> }</select></div>
    <div class="card flush">@for (c of rows(); track c.sha) {
      <div class="c"><nx-avatar [user]="c.author" [size]="24" /><div class="grow" style="min-width:0"><div class="truncate">{{ firstLine(c.message) }}</div>
        <div class="tiny muted">{{ c.author?.name }} committed {{ ago(c.at) }} · <span class="success-text">+{{ c.additions }}</span> <span class="danger-text">−{{ c.deletions }}</span> · {{ c.paths }}</div></div>
        @if (c.checks) { <nx-status [value]="c.checks" /> }<span class="sha" [title]="c.sha">{{ short(c.sha) }}</span></div>
    } @empty { <nx-empty icon="commit" title="No commits on this branch yet" text="Branches start from their base; commits made on the branch appear here." /> }</div>
  </div>`,
})
export class CodeCommits {
  private codeApi = inject(CodeApi);
  private router = inject(Router);
  readonly ctx = inject(RepoContext);
  readonly branch = input<string | undefined>();
  readonly rows = signal<CommitView[]>([]);
  readonly b = computed(() => this.branch() || this.ctx.view()?.repo.defaultBranch || 'main');
  ago = ago; short = short; firstLine = firstLine;
  constructor() { effect(() => { const s = this.ctx.slug(); const b = this.b(); if (s && this.ctx.view()) this.codeApi.commits(s, b).then(r => this.rows.set(r)); }); }
  pick(b: string) { this.router.navigate([], { queryParams: { branch: b } }); }
}

@Component({
  selector: 'nx-code-branches',
  imports: [FormsModule, RouterLink, Icon, Status, Modal],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="page">
    <div class="title-bar"><h1>Branches</h1><div class="actions"><button class="btn primary" (click)="creating.set(true)"><nx-icon name="branch" /> Create branch</button></div></div>
    <div class="card flush"><div class="table-wrap"><table class="table">
      <thead><tr><th>Branch</th><th>Last commit</th><th>Build</th><th>Pull request</th><th>Updated</th><th></th></tr></thead>
      <tbody>@for (b of rows(); track b.name) {
        <tr><td><a class="mono" [routerLink]="['/code', ctx.slug(), 'source']" [queryParams]="{ branch: b.name }">{{ b.name }}</a>
            @if (b.isDefault) { <span class="lozenge">Default</span> } @else if (b.baseBranch) { <div class="tiny subtle">from {{ b.baseBranch }}</div> }</td>
          <td class="truncate" style="max-width:300px">{{ b.head ? firstLine(b.head.message) : '—' }}</td>
          <td>@if (b.pipelineStatus) { <nx-status [value]="b.pipelineStatus" /> }</td>
          <td>@if (b.openPull) { <a [routerLink]="['/code', ctx.slug(), 'pulls', b.openPull]">#{{ b.openPull }}</a> }
            @else if (!b.isDefault) { <a class="small" [routerLink]="['/code', ctx.slug(), 'pulls', 'new']" [queryParams]="{ source: b.name }">Create pull request</a> }</td>
          <td class="muted nowrap">{{ ago(b.updatedAt) }}</td>
          <td>@if (!b.isDefault) { <button class="btn sm ghost icon" (click)="remove(b)" [attr.aria-label]="'Delete ' + b.name"><nx-icon name="x" /></button> }</td></tr>
      }</tbody></table></div></div>
  </div>
  @if (creating()) {
    <nx-modal title="Create branch" (closed)="creating.set(false)">
      <div class="modal-body"><div class="field"><label>Name</label><input class="input mono" [(ngModel)]="name" placeholder="feature/ORB-412-offline-cache" autofocus /></div>
        <div class="field"><label>From</label><select class="input" [(ngModel)]="from">@for (n of ctx.view()?.branchNames ?? []; track n) { <option [value]="n">{{ n }}</option> }</select></div>
        <div class="small muted">Put an issue key such as ORB-412 in the name to link the branch to that Track issue.</div></div>
      <div class="modal-foot"><button class="btn ghost" (click)="creating.set(false)">Cancel</button><button class="btn primary" (click)="create()">Create</button></div>
    </nx-modal>
  }`,
})
export class CodeBranches {
  private codeApi = inject(CodeApi);
  private toasts = inject(Toasts);
  readonly ctx = inject(RepoContext);
  readonly rows = signal<BranchView[]>([]);
  readonly creating = signal(false);
  ago = ago; firstLine = firstLine;
  name = ''; from = '';
  constructor() { effect(() => { if (this.ctx.view()) { this.from = this.ctx.view()!.repo.defaultBranch; this.load(); } }); }
  async load() { this.rows.set(await this.codeApi.branches(this.ctx.slug())); }
  async create() {
    try { await this.codeApi.createBranch(this.ctx.slug(), this.name, this.from); this.creating.set(false); this.name = ''; await this.ctx.reload(); }
    catch (e) { this.toasts.error((e as ApiError).message); }
  }
  async remove(b: BranchView) {
    try { await this.codeApi.deleteBranch(this.ctx.slug(), b.name); this.toasts.info(`Deleted ${b.name}`); await this.ctx.reload(); }
    catch (e) { this.toasts.error((e as ApiError).message); }
  }
}

// ---- pipelines & deployments ---------------------------------------------------------------

const STAGE_ICON: Record<string, string> = { PASSED: 'check', FAILED: 'x', RUNNING: 'refresh', STOPPED: 'pause', QUEUED: 'clock' };

@Component({
  selector: 'nx-code-pipelines',
  imports: [RouterLink, Avatar, Icon, Status, Empty],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './code-pipelines.scss',
  template: `<div class="page">
    <div class="title-bar"><div><h1>Pipelines</h1><div class="sub">Runs reported by your CI through the repository webhook (Repository settings).</div></div></div>
    @if (live(); as l) {
      <div class="card">
        <div class="row"><nx-icon name="refresh" [size]="16" /><a class="strong" [routerLink]="['/code', ctx.slug(), 'pipelines', l.pipeline.number]">#{{ l.pipeline.number }} · {{ l.pipeline.branch }}</a>
          <span class="grow"></span><nx-status [value]="l.pipeline.status" />
          @if (l.pipeline.status === 'RUNNING') { <button class="btn sm" (click)="stop(l.pipeline.number)"><nx-icon name="pause" [size]="13" /> Stop</button> }</div>
        <div class="stages">@for (s of l.stages; track s.name; let last = $last) {
          <div class="stage" [class]="'stage ' + s.status"><span class="dot"><nx-icon [name]="stageIcon(s.status)" [size]="13" [stroke]="2.6" /></span>
            <div class="small strong">{{ s.name }}</div><div class="tiny muted">{{ s.durationSeconds != null ? dur(s.durationSeconds) : '—' }}</div></div>
          @if (!last) { <span class="bar" [class.done]="s.status === 'PASSED'"></span> }
        }</div>
        @if (logOf(l.stages); as log) { <pre class="log">{{ log }}</pre> }
      </div>
    }
    <div class="card flush"><div class="table-wrap"><table class="table">
      <thead><tr><th>Pipeline</th><th>Branch</th><th>Commit</th><th>Status</th><th>Duration</th><th>Triggered by</th><th>Started</th></tr></thead>
      <tbody>@for (p of rows(); track p.number) {
        <tr class="clickable" [routerLink]="['/code', ctx.slug(), 'pipelines', p.number]"><td><a>#{{ p.number }}</a></td><td class="mono small">{{ p.branch }}</td>
          <td class="truncate" style="max-width:260px">{{ p.commitMessage ?? short(p.commitSha) }}</td><td><nx-status [value]="p.status" /></td>
          <td class="muted">{{ p.durationSeconds != null ? dur(p.durationSeconds) : '—' }}</td>
          <td><div class="row nowrap"><nx-avatar [user]="p.triggeredBy" [size]="20" />{{ p.triggeredByName }}</div></td><td class="muted nowrap">{{ ago(p.startedAt) }}</td></tr>
      } @empty { <tr><td colspan="7"><nx-empty icon="play" title="No pipelines yet" text="Point your CI at the webhook in Repository settings to report runs here." /></td></tr> }</tbody></table></div></div>
  </div>`,
})
export class CodePipelines {
  private codeApi = inject(CodeApi);
  private toasts = inject(Toasts);
  readonly ctx = inject(RepoContext);
  readonly rows = signal<PipelineRow[]>([]);
  readonly live = signal<{ pipeline: PipelineRow; stages: StageView[] } | null>(null);
  ago = ago; short = short; stageIcon = (s: string) => STAGE_ICON[s] ?? 'clock';
  constructor() { effect(() => { if (this.ctx.view()) this.load(); }); }
  async load() {
    const rows = await this.codeApi.pipelines(this.ctx.slug());
    this.rows.set(rows);
    const running = rows.find(r => r.status === 'RUNNING') ?? rows[0];
    this.live.set(running ? await this.codeApi.pipeline(this.ctx.slug(), running.number) : null);
  }
  dur(s: number) { return s < 60 ? `${s}s` : duration(Math.round(s / 60)); }
  logOf(st: StageView[]) { return (st.find(s => s.status === 'RUNNING' || s.status === 'FAILED') ?? st.filter(s => s.log).at(-1))?.log ?? null; }
  async stop(n: number) { try { await this.codeApi.stop(this.ctx.slug(), n); this.load(); } catch (e) { this.toasts.error((e as ApiError).message); } }
}

@Component({
  selector: 'nx-code-pipeline',
  imports: [RouterLink, Avatar, Icon, Status],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './code-pipelines.scss',
  template: `@if (p(); as p) { <div class="page">
    <div class="crumbs"><a [routerLink]="['/code', ctx.slug(), 'pipelines']">Pipelines</a><span class="sep">/</span><span>#{{ p.pipeline.number }}</span></div>
    <div class="title-bar"><div><h1>#{{ p.pipeline.number }} · {{ p.pipeline.branch }}</h1>
      <div class="sub">{{ p.pipeline.commitMessage }} @if (p.pipeline.commitSha) { · <span class="mono">{{ short(p.pipeline.commitSha) }}</span> }</div></div>
      <div class="actions"><nx-status [value]="p.pipeline.status" />
        @if (p.pipeline.url) { <a class="btn" [href]="p.pipeline.url" target="_blank" rel="noopener"><nx-icon name="link" /> Open in CI</a> }
        @if (p.pipeline.status === 'RUNNING') { <button class="btn" (click)="stop()"><nx-icon name="pause" /> Stop</button> }</div></div>
    <div class="small muted row"><nx-avatar [user]="p.pipeline.triggeredBy" [size]="18" /> {{ p.pipeline.triggeredByName ?? 'CI' }} · started {{ dateTime(p.pipeline.startedAt) }}
      @if (p.pipeline.finishedAt) { · finished {{ dateTime(p.pipeline.finishedAt) }} }</div>
    @for (s of p.stages; track s.name) {
      <div class="card"><div class="row"><span class="dot" [class]="'dot ' + s.status"><nx-icon [name]="stageIcon(s.status)" [size]="13" [stroke]="2.6" /></span>
        <b class="grow">{{ s.name }}</b><span class="small muted">{{ s.durationSeconds != null ? s.durationSeconds + 's' : '' }}</span><nx-status [value]="s.status" /></div>
        @if (s.log) { <pre class="log">{{ s.log }}</pre> }</div>
    } @empty { <div class="card small subtle">No stages were reported for this run.</div> }
  </div> }`,
})
export class CodePipeline {
  private codeApi = inject(CodeApi);
  readonly ctx = inject(RepoContext);
  readonly n = input.required<string>();
  readonly p = signal<{ pipeline: PipelineRow; stages: StageView[] } | null>(null);
  dateTime = dateTime; short = short; stageIcon = (s: string) => STAGE_ICON[s] ?? 'clock';
  constructor() { effect(() => { const n = +this.n(); if (this.ctx.view()) this.codeApi.pipeline(this.ctx.slug(), n).then(p => this.p.set(p)); }); }
  async stop() { this.p.set(await this.codeApi.stop(this.ctx.slug(), +this.n())); }
}

@Component({
  selector: 'nx-code-deployments',
  imports: [RouterLink, Status, Empty],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="page">
    <div class="title-bar"><div><h1>Deployments</h1><div class="sub">Reported by your CI or deploy tool through the repository webhook.</div></div></div>
    <div class="grid grid-3">@for (e of current(); track e.environment) {
      <div class="card"><div class="stat-label">{{ e.environment }}</div><div class="stat-value" style="font-size:20px">{{ e.version ?? short(e.commitSha) }}</div>
        <div class="stat-note muted">{{ ago(e.at) }}@if (e.deployedBy) { · {{ e.deployedBy }} }</div></div>
    }</div>
    <div class="card flush"><div class="table-wrap"><table class="table">
      <thead><tr><th>Environment</th><th>Version</th><th>Status</th><th>Branch</th><th>Pipeline</th><th>By</th><th>When</th></tr></thead>
      <tbody>@for (d of rows(); track d.id) {
        <tr><td class="strong">{{ d.environment }}</td><td class="mono small">{{ d.version ?? short(d.commitSha) }}</td><td><nx-status [value]="d.status" /></td>
          <td class="mono small">{{ d.branch }}</td><td>@if (d.pipelineNumber) { <a [routerLink]="['/code', ctx.slug(), 'pipelines', d.pipelineNumber]">#{{ d.pipelineNumber }}</a> }</td>
          <td class="muted">{{ d.deployedBy }}</td><td class="muted nowrap">{{ ago(d.at) }}</td></tr>
      } @empty { <tr><td colspan="7"><nx-empty icon="server" title="No deployments reported yet" /></td></tr> }</tbody></table></div></div>
  </div>`,
})
export class CodeDeployments {
  readonly ctx = inject(RepoContext);
  readonly rows = signal<DeploymentView[]>([]);
  readonly current = computed(() => {
    const seen = new Set<string>();
    return this.rows().filter(d => d.status === 'SUCCEEDED' && !seen.has(d.environment) && seen.add(d.environment));
  });
  ago = ago; short = short;
  constructor() { const api = inject(CodeApi); effect(() => { if (this.ctx.view()) api.deployments(this.ctx.slug()).then(r => this.rows.set(r)); }); }
}

// ---- settings -----------------------------------------------------------------------------

@Component({
  selector: 'nx-code-settings',
  imports: [FormsModule, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [`pre{background:var(--surface-hover);border:1px solid var(--border);border-radius:8px;padding:10px 12px;white-space:pre-wrap;word-break:break-all;margin:0}`],
  template: `@if (ctx.view(); as v) { <div class="page narrow">
    <div class="title-bar"><h1>Repository settings</h1></div>
    <div class="card col" style="gap:12px"><h3>General</h3>
      <div class="field"><label>Description</label><input class="input" [(ngModel)]="description" /></div>
      <div class="form-grid"><div class="field"><label>Default branch</label><select class="input" [(ngModel)]="defaultBranch">@for (n of v.branchNames; track n) { <option [value]="n">{{ n }}</option> }</select></div>
        <div class="field"><label>Language</label><input class="input" [(ngModel)]="language" /></div></div>
      <div class="row"><span class="grow"></span><button class="btn primary" (click)="save()">Save</button></div></div>
    <div class="card col" style="gap:12px"><h3>CI webhook</h3>
      <div class="small muted">Your CI posts pipeline runs and deployments here. Newxr shows them on branches, pull requests and the Track issues they mention; it doesn't run builds itself.</div>
      @if (hook()) {
        <div class="banner warning"><nx-icon name="lock" /><div>Copy this URL now — it contains a secret and won't be shown again.</div></div>
        <pre class="mono">{{ hook() }}</pre>
        <div class="small strong">Report a run (call again with the same id to update it)</div>
        <pre class="mono">curl -X POST {{ hook() }} -H 'Content-Type: application/json' -d '{{ samplePipeline }}'</pre>
        <div class="small strong">Report a deployment</div>
        <pre class="mono">curl -X POST {{ hook() }} -H 'Content-Type: application/json' -d '{{ sampleDeploy }}'</pre>
      } @else {
        <div class="small">{{ v.ciConfigured ? 'A webhook URL ending in …' + v.ciTokenHint + ' is active.' : 'No webhook yet.' }}</div>
      }
      <div class="row"><button class="btn" (click)="rotate()">{{ v.ciConfigured ? 'Generate a new URL' : 'Generate webhook URL' }}</button>
        @if (v.ciConfigured && !hook()) { <span class="small muted">The old URL stops working.</span> }</div></div>
  </div> }`,
})
export class CodeSettings {
  private codeApi = inject(CodeApi);
  private toasts = inject(Toasts);
  readonly ctx = inject(RepoContext);
  readonly hook = signal<string | null>(null);
  readonly samplePipeline = '{"type":"pipeline","id":"run-42","branch":"main","commit":"a41c9e2","status":"running","triggeredBy":"you@example.com","stages":[{"name":"Build","status":"passed","durationSeconds":160}]}';
  readonly sampleDeploy = '{"type":"deployment","environment":"production","status":"succeeded","version":"4.12.0","branch":"main"}';
  description = ''; defaultBranch = ''; language = '';
  constructor() {
    effect(() => { const v = this.ctx.view(); if (v) { this.description = v.repo.description ?? ''; this.defaultBranch = v.repo.defaultBranch; this.language = v.repo.language ?? ''; } });
  }
  async save() {
    try { this.ctx.view.set(await this.codeApi.editRepo(this.ctx.slug(), { description: this.description, defaultBranch: this.defaultBranch, language: this.language })); this.toasts.info('Saved'); }
    catch (e) { this.toasts.error((e as ApiError).message); }
  }
  async rotate() {
    try { const t = await this.codeApi.ciToken(this.ctx.slug()); this.hook.set(location.origin + t.webhookPath); await this.ctx.reload(); }
    catch (e) { this.toasts.error((e as ApiError).message); }
  }
}
