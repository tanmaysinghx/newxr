import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiError } from '../core/api';
import { label, shortDate } from '../core/format';
import { Toasts } from '../core/toasts';
import { Avatar, Icon, IssueType, Modal, Priority, Status } from '../shared/ui';
import { BacklogView, IssueRow, SprintView, TYPES, TrackApi } from './track-api';

@Component({
  selector: 'nx-backlog',
  imports: [FormsModule, NgTemplateOutlet, RouterLink, Avatar, Icon, IssueType, Priority, Status, Modal],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './backlog.scss',
  template: `<div class="page">
    <div class="crumbs"><a routerLink="/track">Projects</a><span class="sep">/</span><span>{{ data()?.project?.name }}</span></div>
    <div class="title-bar"><h1>Backlog</h1>
      <div class="actions"><button class="btn" [routerLink]="[]" [queryParams]="{ create: 'issue' }"><nx-icon name="plus" /> Create issue</button></div></div>
    <div class="row wrap">
      <div class="search-input"><nx-icon name="search" /><input class="input" placeholder="Search backlog" [(ngModel)]="q" (ngModelChange)="load()" style="width:200px" /></div>
      <select class="input" style="width:auto" [(ngModel)]="epic" (ngModelChange)="load()"><option value="">Epic</option>
        @for (e of data()?.epics ?? []; track e.key) { <option [value]="e.key">{{ e.summary }}</option> }</select>
      <select class="input" style="width:auto" [(ngModel)]="type" (ngModelChange)="load()"><option value="">Type</option>
        @for (t of types; track t) { <option [value]="t">{{ label(t) }}</option> }</select>
    </div>

    @for (b of data()?.sprints ?? []; track b.sprint.id) {
      <section class="block" (dragover)="$event.preventDefault(); over.set(b.sprint.id)" (dragleave)="over.set(null)" (drop)="drop($event, b.sprint.id)" [class.over]="over() === b.sprint.id">
        <header>
          <button class="btn ghost sm icon" (click)="toggle(b.sprint.id)"><nx-icon [name]="collapsed().has(b.sprint.id) ? 'chevron-right' : 'chevron-down'" /></button>
          <span class="strong">{{ b.sprint.name }}</span>
          <span class="small muted">{{ dates(b.sprint) }} · {{ b.issues.length }} issue{{ b.issues.length === 1 ? '' : 's' }}</span>
          @if (b.sprint.state === 'ACTIVE') { <nx-status value="IN_PROGRESS" /> }
          <span class="grow"></span>
          <span class="pts todo" title="To do points">{{ pts(b.issues, 'TODO') }}</span><span class="pts prog" title="In progress points">{{ pts(b.issues, 'IN_PROGRESS') + pts(b.issues, 'IN_REVIEW') }}</span><span class="pts done" title="Done points">{{ pts(b.issues, 'DONE') }}</span>
          @if (b.sprint.state === 'ACTIVE') { <a class="btn sm" [routerLink]="['/track', key(), 'board']">Complete sprint</a> }
          @else { <button class="btn sm" (click)="openStart(b.sprint)" [disabled]="!b.issues.length">Start sprint</button>
            <button class="btn ghost sm icon" (click)="deleteSprint(b.sprint)" title="Delete sprint"><nx-icon name="x" [size]="14" /></button> }
        </header>
        @if (!collapsed().has(b.sprint.id)) {
          @for (i of b.issues; track i.key) { <ng-container *ngTemplateOutlet="row; context: { $implicit: i }" /> }
          @empty { <div class="drop-hint">Plan a sprint by dragging issues here.</div> }
          <a class="add" [routerLink]="[]" [queryParams]="{ create: 'issue' }"><nx-icon name="plus" [size]="14" /> Create issue</a>
        }
      </section>
    }

    <section class="block" (dragover)="$event.preventDefault(); over.set(0)" (dragleave)="over.set(null)" (drop)="drop($event, null)" [class.over]="over() === 0">
      <header><span class="strong">Backlog</span><span class="small muted">{{ data()?.backlog?.length ?? 0 }} issues</span><span class="grow"></span>
        <button class="btn sm" (click)="createSprint()">Create sprint</button></header>
      @for (i of data()?.backlog ?? []; track i.key) { <ng-container *ngTemplateOutlet="row; context: { $implicit: i }" /> }
      @empty { <div class="drop-hint">Your backlog is empty.</div> }
      <a class="add" [routerLink]="[]" [queryParams]="{ create: 'issue' }"><nx-icon name="plus" [size]="14" /> Create issue</a>
    </section>
  </div>

  <ng-template #row let-i>
    <div class="issue" draggable="true" (dragstart)="dragging.set(i)" (dragend)="dragging.set(null)" (dragover)="overKey.set(i.key)" [class.ghost]="dragging()?.key === i.key">
      <nx-icon name="drag" [size]="14" class="grip" /><nx-itype [type]="i.type" />
      <a class="key" [routerLink]="['/track/issue', i.key]">{{ i.key }}</a>
      <a class="grow truncate summary" [routerLink]="['/track/issue', i.key]">{{ i.summary }}</a>
      @if (i.epicSummary) { <span class="epic truncate">{{ i.epicSummary }}</span> }
      @for (l of i.labels; track l) { <span class="chip hide-sm">{{ l }}</span> }
      <nx-status [value]="i.status" /><nx-priority [value]="i.priority" />
      <span class="pts">{{ i.points ?? '–' }}</span><nx-avatar [user]="i.assignee" [size]="22" />
    </div>
  </ng-template>

  @if (starting(); as s) {
    <nx-modal [title]="'Start ' + s.name" (closed)="starting.set(null)">
      <div class="modal-body">
        @if (error()) { <div class="banner danger"><nx-icon name="alert" /><div>{{ error() }}</div></div> }
        <div class="field"><label>Sprint name</label><input class="input" [(ngModel)]="startForm.name" /></div>
        <div class="form-grid"><div class="field"><label>Start date</label><input class="input" type="date" [(ngModel)]="startForm.startDate" /></div>
          <div class="field"><label>End date</label><input class="input" type="date" [(ngModel)]="startForm.endDate" /></div></div>
        <div class="field"><label>Sprint goal</label><textarea class="input" rows="2" [(ngModel)]="startForm.goal"></textarea></div>
      </div>
      <div class="modal-foot"><button class="btn ghost" (click)="starting.set(null)">Cancel</button><button class="btn primary" (click)="start()">Start</button></div>
    </nx-modal>
  }`,
})
export class Backlog {
  private trackApi = inject(TrackApi);
  private toasts = inject(Toasts);
  readonly key = input.required<string>();
  readonly data = signal<BacklogView | null>(null);
  readonly dragging = signal<IssueRow | null>(null);
  readonly over = signal<number | null>(null);
  readonly overKey = signal<string | null>(null);
  readonly collapsed = signal(new Set<number>());
  readonly starting = signal<SprintView | null>(null);
  readonly error = signal<string | null>(null);
  readonly types = TYPES.filter(t => t !== 'EPIC');
  label = label;
  q = '';
  epic = '';
  type = '';
  startForm = { name: '', goal: '', startDate: '', endDate: '' };

