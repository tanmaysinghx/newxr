import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterOutlet } from '@angular/router';
import { Hub } from '../core/hub';
import { AppFrame } from '../shell/shell';
import { NavItem, Sidebar } from '../shell/sidebar';
import { CreateIssue } from './create-issue';
import { ProjectView, TrackApi } from './track-api';

/** /track/:key/* — project sidebar + the create-issue modal (?create=issue). */
@Component({
  selector: 'nx-track-layout',
  imports: [RouterOutlet, Sidebar, AppFrame, CreateIssue],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<nx-app-frame>
      <nx-sidebar [context]="{ title: project()?.name ?? key(), subtitle: 'Software project', app: 'track' }" [items]="items()" />
      <router-outlet />
    </nx-app-frame>
    @if (creating()) { <nx-create-issue [projectKey]="key()" (closed)="closeCreate()" /> }`,
})
export class TrackLayout {
  private trackApi = inject(TrackApi);
  private hub = inject(Hub);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  readonly key = input.required<string>();
  readonly project = signal<ProjectView | null>(null);
  readonly creating = signal(false);
  readonly items = computed<NavItem[]>(() => {
    const k = this.key();
    return [
      { label: 'Summary', icon: 'home', link: `/track/${k}/summary` },
      { label: 'Timeline', icon: 'timeline', link: `/track/${k}/timeline` },
      { label: 'Backlog', icon: 'backlog', link: `/track/${k}/backlog` },
      { label: 'Board', icon: 'board', link: `/track/${k}/board` },
      { label: 'List', icon: 'list', link: `/track/${k}/list` },
      { label: 'Reports', icon: 'chart', link: `/track/${k}/reports` },
      { label: 'Project settings', icon: 'settings', link: `/track/${k}/settings` },
    ];
  });

  constructor() {
    effect(() => {
      const k = this.key();
      this.trackApi.project(k).then(p => {
        this.project.set(p);
        this.hub.viewed({ app: 'track', kind: 'project', key: p.key, title: p.name, subtitle: 'Track · Software project', link: `/track/${p.key}/board` });
      }).catch(() => this.router.navigateByUrl('/track'));
    });
    this.route.queryParamMap.subscribe(q => this.creating.set(q.get('create') === 'issue'));
  }

  closeCreate() { this.router.navigate([], { queryParams: { create: null }, queryParamsHandling: 'merge' }); }
}
