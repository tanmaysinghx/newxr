import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ago, label } from '../core/format';
import { People } from '../core/people';
import { Avatar, Icon, IssueType, Priority, Status } from '../shared/ui';
import { IssueRow, STATUSES, TYPES, TrackApi } from './track-api';

@Component({
  selector: 'nx-issue-list',
  imports: [FormsModule, RouterLink, Avatar, Icon, IssueType, Priority, Status],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="page">
    <div class="crumbs"><a routerLink="/track">Projects</a><span class="sep">/</span><span>{{ key() }}</span></div>
    <div class="title-bar"><h1>List</h1>
      <div class="actions"><button class="btn" (click)="exportCsv()"><nx-icon name="download" /> Export</button>
        <button class="btn primary" [routerLink]="[]" [queryParams]="{ create: 'issue' }"><nx-icon name="plus" /> Create issue</button></div></div>
    <div class="row wrap">
      <div class="search-input"><nx-icon name="search" /><input class="input" placeholder="Search list" [(ngModel)]="q" (ngModelChange)="load()" style="width:200px" /></div>
      <select class="input" style="width:auto" [(ngModel)]="type" (ngModelChange)="load()"><option value="">Type</option>@for (t of types; track t) { <option [value]="t">{{ label(t) }}</option> }</select>
      <select class="input" style="width:auto" [(ngModel)]="status" (ngModelChange)="load()"><option value="">Status</option>
        <option value="TODO,IN_PROGRESS,IN_REVIEW">Not done</option>@for (s of statuses; track s) { <option [value]="s">{{ label(s) }}</option> }</select>
      <select class="input" style="width:auto" [(ngModel)]="assignee" (ngModelChange)="load()"><option [ngValue]="null">Assignee</option><option [ngValue]="0">Unassigned</option>
        @for (u of people.all(); track u.id) { <option [ngValue]="u.id">{{ u.name }}</option> }</select>
      <span class="grow"></span>
      <select class="input" style="width:auto" [(ngModel)]="sort" (ngModelChange)="load()"><option value="">Sort: Updated</option><option value="priority">Sort: Priority</option><option value="key">Sort: Key</option><option value="status">Sort: Status</option></select>
    </div>
    <div class="query mono small">project = {{ key() }}{{ type ? ' AND type = ' + type : '' }}{{ status ? ' AND status IN (' + status + ')' : '' }}{{ q ? ' AND text ~ "' + q + '"' : '' }} ORDER BY {{ sort || 'updated' }} DESC</div>
    <div class="card flush"><div class="table-wrap"><table class="table">
      <thead><tr><th style="width:28px"><input type="checkbox" [checked]="allSelected()" (change)="toggleAll()" aria-label="Select all" /></th><th>Key</th><th>Summary</th><th>Status</th><th>Assignee</th><th>Priority</th><th>Points</th><th>Labels</th><th>Updated</th></tr></thead>
      <tbody>@for (i of page(); track i.key) {
        <tr class="clickable" [class.selected]="selected().has(i.key)" (click)="open(i.key)">
          <td (click)="$event.stopPropagation()"><input type="checkbox" [checked]="selected().has(i.key)" (change)="toggle(i.key)" /></td>
          <td><div class="row nowrap"><nx-itype [type]="i.type" /><a [routerLink]="['/track/issue', i.key]">{{ i.key }}</a></div></td>
          <td class="truncate" style="max-width:360px">{{ i.summary }}</td><td><nx-status [value]="i.status" /></td>
          <td><div class="row nowrap"><nx-avatar [user]="i.assignee" [size]="20" /><span [class.subtle]="!i.assignee">{{ i.assignee?.name ?? 'Unassigned' }}</span></div></td>
          <td><nx-priority [value]="i.priority" [showLabel]="true" /></td><td>{{ i.points ?? '' }}</td>
          <td><div class="row gap-4">@for (l of i.labels; track l) { <span class="chip">{{ l }}</span> }</div></td>
          <td class="muted nowrap">{{ ago(i.updatedAt) }}</td></tr>
      }</tbody></table></div>
      <div class="table-foot"><span>{{ rows().length ? (pageIdx() * size + 1) + '–' + min((pageIdx() + 1) * size, rows().length) + ' of ' + rows().length + ' issues' : 'No issues' }}</span>
        <div class="row"><button class="btn sm icon" [disabled]="pageIdx() === 0" (click)="pageIdx.set(pageIdx() - 1)"><nx-icon name="chevron-left" /></button>
          <button class="btn sm icon" [disabled]="(pageIdx() + 1) * size >= rows().length" (click)="pageIdx.set(pageIdx() + 1)"><nx-icon name="chevron-right" /></button></div></div>
    </div></div>`,
  styles: ['.query{background:var(--surface);border:1px solid var(--border);border-radius:8px;padding:8px 12px;color:var(--text-muted)}'],
})
export class IssueList {
  private trackApi = inject(TrackApi);
  private router = inject(Router);
  readonly people = inject(People);
  readonly key = input.required<string>();
  readonly rows = signal<IssueRow[]>([]);
  readonly selected = signal(new Set<string>());
  readonly pageIdx = signal(0);
  readonly size = 25;
  readonly types = TYPES; readonly statuses = STATUSES;
  readonly page = computed(() => this.rows().slice(this.pageIdx() * this.size, (this.pageIdx() + 1) * this.size));
  readonly allSelected = computed(() => this.page().length > 0 && this.page().every(i => this.selected().has(i.key)));
  label = label; ago = ago; min = Math.min;
  q = ''; type = ''; status = ''; sort = ''; assignee: number | null = null;

  constructor() { this.people.load(); effect(() => { this.key(); this.trackApi.changed(); this.load(); }); }
  async load() {
    this.rows.set(await this.trackApi.issues(this.key(), { q: this.q, type: this.type, status: this.status, sort: this.sort, assignee: this.assignee }));
    this.pageIdx.set(0);
  }
  open(k: string) { this.router.navigate(['/track/issue', k]); }
  toggle(k: string) { const s = new Set(this.selected()); s.has(k) ? s.delete(k) : s.add(k); this.selected.set(s); }
  toggleAll() { const s = new Set(this.selected()); const all = this.allSelected(); this.page().forEach(i => all ? s.delete(i.key) : s.add(i.key)); this.selected.set(s); }

  exportCsv() {
    const pick = this.selected().size ? this.rows().filter(r => this.selected().has(r.key)) : this.rows();
    const esc = (v: unknown) => `"${String(v ?? '').replace(/"/g, '""')}"`;
    const lines = [['Key', 'Type', 'Summary', 'Status', 'Assignee', 'Priority', 'Points', 'Labels', 'Updated'].join(',')]
      .concat(pick.map(r => [r.key, r.type, r.summary, r.status, r.assignee?.name, r.priority, r.points, r.labels.join(' '), r.updatedAt].map(esc).join(',')));
    const a = document.createElement('a');
    a.href = URL.createObjectURL(new Blob([lines.join('\n')], { type: 'text/csv' }));
    a.download = `${this.key()}-issues.csv`;
    a.click();
    URL.revokeObjectURL(a.href);
  }
}
