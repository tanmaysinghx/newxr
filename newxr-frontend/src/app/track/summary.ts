import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ago, label } from '../core/format';
import { Avatar, Icon, IssueType, Status } from '../shared/ui';
import { IssueRow, ProjectView, TrackApi } from './track-api';

@Component({
  selector: 'nx-track-summary',
  imports: [RouterLink, Avatar, Icon, IssueType, Status],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="page">
    <div class="crumbs"><a routerLink="/track">Projects</a><span class="sep">/</span><span>{{ project()?.name }}</span></div>
    <div class="title-bar"><div><h1>{{ project()?.name }}</h1><div class="sub">{{ project()?.description }}</div></div></div>
    <div class="grid grid-4">
      <div class="card"><div class="stat-label"><nx-icon name="check-circle" [size]="14" /> Done in the last 7 days</div><div class="stat-value">{{ stats().done7 }}</div></div>
      <div class="card"><div class="stat-label"><nx-icon name="edit" [size]="14" /> Updated in the last 7 days</div><div class="stat-value">{{ stats().updated7 }}</div></div>
      <div class="card"><div class="stat-label"><nx-icon name="calendar" [size]="14" /> Due in the next 7 days</div><div class="stat-value">{{ stats().due7 }}</div></div>
      <div class="card"><div class="stat-label"><nx-icon name="bolt" [size]="14" /> Active sprint</div><div class="stat-value" style="font-size:17px">{{ project()?.activeSprint ?? 'None' }}</div></div>
    </div>
    <div class="grid grid-2">
      <div class="card"><div class="card-head"><h3>Status overview</h3></div>
        @for (s of stats().byStatus; track s.k) { <div class="row" style="padding:6px 0"><nx-status [value]="s.k" /><span class="grow"></span><b>{{ s.n }}</b></div> }</div>
      <div class="card flush"><div class="card-head" style="padding:14px 16px 6px;margin:0"><h3>Recent activity</h3><a [routerLink]="['/track', key(), 'list']" class="small">View all</a></div>
        @for (i of recent(); track i.key) {
          <a class="list-row" [routerLink]="['/track/issue', i.key]"><nx-itype [type]="i.type" /><span class="key">{{ i.key }}</span><span class="grow truncate">{{ i.summary }}</span>
            <nx-avatar [user]="i.assignee" [size]="20" /><span class="small subtle nowrap">{{ ago(i.updatedAt) }}</span></a> }</div>
    </div>
  </div>`,
})
export class TrackSummary {
  private trackApi = inject(TrackApi);
  readonly key = input.required<string>();
  readonly project = signal<ProjectView | null>(null);
  readonly rows = signal<IssueRow[]>([]);
  ago = ago; label = label;
  readonly recent = computed(() => this.rows().slice(0, 8));
  readonly stats = computed(() => {
    const week = Date.now() - 7 * 86400000, next = Date.now() + 7 * 86400000;
    const r = this.rows();
    return {
      done7: r.filter(i => i.status === 'DONE' && new Date(i.updatedAt).getTime() > week).length,
      updated7: r.filter(i => new Date(i.updatedAt).getTime() > week).length,
      due7: r.filter(i => i.dueDate && i.status !== 'DONE' && new Date(i.dueDate).getTime() < next).length,
      byStatus: ['TODO', 'IN_PROGRESS', 'IN_REVIEW', 'DONE'].map(k => ({ k, n: r.filter(i => i.status === k).length })),
    };
  });
  constructor() {
    effect(() => {
      const k = this.key();
      this.trackApi.project(k).then(p => this.project.set(p));
      this.trackApi.issues(k).then(r => this.rows.set(r));
    });
  }
}
