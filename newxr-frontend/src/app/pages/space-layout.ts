import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { Router, RouterOutlet } from '@angular/router';
import { Hub } from '../core/hub';
import { AppFrame } from '../shell/shell';
import { NavItem, Sidebar } from '../shell/sidebar';
import { Icon } from '../shared/ui';
import { PageTree } from './page-tree';
import { PagesApi, SpaceView, TreeNode } from './pages-api';

@Component({
  selector: 'nx-space-layout',
  imports: [RouterOutlet, AppFrame, Sidebar, PageTree, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [`.content{display:flex;align-items:center;justify-content:space-between;margin:14px 10px 4px;font:600 11px var(--font);letter-spacing:.05em;text-transform:uppercase;color:var(--text-subtle)}
    .content button{border:0;background:none;color:var(--text-muted);cursor:pointer;border-radius:4px;display:inline-flex;padding:2px} .content button:hover{background:var(--surface-hover)}`],
  template: `<nx-app-frame>
    <nx-sidebar [context]="{ title: space()?.name ?? key(), subtitle: space()?.type === 'PERSONAL' ? 'Personal space' : 'Team space', app: 'pages' }" [items]="items()">
      <div class="content"><span>Content</span><button (click)="newPage()" title="Create a page"><nx-icon name="plus" [size]="14" /></button></div>
      <nx-page-tree [nodes]="tree()" [space]="key()" />
    </nx-sidebar>
    <router-outlet />
  </nx-app-frame>`,
})
export class SpaceLayout {
  private pagesApi = inject(PagesApi);
  private hub = inject(Hub);
  private router = inject(Router);
  readonly key = input.required<string>();
  readonly space = signal<SpaceView | null>(null);
  readonly tree = signal<TreeNode[]>([]);
  readonly items = computed<NavItem[]>(() => [
    { label: 'Overview', icon: 'home', link: `/pages/${this.key()}`, exact: true },
    { label: 'Blog', icon: 'megaphone', link: `/pages/${this.key()}/blog` },
    { label: 'Templates', icon: 'grid', link: `/pages/${this.key()}/templates` },
    { label: 'Space settings', icon: 'settings', link: `/pages/${this.key()}/settings` },
  ]);
  constructor() {
    effect(() => {
      const k = this.key();
      this.pagesApi.treeChanged();
      this.pagesApi.overview(k).then(o => {
        this.space.set(o.space);
        this.tree.set(o.tree);
        this.hub.viewed({ app: 'pages', kind: 'space', key: o.space.key, title: o.space.name, subtitle: 'Pages · Space', link: `/pages/${o.space.key}` });
      }).catch(() => this.router.navigateByUrl('/pages'));
    });
  }
  async newPage() {
    const p = await this.pagesApi.create({ spaceKey: this.key(), title: 'Untitled', body: '' });
    this.pagesApi.touch();
    this.router.navigate(['/pages', this.key(), p.id, 'edit']);
  }
}
