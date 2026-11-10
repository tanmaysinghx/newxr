import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ago } from '../core/format';
import { WorkItem } from '../core/hub';
import { AppTile, IssueType, Priority, Status } from '../shared/ui';

/** A work item row: type/app icon, key, title, priority, status, context. */
@Component({
  selector: 'nx-work-row',
  imports: [RouterLink, AppTile, IssueType, Priority, Status],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [':host{display:block} .list-row{gap:10px} .key{width:72px;flex:none} .ctx{width:120px;flex:none;text-align:right} @media (max-width:700px){.ctx,.key{display:none}}'],
  template: `<a class="list-row" [routerLink]="item().link">
    @if (isIssue()) { <nx-itype [type]="item().kind" /> } @else { <nx-app-tile [app]="item().app" [size]="18" /> }
    <span class="key">{{ item().key }}</span>
    <span class="grow truncate">{{ item().title }}</span>
    @if (item().priority && isIssue()) { <nx-priority [value]="item().priority" /> }
    @if (item().status) { <nx-status [value]="item().status" /> }
    <span class="ctx small subtle truncate">{{ context() === 'time' ? ago(item().updatedAt) : item().subtitle }}</span>
  </a>`,
})
export class WorkRow {
  readonly item = input.required<WorkItem>();
  readonly context = input<'subtitle' | 'time'>('subtitle');
  ago = ago;
  isIssue() { return ['STORY', 'BUG', 'TASK', 'EPIC', 'SUBTASK'].includes(this.item().kind); }
}
