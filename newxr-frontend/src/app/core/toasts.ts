import { Injectable, signal } from '@angular/core';

export interface Toast { id: number; text: string; kind: 'info' | 'error'; }

@Injectable({ providedIn: 'root' })
export class Toasts {
  readonly items = signal<Toast[]>([]);
  private seq = 0;
  info(text: string) { this.push(text, 'info'); }
  error(text: string) { this.push(text, 'error'); }
  private push(text: string, kind: Toast['kind']) {
    const id = ++this.seq;
    this.items.update(t => [...t, { id, text, kind }]);
    setTimeout(() => this.items.update(t => t.filter(x => x.id !== id)), kind === 'error' ? 6000 : 3500);
  }
}
