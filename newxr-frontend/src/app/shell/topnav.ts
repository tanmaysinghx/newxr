import { ChangeDetectionStrategy, Component, ElementRef, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { filter } from 'rxjs';
import { Api } from '../core/api';
import { Auth } from '../core/auth';
import { APPS } from '../core/models';
import { Theme } from '../core/theme';
import { AppTile, Avatar, Icon, Logo } from '../shared/ui';
import { NotificationsPanel } from './notifications-panel';

type Menu = 'apps' | 'create' | 'notifications' | 'account' | null;

@Component({
  selector: 'nx-topnav',
  imports: [RouterLink, FormsModule, Icon, Logo, AppTile, Avatar, NotificationsPanel],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './topnav.scss',
  host: { '(document:mousedown)': 'outside($event)', '(document:keydown)': 'keys($event)' },
  template: `
    <div class="left">
      <div class="anchor">
        <button class="icon-btn" (click)="toggle('apps')" aria-label="Switch app" [class.on]="menu() === 'apps'"><nx-icon name="grid" [size]="19" /></button>
        @if (menu() === 'apps') {
          <div class="popover switcher">
            <div class="menu-label">Switch to</div>
            @for (a of apps(); track a.code) {
              <a class="menu-item app" [routerLink]="a.home" (click)="menu.set(null)" [class.active]="section() === a.code">
                <nx-app-tile [app]="a.code" [size]="28" /><div class="grow"><div class="strong">{{ a.name }}</div><div class="tiny muted">{{ a.tagline }}</div></div>
                @if (section() === a.code) { <nx-icon name="check" [size]="16" class="accent" /> }
              </a>
            }
            <div class="menu-sep"></div>
            <a class="menu-item" routerLink="/apps" (click)="menu.set(null)"><nx-icon name="grid" [size]="16" /> Explore more apps</a>
            @if (auth.isAdmin()) { <a class="menu-item" routerLink="/admin" (click)="menu.set(null)"><nx-icon name="settings" [size]="16" /> Administration</a> }
          </div>
        }
      </div>
      <a routerLink="/" class="brand" aria-label="Newxr home"><nx-logo [mark]="26" /></a>
    </div>

    <form class="search" (submit)="search($event)" role="search">
      <nx-icon name="search" [size]="16" />
      <input #q name="q" [(ngModel)]="query" placeholder="Search issues, pages, repos, people" autocomplete="off" aria-label="Search" />
      <span class="kbd">/</span>
    </form>

    <div class="anchor">
      <button class="btn primary create" (click)="toggle('create')"><nx-icon name="plus" [size]="16" [stroke]="2.4" /><span class="hide-sm">Create</span></button>
      @if (menu() === 'create') {
        <div class="popover create-menu">
          @for (c of creatables(); track c.label) {
            <a class="menu-item" [routerLink]="c.link" [queryParams]="{ create: c.kind }" (click)="menu.set(null)">
              <nx-app-tile [app]="c.app" [size]="22" /><div><div class="strong">{{ c.label }}</div><div class="tiny muted">{{ c.hint }}</div></div></a>
          }
        </div>
      }
    </div>

    <div class="spacer"></div>

    <div class="right">
      <div class="anchor">
        <button class="icon-btn" (click)="toggle('notifications')" aria-label="Notifications" [class.on]="menu() === 'notifications'">
          <nx-icon name="bell" [size]="19" />@if (unread() > 0) { <span class="badge">{{ unread() > 9 ? '9+' : unread() }}</span> }
        </button>
        @if (menu() === 'notifications') { <nx-notifications-panel (changed)="unread.set($event)" (closed)="menu.set(null)" /> }
      </div>
      <a class="icon-btn hide-sm" [href]="helpUrl()" target="_blank" rel="noopener" aria-label="Help"><nx-icon name="help" [size]="19" /></a>
      <button class="icon-btn hide-sm" (click)="theme.cycle()" [attr.aria-label]="'Theme: ' + theme.mode()" [title]="'Theme: ' + theme.mode()">
        <nx-icon [name]="theme.mode() === 'dark' ? 'eye' : theme.mode() === 'light' ? 'lightbulb' : 'settings'" [size]="19" /></button>
      <div class="anchor">
        <button class="avatar-btn" (click)="toggle('account')" aria-label="Your account"><nx-avatar [user]="auth.me()" [size]="30" /></button>
        @if (menu() === 'account') {
          <div class="popover account">
            <div class="row me"><nx-avatar [user]="auth.me()" [size]="36" /><div class="truncate"><div class="strong truncate">{{ auth.me()?.name }}</div><div class="tiny muted truncate">{{ auth.me()?.email }}</div></div></div>
            <div class="tiny subtle signed">Signed in with {{ auth.me()?.signedInWith === 'portal' ? 'Portal SSO' : 'a Newxr password' }}</div>
            <div class="menu-sep"></div>
            <a class="menu-item" routerLink="/profile" (click)="menu.set(null)"><nx-icon name="user" [size]="16" /> Profile</a>
            @if (auth.isAdmin()) { <a class="menu-item" routerLink="/admin" (click)="menu.set(null)"><nx-icon name="settings" [size]="16" /> Administration</a> }
            <button class="menu-item" (click)="theme.cycle()"><nx-icon name="eye" [size]="16" /> Theme: {{ theme.mode() }}</button>
            <div class="menu-sep"></div>
            <button class="menu-item" (click)="auth.logout()"><nx-icon name="arrow-right" [size]="16" /> Log out</button>
          </div>
        }
      </div>
    </div>`,
})
export class TopNav {
  readonly auth = inject(Auth);
  readonly theme = inject(Theme);
  private api = inject(Api);
  private router = inject(Router);
  private host = inject(ElementRef<HTMLElement>);
  readonly menu = signal<Menu>(null);
  readonly unread = signal(0);
  readonly section = signal('');
  query = '';

  readonly apps = computed(() => APPS.filter(a => this.auth.me()?.apps.includes(a.code)));
  readonly creatables = computed(() => [
    { app: 'track', kind: 'issue', label: 'Issue', hint: 'Story, bug or task', link: '/track' },
    { app: 'pages', kind: 'page', label: 'Page', hint: 'Write in a space', link: '/pages' },
    { app: 'boards', kind: 'board', label: 'Board', hint: 'Lists and cards', link: '/boards' },
    { app: 'code', kind: 'repo', label: 'Repository', hint: 'Git repository', link: '/code' },
    { app: 'desk', kind: 'request', label: 'Request', hint: 'Raise a service request', link: '/desk' },
    { app: 'pulse', kind: 'alert', label: 'Alert', hint: 'Page whoever is on call', link: '/pulse' },
    { app: 'goals', kind: 'goal', label: 'Goal', hint: 'Set an outcome to track', link: '/goals' },
  ].filter(c => this.auth.me()?.apps.includes(c.app)));
  readonly helpUrl = computed(() => this.auth.options()?.supportUrl || 'https://github.com/tanmaysinghx/newxr#readme');

  constructor() {
    this.refreshUnread();
    setInterval(() => this.refreshUnread(), 60000);
    this.auth.loadOptions().catch(() => null);
    this.router.events.pipe(filter(e => e instanceof NavigationEnd)).subscribe(() => {
      this.section.set(location.pathname.split('/')[1] ?? '');
      this.menu.set(null);
    });
  }

  toggle(m: Exclude<Menu, null>) { this.menu.set(this.menu() === m ? null : m); }

  outside(e: MouseEvent) {
    if (!this.menu()) return;
    const target = e.target as HTMLElement;
    if (!target.closest('.anchor') || !this.host.nativeElement.contains(target)) this.menu.set(null);
  }

  keys(e: KeyboardEvent) {
    const t = e.target as HTMLElement;
    const typing = t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || t.isContentEditable;
    if (e.key === '/' && !typing) { e.preventDefault(); this.host.nativeElement.querySelector('input[name=q]')?.focus(); }
    if (e.key === 'Escape') this.menu.set(null);
  }

  search(e: Event) {
    e.preventDefault();
    if (this.query.trim()) this.router.navigate(['/search'], { queryParams: { q: this.query.trim() } });
  }

  private async refreshUnread() {
    try {
      const r = await this.api.get<{ unread: number }>('/api/notifications', { unreadOnly: true });
      this.unread.set(r.unread);
    } catch { /* signed out */ }
  }
}
