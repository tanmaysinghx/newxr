import { ChangeDetectionStrategy, Component, effect, inject, input } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api } from '../core/api';
import { People } from '../core/people';
import { Toasts } from '../core/toasts';
import { TrackApi } from './track-api';

@Component({
  selector: 'nx-track-settings',
  imports: [FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="page narrow"><div class="title-bar"><h1>Project settings</h1></div>
    <div class="card col gap-12" style="max-width:600px">
      <div class="field"><label>Name</label><input class="input" [(ngModel)]="name" /></div>
      <div class="field"><label>Key</label><input class="input mono" [value]="key()" disabled /><span class="hint">Keys cannot change once issues use them.</span></div>
      <div class="field"><label>Description</label><textarea class="input" rows="3" [(ngModel)]="description"></textarea></div>
      <div class="field"><label>Project lead</label><select class="input" [(ngModel)]="leadId">@for (u of people.all(); track u.id) { <option [ngValue]="u.id">{{ u.name }}</option> }</select></div>
      <div><button class="btn primary" (click)="save()">Save</button></div>
    </div></div>`,
})
export class TrackSettings {
  private trackApi = inject(TrackApi);
  private api = inject(Api);
  private toasts = inject(Toasts);
  readonly people = inject(People);
  readonly key = input.required<string>();
  name = ''; description = ''; leadId: number | null = null;
  constructor() {
    this.people.load();
    effect(() => this.trackApi.project(this.key()).then(p => { this.name = p.name; this.description = p.description ?? ''; this.leadId = p.lead?.id ?? null; }));
  }
  async save() {
    if (await this.api.quiet(this.trackApi.updateProject(this.key(), { name: this.name, description: this.description, leadId: this.leadId }))) this.toasts.info('Project saved');
  }
}
