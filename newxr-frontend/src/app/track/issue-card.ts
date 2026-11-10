import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Avatar, Icon, IssueType, Priority } from '../shared/ui';
import { IssueRow } from './track-api';

@Component({
  selector: 'nx-issue-card',
  imports: [RouterLink, Avatar, IssueType, Priority, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [`:host { display:block; background:var(--surface); border:1px solid var(--border); border-radius:8px; padding:10px 12px; box-shadow:var(--shadow-1); cursor:grab; }
    :host(:hover) { border-color: var(--border-strong); }
    .title { color: var(--text); font-size: 13px; line-height: 1.4; display: block; text-decoration: none !important; }
    .title:hover { color: var(--link); }
    .meta { display:flex; align-items:center; gap:6px; margin-top:8px; }
    .labels { display:flex; flex-wrap:wrap; gap:4px; margin-top:6px; }
    .epic { font: 600 10.5px var(--font); color: var(--ai); background: var(--ai-soft); padding: 1px 6px; border-radius: 4px; max-width: 100%; }
    .pts { min-width:20px; height:18px; border-radius:9px; background:var(--neutral-soft); font:600 11px var(--font); display:inline-flex; align-items:center; justify-content:center; padding:0 5px; color:var(--text-muted); }`],
  template: `<a class="title" [routerLink]="['/track/issue', issue().key]" draggable="false">{{ issue().summary }}</a>
    @if (issue().epicSummary || issue().labels.length) {
      <div class="labels">
        @if (issue().epicSummary) { <span class="epic truncate">{{ issue().epicSummary }}</span> }
        @for (l of issue().labels; track l) { <span class="chip">{{ l }}</span> }
      </div>
    }
    <div class="meta">
      <nx-itype [type]="issue().type" /><span class="key grow" [style.text-decoration]="issue().status === 'DONE' ? 'line-through' : 'none'">{{ issue().key }}</span>
      @if (issue().childCount) { <span class="tiny subtle row gap-4"><nx-icon name="list" [size]="12" />{{ issue().childDone }}/{{ issue().childCount }}</span> }
      <nx-priority [value]="issue().priority" />
      @if (issue().points != null) { <span class="pts">{{ issue().points }}</span> }
      <nx-avatar [user]="issue().assignee" [size]="22" />
    </div>`,
})
export class IssueCard {
  readonly issue = input.required<IssueRow>();
}
