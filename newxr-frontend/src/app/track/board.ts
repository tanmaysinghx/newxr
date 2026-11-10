import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { label } from '../core/format';
import { Hub } from '../core/hub';
import { Toasts } from '../core/toasts';
import { Avatar, Icon, Modal } from '../shared/ui';
import { IssueCard } from './issue-card';
import { BoardView, IssueRow, STATUSES, SprintView, TYPES, TrackApi } from './track-api';

@Component({
  selector: 'nx-board',
  imports: [FormsModule, RouterLink, IssueCard, Avatar, Icon, Modal],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './board.scss',
  template: `<div class="page">
    <div class="crumbs"><a routerLink="/track">Projects</a><span class="sep">/</span><span>{{ data()?.project?.name }}</span></div>
    <div class="title-bar">
      <div><h1>{{ data()?.sprint?.name ?? 'Board' }}</h1>
        @if (data()?.sprint?.goal) { <div class="sub">Goal: {{ data()?.sprint?.goal }}</div> }
        @if (!data()?.sprint) { <div class="sub">No active sprint — showing every open issue. Start a sprint from the backlog.</div> }</div>
      <div class="actions">
        @if (data()?.sprint) { <span class="btn ghost sm"><nx-icon name="clock" [size]="14" />{{ daysLeft() }}</span> }
        <button class="btn icon" (click)="star()" [attr.aria-label]="starred() ? 'Unstar' : 'Star'"><nx-icon name="star" [style.color]="starred() ? '#e8a317' : null" /></button>
        <button class="btn" [routerLink]="[]" [queryParams]="{ create: 'issue' }"><nx-icon name="plus" /> Create issue</button>
        @if (data()?.sprint) { <button class="btn primary" (click)="completing.set(true)">Complete sprint</button> }
      </div>
    </div>
    <div class="row wrap toolbar">
      <div class="search-input"><nx-icon name="search" /><input class="input" placeholder="Search board" [(ngModel)]="q" (ngModelChange)="load()" style="width:200px" /></div>
      <div class="people">@for (u of data()?.people ?? []; track u.id) {
        <button class="avatar-filter" [class.on]="assignee() === u.id" (click)="toggleAssignee(u.id)" [title]="u.name"><nx-avatar [user]="u" [size]="28" /></button> }</div>
      <select class="input" style="width:auto" [(ngModel)]="type" (ngModelChange)="load()"><option value="">Type</option>@for (t of types; track t) { <option [value]="t">{{ label(t) }}</option> }</select>
      @if (assignee() || q || type) { <button class="btn ghost sm" (click)="clear()">Clear filters</button> }
    </div>
    <div class="columns">
      @for (s of statuses; track s) {
        <section class="column" [class.over]="over() === s" (dragover)="$event.preventDefault(); over.set(s)" (dragleave)="over.set(null)" (drop)="drop($event, s)">
          <header><span>{{ label(s) }}</span><span class="count">{{ col(s).length }}</span>@if (s === 'DONE') { <nx-icon name="check" [size]="14" class="done-check" /> }</header>
          <div class="cards">
            @for (i of col(s); track i.key) {
              <nx-issue-card [issue]="i" draggable="true" (dragstart)="dragStart($event, i)" (dragend)="dragging.set(null)" [class.ghost]="dragging()?.key === i.key"
                (dragover)="$event.preventDefault(); overCard.set(i.key)" />
            }
            @if (s === 'TODO') { <a class="add" [routerLink]="[]" [queryParams]="{ create: 'issue' }"><nx-icon name="plus" [size]="14" /> Create issue</a> }
          </div>
        </section>
      }
    </div>
  </div>
  @if (completing()) {
    <nx-modal [title]="'Complete ' + data()?.sprint?.name" (closed)="completing.set(false)">
      <div class="modal-body">
        <p>This sprint contains <b>{{ col('DONE').length }}</b> completed and <b>{{ openCount() }}</b> open issue{{ openCount() === 1 ? '' : 's' }}.</p>
        @if (openCount()) {
          <div class="field"><label>Move open issues to</label>
            <select class="input" [(ngModel)]="moveTo"><option value="backlog">Backlog</option>
              @for (s of futureSprints(); track s.id) { <option [value]="s.id">{{ s.name }}</option> }</select></div>
        }
      </div>
      <div class="modal-foot"><button class="btn ghost" (click)="completing.set(false)">Cancel</button><button class="btn primary" (click)="complete()">Complete sprint</button></div>
    </nx-modal>
  }`,
})
export class Board {
  private trackApi = inject(TrackApi);
  private toasts = inject(Toasts);
  private hub = inject(Hub);
  private router = inject(Router);
  readonly key = input.required<string>();
  readonly data = signal<BoardView | null>(null);
  readonly assignee = signal<number | null>(null);
  readonly dragging = signal<IssueRow | null>(null);
  readonly over = signal<string | null>(null);
  readonly overCard = signal<string | null>(null);
  readonly completing = signal(false);
  readonly starred = signal(false);
  readonly futureSprints = signal<SprintView[]>([]);
  readonly statuses = STATUSES;
  readonly types = TYPES.filter(t => t !== 'EPIC');
  label = label;
  q = '';
  type = '';
  moveTo = 'backlog';

