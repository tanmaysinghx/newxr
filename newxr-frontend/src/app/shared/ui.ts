import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { ICONS } from '../core/icons';
import { STATUS_TONE, label } from '../core/format';
import { UserRef, appInfo } from '../core/models';

const cache = new Map<string, SafeHtml>();

/** Line icon from the design system. Only names from ICONS are ever rendered. */
@Component({
  selector: 'nx-icon',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'nx-icon', '[style.width.px]': 'size()', '[style.height.px]': 'size()', '[innerHTML]': 'svg()', 'aria-hidden': 'true' },
  styles: [':host{display:inline-flex;flex:none;line-height:0} :host ::ng-deep svg{width:100%;height:100%}'],
  template: '',
})
export class Icon {
  private san = inject(DomSanitizer);
  readonly name = input.required<string>();
  readonly size = input(16);
  readonly stroke = input(2);
  readonly svg = computed(() => {
    const key = `${this.name()}|${this.stroke()}`;
    let v = cache.get(key);
    if (!v) {
      const body = ICONS[this.name()] ?? ICONS['help'];
      v = this.san.bypassSecurityTrustHtml(
        `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="${this.stroke()}" stroke-linecap="round" stroke-linejoin="round">${body}</svg>`);
      cache.set(key, v);
    }
    return v;
  });
}

@Component({
  selector: 'nx-avatar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[style.width.px]': 'size()', '[style.height.px]': 'size()', '[style.font-size.px]': 'size() * 0.38',
    '[style.background]': 'bg()', '[style.color]': 'user()?.color ?? \'var(--text-subtle)\'', '[attr.title]': 'user()?.name ?? \'Unassigned\'',
  },
  styles: [':host{display:inline-flex;align-items:center;justify-content:center;border-radius:50%;font-weight:600;flex:none;letter-spacing:0;user-select:none}'],
  template: `{{ user()?.initials ?? '' }}`,
})
export class Avatar {
  readonly user = input<UserRef | null | undefined>(null);
  readonly size = input(24);
  readonly bg = computed(() => {
    const c = this.user()?.color;
    return c ? `color-mix(in srgb, ${c} 16%, var(--surface))` : 'var(--neutral-soft)';
  });
}

@Component({
  selector: 'nx-avatars',
  imports: [Avatar],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [':host{display:inline-flex} nx-avatar{box-shadow:0 0 0 2px var(--surface)} nx-avatar+nx-avatar{margin-left:-6px} .more{margin-left:4px;font-size:12px;color:var(--text-muted);align-self:center}'],
  template: `@for (u of shown(); track u.id) { <nx-avatar [user]="u" [size]="size()" /> }
    @if (users().length > max()) { <span class="more">+{{ users().length - max() }}</span> }`,
})
export class Avatars {
  readonly users = input<UserRef[]>([]);
  readonly size = input(24);
  readonly max = input(5);
  readonly shown = computed(() => this.users().slice(0, this.max()));
}

