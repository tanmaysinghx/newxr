import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ApiError } from '../core/api';
import { AppFrame } from '../shell/shell';
import { Sidebar } from '../shell/sidebar';
import { Avatar, Empty, Icon, Modal } from '../shared/ui';
import { CreateIssue } from './create-issue';
import { ProjectView, TrackApi } from './track-api';

/** /track — project directory. */
@Component({
  selector: 'nx-track-projects',
  imports: [FormsModule, RouterLink, AppFrame, Sidebar, Avatar, Icon, Modal, Empty, CreateIssue],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<nx-app-frame>
    <nx-sidebar [context]="{ title: 'Newxr Track', subtitle: 'Projects', app: 'track' }"
      [items]="[{ label: 'Projects', icon: 'folder', link: '/track', exact: true }]"
      [groups]="[{ title: 'Recent projects', items: recentNav() }]" />
    <div class="page">
      <div class="title-bar"><div><h1>Projects</h1><div class="sub">Plan, track and ship work.</div></div>
        <div class="actions"><button class="btn primary" (click)="creating.set(true)"><nx-icon name="plus" /> Create project</button></div></div>
      <div class="card flush"><div class="table-wrap"><table class="table">
        <thead><tr><th>Name</th><th>Key</th><th>Type</th><th>Lead</th><th>Issues</th><th>Active sprint</th></tr></thead>
        <tbody>@for (p of projects(); track p.key) {
          <tr class="clickable" [routerLink]="['/track', p.key, 'board']">
            <td><div class="row"><span class="tile" [style.background]="p.color">{{ p.key.slice(0, 2) }}</span><a [routerLink]="['/track', p.key, 'board']" class="strong">{{ p.name }}</a></div></td>
            <td class="mono">{{ p.key }}</td><td class="muted">Software</td>
            <td><div class="row"><nx-avatar [user]="p.lead" [size]="22" />{{ p.lead?.name }}</div></td>
            <td>{{ p.issueCount }}</td><td class="muted">{{ p.activeSprint ?? '—' }}</td></tr>
        }</tbody></table></div>
        @if (!projects().length) { <nx-empty icon="board" title="No projects yet" text="Create a project to start planning work." /> }
      </div>
    </div>
  </nx-app-frame>
  @if (creating()) {
    <nx-modal title="Create project" (closed)="creating.set(false)">
      <div class="modal-body">
        @if (error()) { <div class="banner danger"><nx-icon name="alert" /><div>{{ error() }}</div></div> }
        <div class="field"><label>Name <span class="req">*</span></label><input class="input" [(ngModel)]="name" (ngModelChange)="suggestKey()" placeholder="Orbit Mobile" /></div>
        <div class="field"><label>Key <span class="req">*</span></label><input class="input mono" [(ngModel)]="key" (ngModelChange)="keyTouched = true" maxlength="10" />
          <span class="hint">Prefixes every issue: {{ key || 'KEY' }}-1, {{ key || 'KEY' }}-2…</span></div>
        <div class="field"><label>Description</label><textarea class="input" rows="3" [(ngModel)]="description"></textarea></div>
      </div>
      <div class="modal-foot"><button class="btn ghost" (click)="creating.set(false)">Cancel</button><button class="btn primary" (click)="create()">Create</button></div>
    </nx-modal>
  }
  @if (creatingIssue()) { <nx-create-issue (closed)="router.navigate([], { queryParams: {} })" /> }`,
  styles: ['.tile{width:24px;height:24px;border-radius:6px;color:#fff;font:700 10px var(--font);display:inline-flex;align-items:center;justify-content:center}'],
})
export class TrackProjects {
  private trackApi = inject(TrackApi);
  readonly router = inject(Router);
  private route = inject(ActivatedRoute);
  readonly projects = signal<ProjectView[]>([]);
  readonly creating = signal(false);
  readonly creatingIssue = signal(false);
  readonly error = signal<string | null>(null);
  readonly recentNav = signal<{ label: string; icon: string; link: string }[]>([]);
  name = '';
  key = '';
  description = '';
  keyTouched = false;

  constructor() {
    this.load();
    this.route.queryParamMap.subscribe(q => this.creatingIssue.set(q.get('create') === 'issue'));
  }

  async load() {
    const ps = await this.trackApi.projects();
    this.projects.set(ps);
    this.recentNav.set(ps.slice(0, 6).map(p => ({ label: p.name, icon: 'board', link: `/track/${p.key}/board` })));
  }

  suggestKey() {
    if (this.keyTouched) return;
    const words = this.name.trim().split(/\s+/).filter(Boolean);
    this.key = (words.length > 1 ? words.map(w => w[0]).join('') : this.name.slice(0, 3)).toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 10);
  }

  async create() {
    this.error.set(null);
    try {
      const p = await this.trackApi.createProject({ name: this.name, key: this.key, description: this.description });
      this.creating.set(false);
      this.router.navigate(['/track', p.key, 'board']);
    } catch (e) { this.error.set((e as ApiError).message); }
  }
}
