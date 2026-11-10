import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiError } from '../core/api';
import { label } from '../core/format';
import { People } from '../core/people';
import { Toasts } from '../core/toasts';
import { Icon, Modal } from '../shared/ui';
import { PRIORITIES, ProjectView, SprintView, TYPES, TrackApi } from './track-api';

@Component({
  selector: 'nx-create-issue',
  imports: [FormsModule, Modal, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<nx-modal title="Create issue" [wide]="true" (closed)="closed.emit()">
    <div class="modal-body">
      <div class="small muted">Required fields are marked with an asterisk <span class="req">*</span></div>
      @if (error()) { <div class="banner danger"><nx-icon name="alert" /><div>{{ error() }}</div></div> }
      <div class="form-grid">
        <div class="field"><label>Project <span class="req">*</span></label>
          <select class="input" [(ngModel)]="form.projectKey" (ngModelChange)="loadSprints()">
            @for (p of projects(); track p.key) { <option [value]="p.key">{{ p.name }} ({{ p.key }})</option> }</select></div>
        <div class="field"><label>Issue type <span class="req">*</span></label>
          <select class="input" [(ngModel)]="form.type">@for (t of types; track t) { <option [value]="t">{{ label(t) }}</option> }</select></div>
        <div class="field full"><label>Summary <span class="req">*</span></label>
          <input class="input" [(ngModel)]="form.summary" placeholder="What needs to be done?" autofocus (keydown.enter)="submit()" /></div>
        <div class="field full"><label>Description</label>
          <textarea class="input" rows="5" [(ngModel)]="form.description" placeholder="Describe the work, add context. Markdown supported."></textarea></div>
        <div class="field"><label>Assignee</label>
          <select class="input" [(ngModel)]="form.assigneeId"><option [ngValue]="null">Unassigned</option>
            @for (u of people.all(); track u.id) { <option [ngValue]="u.id">{{ u.name }}</option> }</select></div>
        <div class="field"><label>Priority</label>
          <select class="input" [(ngModel)]="form.priority">@for (p of priorities; track p) { <option [value]="p">{{ label(p) }}</option> }</select></div>
        <div class="field"><label>Sprint</label>
          <select class="input" [(ngModel)]="form.sprintId"><option [ngValue]="null">Backlog</option>
            @for (s of sprints(); track s.id) { <option [ngValue]="s.id">{{ s.name }}{{ s.state === 'ACTIVE' ? ' (active)' : '' }}</option> }</select></div>
        <div class="field"><label>Story points</label><input class="input" type="number" min="0" [(ngModel)]="form.points" /></div>
        <div class="field"><label>Labels</label><input class="input" [(ngModel)]="labels" placeholder="mobile, sync" /></div>
        <div class="field"><label>Due date</label><input class="input" type="date" [(ngModel)]="form.dueDate" /></div>
        @if (form.type === 'SUBTASK' || form.type !== 'EPIC') {
          <div class="field"><label>{{ form.type === 'SUBTASK' ? 'Parent issue' : 'Epic' }}</label>
            <input class="input" [(ngModel)]="form.parentKey" [placeholder]="form.type === 'SUBTASK' ? 'ORB-412' : 'ORB-380'" /></div>
        }
      </div>
    </div>
    <div class="modal-foot">
      <label class="check small" style="margin-right:auto"><input type="checkbox" [(ngModel)]="another" /> Create another</label>
      <button class="btn ghost" (click)="closed.emit()">Cancel</button>
      <button class="btn primary" (click)="submit()" [disabled]="busy()">Create</button>
    </div>
  </nx-modal>`,
})
export class CreateIssue {
  private trackApi = inject(TrackApi);
  private router = inject(Router);
  private toasts = inject(Toasts);
  readonly people = inject(People);
  readonly projectKey = input<string | null>(null);
  readonly defaults = input<{ sprintId?: number | null; status?: string; parentKey?: string; type?: string }>({});
  readonly closed = output<void>();
  readonly projects = signal<ProjectView[]>([]);
  readonly sprints = signal<SprintView[]>([]);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  readonly types = TYPES;
  readonly priorities = PRIORITIES;
  label = label;
  another = false;
  labels = '';
  form = { projectKey: '', type: 'STORY', summary: '', description: '', assigneeId: null as number | null, priority: 'MEDIUM',
    sprintId: null as number | null, points: null as number | null, dueDate: '', parentKey: '' };

  constructor() {
    this.people.load();
    queueMicrotask(async () => {
      const d = this.defaults();
      if (d.type) this.form.type = d.type;
      if (d.parentKey) this.form.parentKey = d.parentKey;
      const ps = await this.trackApi.projects();
      this.projects.set(ps);
      this.form.projectKey = this.projectKey() ?? ps[0]?.key ?? '';
      await this.loadSprints();
      if (d.sprintId !== undefined) this.form.sprintId = d.sprintId;
      else this.form.sprintId = this.sprints().find(s => s.state === 'ACTIVE')?.id ?? null;
    });
  }

  async loadSprints() {
    if (!this.form.projectKey) return;
    this.sprints.set((await this.trackApi.sprints(this.form.projectKey)).filter(s => s.state !== 'CLOSED'));
  }

  async submit() {
    if (!this.form.summary.trim()) { this.error.set('Enter a summary.'); return; }
    this.busy.set(true);
    this.error.set(null);
    try {
      const created = await this.trackApi.create({ ...this.form, dueDate: this.form.dueDate || null, parentKey: this.form.parentKey || null,
        labels: this.labels.split(',').map(s => s.trim()).filter(Boolean) });
      this.trackApi.touch();
      this.toasts.info(`${created.key} created`);
      if (this.another) { this.form.summary = ''; this.form.description = ''; }
      else { this.closed.emit(); if (!this.projectKey()) this.router.navigate(['/track/issue', created.key]); }
    } catch (e) { this.error.set((e as ApiError).message); }
    finally { this.busy.set(false); }
  }
}