  readonly openCount = computed(() => STATUSES.filter(s => s !== 'DONE').reduce((n, s) => n + this.col(s).length, 0));
  readonly daysLeft = computed(() => {
    const end = this.data()?.sprint?.endDate;
    if (!end) return '';
    const d = Math.ceil((new Date(end).getTime() - Date.now()) / 86400000);
    return d > 0 ? `${d} day${d === 1 ? '' : 's'} left` : d === 0 ? 'Ends today' : `${-d} days over`;
  });

  constructor() {
    effect(() => { this.key(); this.trackApi.changed(); this.load(); });
    effect(() => { const k = this.key(); this.hub.isStarred(`/track/${k}/board`).then(s => this.starred.set(s)); });
  }

  col(s: string) { return this.data()?.columns[s] ?? []; }

  async load() {
    this.data.set(await this.trackApi.board(this.key(), { q: this.q, assignee: this.assignee(), type: this.type }));
  }

  toggleAssignee(id: number) { this.assignee.set(this.assignee() === id ? null : id); this.load(); }
  clear() { this.q = ''; this.type = ''; this.assignee.set(null); this.load(); }

  dragStart(e: DragEvent, i: IssueRow) {
    this.dragging.set(i);
    e.dataTransfer?.setData('text/plain', i.key);
    if (e.dataTransfer) e.dataTransfer.effectAllowed = 'move';
  }

  async drop(e: DragEvent, status: string) {
    e.preventDefault();
    this.over.set(null);
    const i = this.dragging();
    this.dragging.set(null);
    if (!i) return;
    const target = this.col(status).filter(x => x.key !== i.key);
    const idx = this.overCard() ? target.findIndex(x => x.key === this.overCard()) : -1;
    const before = idx > 0 ? target[idx - 1].key : null;
    const after = idx >= 0 ? target[idx].key : null;
    // Optimistic: move the card now, then confirm with the server.
    const d = this.data()!;
    const cols = { ...d.columns };
    cols[i.status] = cols[i.status].filter(x => x.key !== i.key);
    const moved = { ...i, status };
    const list = [...target];
    list.splice(idx >= 0 ? idx : list.length, 0, moved);
    cols[status] = list;
    this.data.set({ ...d, columns: cols });
    this.overCard.set(null);
    try {
      await this.trackApi.move(i.key, { status, beforeKey: idx >= 0 ? before : target.at(-1)?.key ?? null, afterKey: after });
    } catch {
      this.toasts.error(`Could not move ${i.key}`);
      this.load();
    }
  }

  async star() {
    const p = this.data()?.project;
    if (!p) return;
    this.starred.set(await this.hub.toggleStar({ app: 'track', kind: 'project', title: p.name, link: `/track/${p.key}/board` }));
  }

  async complete() {
    const s = this.data()?.sprint;
    if (!s) return;
    await this.trackApi.completeSprint(s.id, this.moveTo);
    this.completing.set(false);
    this.toasts.info(`${s.name} completed`);
    this.router.navigate(['/track', this.key(), 'reports'], { queryParams: { sprint: s.id } });
  }

  ngOnInit() {
    this.trackApi.sprints(this.key()).then(s => this.futureSprints.set(s.filter(x => x.state === 'FUTURE')));
  }
}
