import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AppTile, Icon } from '../shared/ui';

export interface NavItem { label: string; icon: string; link: string | unknown[]; exact?: boolean; count?: number | null; }
export interface NavGroup { title: string; items: NavItem[]; }
export interface SideContext { title: string; subtitle?: string; app?: string; icon?: string; color?: string; initials?: string; }

@Component({
  selector: 'nx-sidebar',
  imports: [RouterLink, RouterLinkActive, Icon, AppTile],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './sidebar.scss',
  template: `
    <div class="context">
      @if (context().app) { <nx-app-tile [app]="context().app!" [size]="32" /> }
      @else if (context().initials) { <span class="ctx-tile" [style.background]="context().color ?? 'var(--text)'">{{ context().initials }}</span> }
      @else { <span class="ctx-tile" [style.background]="context().color ?? 'var(--text)'"><nx-icon [name]="context().icon ?? 'shield'" [size]="18" /></span> }
      <div class="ctx-text"><div class="ctx-title truncate">{{ context().title }}</div>
        @if (context().subtitle) { <div class="ctx-sub truncate">{{ context().subtitle }}</div> }</div>
    </div>
    <nav>
      @for (item of items(); track item.label) {
        <a class="nav-item" [routerLink]="item.link" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: item.exact ?? false }">
          <nx-icon [name]="item.icon" [size]="16" /><span class="grow truncate">{{ item.label }}</span>
          @if (item.count != null) { <span class="count">{{ item.count }}</span> }
        </a>
      }
      @for (g of groups(); track g.title) {
        <div class="group-title">{{ g.title }}</div>
        @for (item of g.items; track item.label) {
          <a class="nav-item sub" [routerLink]="item.link" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: item.exact ?? true }">
            <nx-icon [name]="item.icon" [size]="15" /><span class="grow truncate">{{ item.label }}</span>
            @if (item.count != null) { <span class="small subtle">{{ item.count }}</span> }
          </a>
        }
      }
      <ng-content />
    </nav>`,
})
export class Sidebar {
  readonly context = input.required<SideContext>();
  readonly items = input<NavItem[]>([]);
  readonly groups = input<NavGroup[]>([]);
}
