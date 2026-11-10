import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { Toasts } from '../core/toasts';
import { TopNav } from './topnav';

/** Signed-in layout: top nav over whatever the app renders (usually sidebar + page). */
@Component({
  selector: 'nx-shell',
  imports: [RouterOutlet, TopNav],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<nx-topnav /><router-outlet />`,
})
export class Shell {}

/** Sidebar + main column; used by every app layout. */
@Component({
  selector: 'nx-app-frame',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [':host{display:flex;min-height:calc(100vh - var(--nav-h))} .main{flex:1;min-width:0}'],
  template: `<ng-content select="nx-sidebar" /><main class="main"><ng-content /></main>`,
})
export class AppFrame {}

@Component({
  selector: 'nx-toasts',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="toasts" aria-live="polite">@for (t of toasts.items(); track t.id) { <div class="toast" [class.error]="t.kind === 'error'">{{ t.text }}</div> }</div>`,
})
export class ToastHost {
  readonly toasts = inject(Toasts);
}