  constructor() { effect(() => { this.key(); this.trackApi.changed(); this.load(); }); }

  async load() { this.data.set(await this.trackApi.backlog(this.key(), { q: this.q, epic: this.epic, type: this.type })); }

  pts(list: IssueRow[], status: string) { return list.filter(i => i.status === status).reduce((n, i) => n + (i.points ?? 0), 0); }
  dates(s: SprintView) { return s.startDate ? `${shortDate(s.startDate)} – ${shortDate(s.endDate)}` : 'Not started'; }
  toggle(id: number) { const c = new Set(this.collapsed()); c.has(id) ? c.delete(id) : c.add(id); this.collapsed.set(c); }

  async drop(e: DragEvent, sprintId: number | null) {
    e.preventDefault();
    this.over.set(null);
    const i = this.dragging();
    this.dragging.set(null);
    if (!i) return;
    const list = sprintId == null ? this.data()!.backlog : this.data()!.sprints.find(b => b.sprint.id === sprintId)!.issues;
    const others = list.filter(x => x.key !== i.key);
    const idx = others.findIndex(x => x.key === this.overKey());
    await this.trackApi.move(i.key, { sprintId, toBacklog: sprintId == null,
      beforeKey: idx > 0 ? others[idx - 1].key : idx === -1 ? others.at(-1)?.key ?? null : null, afterKey: idx >= 0 ? others[idx].key : null });
    this.overKey.set(null);
    this.load();
  }

  openStart(s: SprintView) {
    const today = new Date();
    const end = new Date(today.getTime() + 14 * 86400000);
    this.startForm = { name: s.name, goal: s.goal ?? '', startDate: today.toISOString().slice(0, 10), endDate: end.toISOString().slice(0, 10) };
    this.error.set(null);
    this.starting.set(s);
  }

  async start() {
    try {
      await this.trackApi.startSprint(this.starting()!.id, this.startForm);
      this.toasts.info(`${this.startForm.name} started`);
      this.starting.set(null);
      this.load();
    } catch (e) { this.error.set((e as ApiError).message); }
  }

  async createSprint() { await this.trackApi.createSprint(this.key()); this.load(); }

  async deleteSprint(s: SprintView) {
    await this.trackApi.deleteSprint(s.id);
    this.toasts.info(`${s.name} deleted; its issues moved to the backlog`);
    this.load();
  }
}
