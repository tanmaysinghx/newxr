import { Injectable, signal } from '@angular/core';

type Mode = 'light' | 'dark' | 'system';

@Injectable({ providedIn: 'root' })
export class Theme {
  readonly mode = signal<Mode>(this.read());
  constructor() { this.apply(this.mode()); }
  cycle() {
    const next: Mode = this.mode() === 'system' ? 'dark' : this.mode() === 'dark' ? 'light' : 'system';
    this.mode.set(next);
    try { localStorage.setItem('newxr.theme', next); } catch { /* storage blocked */ }
    this.apply(next);
  }
  private apply(m: Mode) {
    if (m === 'system') document.documentElement.removeAttribute('data-theme');
    else document.documentElement.setAttribute('data-theme', m);
  }
  private read(): Mode {
    try { return (localStorage.getItem('newxr.theme') as Mode) || 'system'; } catch { return 'system'; }
  }
}
