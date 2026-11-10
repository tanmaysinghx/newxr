import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ApiError } from '../core/api';
import { Auth } from '../core/auth';
import { APPS, SignInOptions } from '../core/models';
import { AppTile, Icon, Logo } from '../shared/ui';

const SSO_ERRORS: Record<string, string> = {
  sso_unavailable: 'Portal SSO is not reachable right now. Try again, or sign in with your password.',
  sso_failed: 'Sign-in with Portal SSO did not complete. Try again.',
  sso_not_invited: 'Your Portal SSO account has no Newxr access yet. Ask an administrator to invite you.',
  sso_no_email: 'Portal SSO did not share an email address for your account.',
  account_disabled: 'This account has been deactivated.',
};

@Component({
  selector: 'nx-login',
  imports: [FormsModule, Logo, AppTile, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './login.scss',
  template: `
    <section class="form-side">
      <div class="form">
        <nx-logo [mark]="28" />
        <h1>Log in to continue</h1>
        <p class="muted small">One account for {{ appList }}@if (options()?.organization && options()?.organization !== 'Newxr') { at {{ options()?.organization }}}.</p>

        @if (error()) { <div class="banner danger"><nx-icon name="alert" /><div>{{ error() }}</div></div> }

        @if (showPassword()) {
          <form class="col gap-12" (submit)="submit($event)">
            <div class="field"><label for="email">Work email</label>
              <input id="email" class="input lg" type="email" name="email" [(ngModel)]="email" autocomplete="username" required autofocus /></div>
            <div class="field"><label for="password">Password</label>
              <input id="password" class="input lg" type="password" name="password" [(ngModel)]="password" autocomplete="current-password" required /></div>
            <div class="row between small">
              <span class="subtle">Sessions last 12 hours.</span>
              <button type="button" class="link-btn" (click)="forgot.set(!forgot())">Forgot password?</button>
            </div>
            @if (forgot()) { <div class="banner small"><nx-icon name="info" /><div>Ask a Newxr administrator to reset it under Administration → Users.
              @if (options()?.portalEnabled) { Or sign in with {{ ssoName() }} below. }</div></div> }
            <button class="btn primary block" [disabled]="busy()">{{ busy() ? 'Signing in…' : 'Continue' }}</button>
          </form>
        }

        @if (options()?.portalEnabled) {
          @if (showPassword()) { <div class="or"><span>or</span></div> }
          <a class="btn block" [href]="options()?.ssoLoginPath"><nx-icon name="lock" /> {{ options()?.portalButtonLabel }}</a>
          @if (!options()?.builtinEnabled && !adminLogin()) {
            <button class="link-btn small admin-link" (click)="adminLogin.set(true)">Administrator? Sign in with a password</button>
          }
        }
        <p class="tiny subtle legal">Newxr is open source under the Apache License 2.0.</p>
      </div>
    </section>
    <section class="hero">
      <div class="hero-inner">
        <h2>Every team's work, connected.</h2>
        <p>Plan in Track, write in Pages, ship from Code and keep customers happy in Desk — with one search and one login.</p>
        <div class="chips">@for (a of apps; track a.code) { <span class="chip-app"><nx-app-tile [app]="a.code" [size]="20" />{{ a.short }}</span> }</div>
      </div>
    </section>`,
})
export class Login {
  private auth = inject(Auth);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  readonly options = signal<SignInOptions | null>(null);
  readonly error = signal<string | null>(null);
  readonly busy = signal(false);
  readonly forgot = signal(false);
  readonly adminLogin = signal(false);
  readonly apps = APPS;
  readonly appList = 'Track, Pages, Boards, Code, Desk, Pulse and Goals';
  email = '';
  password = '';

  constructor() {
    const err = this.route.snapshot.queryParamMap.get('error');
    if (err) this.error.set(SSO_ERRORS[err] ?? 'Sign-in failed.');
    this.auth.loadOptions().then(o => {
      if (o.setupRequired) { this.router.navigateByUrl('/setup'); return; }
      this.options.set(o);
    }).catch(() => this.error.set('Cannot reach the Newxr server.'));
    this.auth.ensure().then(me => { if (me) this.router.navigateByUrl(this.next()); });
  }

  showPassword() { const o = this.options(); return !o || o.builtinEnabled || !o.portalEnabled || this.adminLogin(); }
  ssoName() { return (this.options()?.portalButtonLabel ?? 'SSO').replace(/^Continue with /, ''); }

  async submit(e: Event) {
    e.preventDefault();
    this.busy.set(true);
    this.error.set(null);
    try {
      await this.auth.login(this.email, this.password);
      this.router.navigateByUrl(this.next());
    } catch (err) {
      this.error.set((err as ApiError).message);
    } finally {
      this.busy.set(false);
    }
  }

  private next() {
    const n = this.route.snapshot.queryParamMap.get('next');
    return n && n.startsWith('/') && !n.startsWith('//') ? n : '/';
  }
}