/** Status lozenge; tone derived from the value (IN_PROGRESS → info, DONE → success...). */
@Component({
  selector: 'nx-status',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="lozenge" [class]="'lozenge ' + tone()">{{ text() }}</span>`,
  styles: [':host{display:inline-flex}'],
})
export class Status {
  readonly value = input<string | null | undefined>('');
  readonly text = computed(() => label(this.value()));
  readonly tone = computed(() => STATUS_TONE[this.value() ?? ''] ?? '');
}

@Component({
  selector: 'nx-app-tile',
  imports: [Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[style.width.px]': 'size()', '[style.height.px]': 'size()', '[style.border-radius.px]': 'size() * 0.28', '[style.background]': 'info()?.color' },
  styles: [':host{display:inline-flex;align-items:center;justify-content:center;color:#fff;flex:none}'],
  template: `<nx-icon [name]="info()?.icon ?? 'grid'" [size]="size() * 0.6" />`,
})
export class AppTile {
  readonly app = input.required<string>();
  readonly size = input(28);
  readonly info = computed(() => appInfo(this.app()));
}

const ITYPE: Record<string, [string, string]> = {
  STORY: ['#2f9a57', 'bookmark'], BUG: ['#d23b3b', 'bug'], TASK: ['#2563c9', 'check'], EPIC: ['#7c3aed', 'bolt'], SUBTASK: ['#0e9f8e', 'list'],
};

@Component({
  selector: 'nx-itype',
  imports: [Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[style.width.px]': 'size()', '[style.height.px]': 'size()', '[style.background]': 'def()[0]', '[attr.title]': 'type()' },
  styles: [':host{display:inline-flex;align-items:center;justify-content:center;border-radius:3px;color:#fff;flex:none}'],
  template: `<nx-icon [name]="def()[1]" [size]="size() - 4" [stroke]="2.4" />`,
})
export class IssueType {
  readonly type = input<string>('TASK');
  readonly size = input(16);
  readonly def = computed(() => ITYPE[this.type()] ?? ITYPE['TASK']);
}

const PRIO: Record<string, [string, string]> = {
  HIGHEST: ['#d23b3b', 'chevron-up'], HIGH: ['#e5683d', 'chevron-up'], MEDIUM: ['#d99a00', 'menu'], LOW: ['#2563c9', 'chevron-down'], LOWEST: ['#8a909e', 'chevron-down'],
};

@Component({
  selector: 'nx-priority',
  imports: [Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [':host{display:inline-flex;align-items:center;gap:6px}'],
  template: `<nx-icon [name]="def()[1]" [size]="16" [stroke]="2.4" [style.color]="def()[0]" [attr.title]="text()" />
    @if (showLabel()) { <span>{{ text() }}</span> }`,
})
export class Priority {
  readonly value = input<string>('MEDIUM');
  readonly showLabel = input(false);
  readonly def = computed(() => PRIO[this.value()] ?? PRIO['MEDIUM']);
  readonly text = computed(() => label(this.value()));
}

@Component({
  selector: 'nx-logo',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [':host{display:inline-flex;align-items:center;gap:8px;font-weight:700;letter-spacing:-.02em;color:var(--text)} svg{flex:none}'],
  template: `<svg [attr.width]="mark()" [attr.height]="mark()" viewBox="0 0 52 52" aria-hidden="true">
      <rect width="52" height="52" rx="15.6" fill="#5a4bf5" />
      <path d="M15 37V15l22 22V15" fill="none" stroke="#fff" stroke-width="5.2" stroke-linecap="round" stroke-linejoin="round" />
      <circle cx="41" cy="11" r="4" fill="#c9c3ff" /></svg>
    @if (word()) { <span [style.font-size.px]="mark() * 0.72">newxr</span> }`,
})
export class Logo {
  readonly mark = input(26);
  readonly word = input(true);
}

/** Modal shell: backdrop click and Escape close it. */
@Component({
  selector: 'nx-modal',
  imports: [Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '(document:keydown.escape)': 'closed.emit()' },
  template: `<div class="backdrop" (mousedown)="$event.target === $event.currentTarget && closed.emit()">
    <div class="modal" [class.wide]="wide()" role="dialog" aria-modal="true" [attr.aria-label]="title()">
      <div class="modal-head"><h2>{{ title() }}</h2>
        <button class="btn ghost icon" (click)="closed.emit()" aria-label="Close"><nx-icon name="x" /></button></div>
      <ng-content />
    </div></div>`,
})
export class Modal {
  readonly title = input('');
  readonly wide = input(false);
  readonly closed = output<void>();
}

/** Small "N items" style empty state with an icon. */
@Component({
  selector: 'nx-empty',
  imports: [Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="empty"><nx-icon [name]="icon()" [size]="28" /><div class="strong">{{ title() }}</div>
    @if (text()) { <div class="small mt-8">{{ text() }}</div> }<ng-content /></div>`,
})
export class Empty {
  readonly icon = input('inbox');
  readonly title = input('Nothing here yet');
  readonly text = input('');
}

export const UI = [Icon, Avatar, Avatars, Status, AppTile, IssueType, Priority, Logo, Modal, Empty] as const;
