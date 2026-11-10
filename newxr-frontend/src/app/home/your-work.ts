import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { Auth } from '../core/auth';
import { AppFrame } from '../shell/shell';
import { NavItem, Sidebar } from '../shell/sidebar';

@Component({
  selector: 'nx-your-work',
  imports: [RouterOutlet, Sidebar, AppFrame],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<nx-app-frame><nx-sidebar [context]="ctx()" [items]="items" /><router-outlet /></nx-app-frame>`,
})
export class YourWork {
  private auth = inject(Auth);
  readonly ctx = computed(() => ({ title: 'Your work', initials: this.auth.me()?.initials, color: this.auth.me()?.color }));
  readonly items: NavItem[] = [
    { label: 'For you', icon: 'home', link: '/', exact: true },
    { label: 'Recent', icon: 'clock', link: '/recent' },
    { label: 'Starred', icon: 'star', link: '/starred' },
    { label: 'Apps', icon: 'grid', link: '/apps' },
    { label: 'People', icon: 'users', link: '/people' },
    { label: 'Search', icon: 'search', link: '/search' },
  ];
}
