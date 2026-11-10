import { ChangeDetectionStrategy, Component, computed, inject, output, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Api } from '../core/api';
import { ago } from '../core/format';
import { UserRef } from '../core/models';
import { AppTile, Avatar } from '../shared/ui';

export interface NotificationItem { id: number; actor: UserRef | null; app: string; kind: string; text: string; link: string; read: boolean; at: string; }

@Component({
  selector: 'nx-notifications-panel',
  imports: [Avatar, AppTile],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [`
    :host { position: absolute; top: 46px; right: 0; width: 400px; max-width: calc(100vw - 24px); background: var(--surface); border: 1px solid var(--border); border-radius: 12px; box-shadow: var(--shadow-3); z-index: 70; }
    .head { display: flex; align-items: center; justify-content: space-between; padding: 14px 16px 6px; }
    .tabs { padding: 0 12px; }
    .items { max-height: 440px; overflow-y: auto; }
    .item { display: flex; gap: 10px; padding: 11px 16px; border-bottom: 1px solid var(--border); cursor: pointer; position: relative; }
    .item:hover { background: var(--surface-hover); }
    .item.unread { background: color-mix(in srgb, var(--accent-soft) 55%, var(--surface)); }
    .item.unread::after { content: ''; position: absolute; right: 14px; top: 16px; width: 7px; height: 7px; border-radius: 50%; background: var(--accent); }
    .who { position: relative; flex: none; } .who nx-app-tile { position: absolute; right: -4px; bottom: -4px; box-shadow: 0 0 0 2px var(--surface); }
    .text { font-size: 13px; padding-right: 14px; } .foot { padding: 10px; text-align: center; }`],
  template: `
    <div class="head"><h3>Notifications</h3>
      <label class="row small muted">Only unread <span class="switch"><input type="checkbox" [checked]="unreadOnly()" (change)="unreadOnly.set(!unreadOnly())"><span></span></span></label></div>
    <div class="tabs">
      @for (t of tabs; track t.kind) {
        <button [class.active]="tab() === t.kind" (click)="tab.set(t.kind)">{{ t.label }} @if (countFor(t.kind)) { <span class="count">{{ countFor(t.kind) }}</span> }</button>
      }
    </div>
    <div class="items">
      @for (n of visible(); track n.id) {
        <div class="item" [class.unread]="!n.read" (click)="open(n)">
          <span class="who"><nx-avatar [user]="n.actor" [size]="30" /><nx-app-tile [app]="n.app" [size]="14" /></span>
          <div><div class="text">{{ n.text }}</div><div class="tiny subtle">{{ ago(n.at) }}</div></div>
        </div>
      } @empty { <div class="empty small">You're all caught up.</div> }
    </div>
    <div class="foot"><button class="link-btn small" (click)="readAll()">Mark all as read</button></div>`,
})
export class NotificationsPanel {
  private api = inject(Api);
  private router = inject(Router);
  readonly changed = output<number>();
  readonly closed = output<void>();
  readonly items = signal<NotificationItem[]>([]);
  readonly tab = signal('direct');
  readonly unreadOnly = signal(false);
  readonly tabs = [{ kind: 'direct', label: 'Direct' }, { kind: 'watching', label: 'Watching' }, { kind: 'mention', label: 'Mentions' }];
  readonly visible = computed(() => this.items().filter(n => n.kind === this.tab() && (!this.unreadOnly() || !n.read)));
  ago = ago;

  constructor() { this.load(); }

  countFor(kind: string) { return this.items().filter(n => n.kind === kind && !n.read).length; }

  async load() {
    const r = await this.api.quiet(this.api.get<{ items: NotificationItem[]; unread: number }>('/api/notifications'));
    if (r) { this.items.set(r.items); this.changed.emit(r.unread); }
  }

  async open(n: NotificationItem) {
    if (!n.read) {
      await this.api.quiet(this.api.post(`/api/notifications/${n.id}/read`));
      this.items.update(list => list.map(x => x.id === n.id ? { ...x, read: true } : x));
      this.changed.emit(this.items().filter(x => !x.read).length);
    }
    this.closed.emit();
    if (n.link) this.router.navigateByUrl(n.link);
  }

  async readAll() {
    await this.api.quiet(this.api.post('/api/notifications/read-all'));
    this.items.update(list => list.map(x => ({ ...x, read: true })));
    this.changed.emit(0);
  }
}
