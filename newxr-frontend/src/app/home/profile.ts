import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api, ApiError } from '../core/api';
import { Auth } from '../core/auth';
import { Toasts } from '../core/toasts';
import { Avatar, Icon } from '../shared/ui';

@Component({
  selector: 'nx-profile',
  imports: [FormsModule, Avatar, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="page narrow">
    <div class="title-bar"><div class="row gap-12"><nx-avatar [user]="auth.me()" [size]="56" /><div><h1>{{ auth.me()?.name }}</h1><div class="sub">{{ auth.me()?.email }}</div></div></div></div>
    <div class="card col gap-12" style="max-width:560px">
      <h3>Profile</h3>
      <div class="field"><label>Full name</label><input class="input" [(ngModel)]="name" /></div>
      <div class="field"><label>Job title</label><input class="input" [(ngModel)]="jobTitle" /></div>
      <div><button class="btn primary" (click)="save()">Save</button></div>
    </div>
    @if (auth.me()?.signedInWith === 'password' || auth.isAdmin()) {
    <div class="card col gap-12" style="max-width:560px">
      <h3>Password</h3>
      @if (pwError()) { <div class="banner danger"><nx-icon name="alert" /><div>{{ pwError() }}</div></div> }
      <div class="field"><label>Current password</label><input class="input" type="password" [(ngModel)]="current" autocomplete="current-password" /></div>
      <div class="field"><label>New password</label><input class="input" type="password" [(ngModel)]="next" autocomplete="new-password" />
        <span class="hint">At least 12 characters, with upper case, lower case and a digit.</span></div>
      <div><button class="btn" (click)="changePassword()">Change password</button></div>
    </div>
    }
  </div>`,
})
export class Profile {
  readonly auth = inject(Auth);
  private api = inject(Api);
  private toasts = inject(Toasts);
  readonly pwError = signal<string | null>(null);
  name = this.auth.me()?.name ?? '';
  jobTitle = this.auth.me()?.jobTitle ?? '';
  current = '';
  next = '';

  async save() {
    const r = await this.api.quiet(this.api.put('/api/profile', { name: this.name, jobTitle: this.jobTitle }));
    if (r) { this.auth.me.update(m => m && ({ ...m, name: this.name, jobTitle: this.jobTitle })); this.toasts.info('Profile saved'); }
  }

  async changePassword() {
    this.pwError.set(null);
    try {
      await this.api.post('/api/profile/password', { currentPassword: this.current, newPassword: this.next });
      this.current = this.next = '';
      this.toasts.info('Password changed');
    } catch (e) { this.pwError.set((e as ApiError).message); }
  }
}
