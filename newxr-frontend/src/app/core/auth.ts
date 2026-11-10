import { Injectable, computed, inject, signal } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Api } from './api';
import { Me, SignInOptions } from './models';

@Injectable({ providedIn: 'root' })
export class Auth {
  private api = inject(Api);
  readonly me = signal<Me | null>(null);
  readonly options = signal<SignInOptions | null>(null);
  readonly isAdmin = computed(() => this.me()?.role === 'ADMIN');
  private loaded = false;

  async loadOptions(): Promise<SignInOptions> {
    const o = await this.api.get<SignInOptions>('/api/public/auth/options');
    this.options.set(o);
    return o;
  }

  /** Resolves the signed-in user once per page load; null when signed out. */
  async ensure(): Promise<Me | null> {
    if (this.loaded) return this.me();
    try {
      this.me.set(await this.api.get<Me>('/api/auth/me'));
    } catch {
      this.me.set(null);
    }
    this.loaded = true;
    return this.me();
  }

  async login(email: string, password: string): Promise<Me> {
    const me = await this.api.post<Me>('/api/public/auth/login', { email, password });
    this.me.set(me);
    this.loaded = true;
    return me;
  }

  async logout() {
    try { await this.api.post('/api/auth/logout'); } catch { /* already signed out */ }
    this.me.set(null);
    location.assign('/login');
  }

  canUse(app: string) { return this.me()?.apps.includes(app) ?? false; }
}

/** Signed-in users only; setup first when the server has no administrator. */
export const authGuard: CanActivateFn = async (_route, state) => {
  const auth = inject(Auth);
  const router = inject(Router);
  const me = await auth.ensure();
  if (me) return true;
  const o = await auth.loadOptions().catch(() => null);
  if (o?.setupRequired) return router.parseUrl('/setup');
  return router.createUrlTree(['/login'], { queryParams: state.url !== '/' ? { next: state.url } : {} });
};

export const adminGuard: CanActivateFn = async () => {
  const auth = inject(Auth);
  await auth.ensure();
  return auth.isAdmin() ? true : inject(Router).parseUrl('/');
};
