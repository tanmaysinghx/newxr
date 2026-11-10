import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ToastHost } from './shell/shell';

@Component({
  selector: 'nx-root',
  imports: [RouterOutlet, ToastHost],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<router-outlet /><nx-toasts />`,
})
export class App {}
