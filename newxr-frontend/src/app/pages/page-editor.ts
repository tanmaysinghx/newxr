import { ChangeDetectionStrategy, Component, ElementRef, computed, effect, inject, input, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Api, ApiError } from '../core/api';
import { ago } from '../core/format';
import { markdown } from '../core/markdown';
import { Toasts } from '../core/toasts';
import { Icon, Logo, Status } from '../shared/ui';
import { PageView, PagesApi } from './pages-api';

interface InsertItem { label: string; hint: string; icon: string; text: string; }

const INSERTS: InsertItem[] = [
  { label: 'Action item', hint: 'Track a to-do with an owner', icon: 'checklist', text: '- [ ] ' },
  { label: 'Table', hint: 'Insert a 3×3 table', icon: 'table', text: '| Column | Column | Column |\n| --- | --- | --- |\n|  |  |  |\n|  |  |  |\n' },
  { label: 'Track issue', hint: 'Link an issue by its key', icon: 'board', text: '[ORB-000](/track/issue/ORB-000)' },
  { label: 'Info panel', hint: 'Highlight a note', icon: 'info', text: '> [!info] ' },
  { label: 'Warning panel', hint: 'Call out a risk', icon: 'alert', text: '> [!warning] ' },
  { label: 'Code block', hint: 'Monospaced code', icon: 'code', text: '```\n\n```\n' },
  { label: 'Divider', hint: 'Horizontal rule', icon: 'menu', text: '\n---\n' },
];

/** Full-screen Markdown editor with autosaved drafts. */
@Component({
  selector: 'nx-page-editor',
  imports: [FormsModule, RouterLink, Icon, Logo, Status],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './page-editor.scss',
  host: { '(document:keydown)': 'keys($event)' },
  template: `
  <header class="bar">
    <a class="btn ghost icon" [routerLink]="back()" aria-label="Back"><nx-icon name="arrow-left" /></a>
    <a routerLink="/"><nx-logo [mark]="22" /></a>
    <div class="crumbs"><span class="sep">/</span><span>{{ page()?.space?.name }}</span>@for (c of page()?.crumbs ?? []; track c.id) { <span class="sep">/</span><span>{{ c.title }}</span> }</div>
    <nx-status [value]="page()?.status === 'PUBLISHED' ? (dirtyOrDraft() ? 'DRAFT' : 'PUBLISHED') : 'DRAFT'" />
    <span class="small subtle">{{ saving() ? 'Saving…' : savedAt() ? 'Saved ' + ago(savedAt()!) : '' }}</span>
    <span class="grow"></span>
    @if (page()?.status === 'PUBLISHED' && page()?.hasDraft) { <button class="btn ghost sm" (click)="discard()">Discard changes</button> }
    <button class="btn sm" (click)="preview.set(!preview())"><nx-icon [name]="preview() ? 'edit' : 'eye'" [size]="14" /> {{ preview() ? 'Edit' : 'Preview' }}</button>
    <button class="btn primary sm" (click)="publish()" [disabled]="busy()">{{ page()?.status === 'PUBLISHED' ? 'Update' : 'Publish' }}</button>
  </header>
  @if (!preview()) {
    <div class="toolbar" role="toolbar" aria-label="Formatting">
      <button (click)="line('## ')" title="Heading"><nx-icon name="heading" [size]="15" /></button>
      <button (click)="wrap('**')" title="Bold (Ctrl+B)"><nx-icon name="bold" [size]="15" /></button>
      <button (click)="wrap('*')" title="Italic (Ctrl+I)"><nx-icon name="italic" [size]="15" /></button>
      <span class="sep"></span>
      <button (click)="line('- ')" title="Bulleted list"><nx-icon name="list" [size]="15" /></button>
      <button (click)="line('- [ ] ')" title="Action item"><nx-icon name="checklist" [size]="15" /></button>
      <button (click)="line('> ')" title="Quote"><nx-icon name="quote" [size]="15" /></button>
      <span class="sep"></span>
      <button (click)="link()" title="Link (Ctrl+K)"><nx-icon name="link" [size]="15" /></button>
      <button (click)="insert(inserts[1].text)" title="Table"><nx-icon name="table" [size]="15" /></button>
      <button (click)="wrap('\`')" title="Inline code"><nx-icon name="code" [size]="15" /></button>
      <button (click)="insert(inserts[3].text)" title="Info panel"><nx-icon name="info" [size]="15" /></button>
      <button (click)="insert('@')" title="Mention"><nx-icon name="at" [size]="15" /></button>
      <span class="sep"></span>
      <button (click)="menuOpen() ? menuOpen.set(false) : openMenuAtCaret()" title="Insert (type / at the start of a line)"><nx-icon name="plus" [size]="15" /></button>
    </div>
  }
  <main class="canvas">
    <div class="doc">
      @if (preview()) {
        <h1 class="title-view">{{ title || 'Untitled' }}</h1>
        <div class="prose" [innerHTML]="html()"></div>
      } @else {
        <input class="title-input" [(ngModel)]="title" (ngModelChange)="changed()" placeholder="Give this page a title" aria-label="Title" />
        <div class="anchor">
          <textarea #ta class="body-input" [(ngModel)]="body" (ngModelChange)="changed()" (keydown)="editorKeys($event)" (input)="autoGrow()"
            placeholder="Start writing. Markdown works: ## heading, **bold**, - list, - [ ] task, | table |. Type / for more." aria-label="Content"></textarea>
          @if (menuOpen()) {
            <div class="popover insert-menu" [style.top.px]="menuTop()">
              <div class="menu-label">Insert</div>
              @for (it of inserts; track it.label) {
                <button class="menu-item" (mousedown)="$event.preventDefault(); pick(it)"><nx-icon [name]="it.icon" [size]="16" />
                  <div><div class="strong small">{{ it.label }}</div><div class="tiny muted">{{ it.hint }}</div></div></button> }
            </div>
          }
        </div>
      }
    </div>
  </main>
  @if (error()) { <div class="toast error floating">{{ error() }}</div> }`,
})
export class PageEditor {
  private pagesApi = inject(PagesApi);
  private api = inject(Api);
  private toasts = inject(Toasts);
  private router = inject(Router);
  readonly key = input.required<string>();
  readonly id = input.required<string>();
  readonly page = signal<PageView | null>(null);
  readonly preview = signal(false);
  readonly menuOpen = signal(false);
  readonly menuTop = signal(0);
  readonly saving = signal(false);
  readonly busy = signal(false);
  readonly savedAt = signal<string | null>(null);
  readonly dirty = signal(false);
  readonly error = signal<string | null>(null);
  readonly ta = viewChild<ElementRef<HTMLTextAreaElement>>('ta');
  readonly inserts = INSERTS;
  readonly back = computed(() => ['/pages', this.key(), ...(this.page()?.status === 'PUBLISHED' ? [this.id()] : [])]);
  readonly dirtyOrDraft = computed(() => this.dirty() || !!this.page()?.hasDraft);
  title = '';
  body = '';
  ago = ago;
  private timer: ReturnType<typeof setTimeout> | null = null;
  private bodyVersion = signal(0);
  readonly html = computed(() => { this.bodyVersion(); return markdown(this.body); });

  constructor() {
    effect(() => {
      this.id();
      this.pagesApi.page(+this.id()).then(p => {
        this.page.set(p);
        this.title = p.draftTitle ?? p.title;
        this.body = p.draftBody ?? p.body ?? '';
        if (this.title === 'Untitled') this.title = '';
        setTimeout(() => this.autoGrow());
      }).catch(() => this.router.navigate(['/pages', this.key()]));
    });
  }

  changed() {
    this.dirty.set(true);
    this.bodyVersion.update(n => n + 1);
    if (this.timer) clearTimeout(this.timer);
    this.timer = setTimeout(() => this.saveDraft(), 1200);
  }

  async saveDraft() {
    this.saving.set(true);
    try {
      const p = await this.pagesApi.save(+this.id(), { title: this.title || 'Untitled', body: this.body });
      this.page.set(p);
      this.savedAt.set(new Date().toISOString());
      this.dirty.set(false);
      this.error.set(null);
    } catch (e) { this.error.set('Draft not saved: ' + (e as ApiError).message); }
    finally { this.saving.set(false); }
  }

  async publish() {
    if (this.timer) clearTimeout(this.timer);
    this.busy.set(true);
    try {
      const p = await this.pagesApi.save(+this.id(), { title: this.title || 'Untitled', body: this.body, publish: true });
      this.pagesApi.touch();
      this.toasts.info(`Published “${p.title}”`);
      this.router.navigate(['/pages', this.key(), p.id]);
    } catch (e) { this.error.set((e as ApiError).message); }
    finally { this.busy.set(false); }
  }

  async discard() {
    const p = await this.api.quiet(this.pagesApi.discard(+this.id()));
    if (p) this.router.navigate(['/pages', this.key(), p.id]);
  }

  keys(e: KeyboardEvent) {
    if ((e.ctrlKey || e.metaKey) && e.key === 's') { e.preventDefault(); this.saveDraft(); }
    if (e.key === 'Escape') this.menuOpen.set(false);
  }

  editorKeys(e: KeyboardEvent) {
    const mod = e.ctrlKey || e.metaKey;
    if (mod && e.key === 'b') { e.preventDefault(); this.wrap('**'); }
    else if (mod && e.key === 'i') { e.preventDefault(); this.wrap('*'); }
    else if (mod && e.key === 'k') { e.preventDefault(); this.link(); }
    else if (e.key === '/') {
      const t = this.ta()!.nativeElement;
      const before = t.value.slice(0, t.selectionStart);
      if (before === '' || before.endsWith('\n')) { e.preventDefault(); this.openMenuAtCaret(); }
    } else if (e.key === 'Enter') {
      // Continue lists and action items.
      const t = this.ta()!.nativeElement;
      const lineStart = t.value.lastIndexOf('\n', t.selectionStart - 1) + 1;
      const line = t.value.slice(lineStart, t.selectionStart);
      const m = /^(\s*)(- \[[ x]\] |- |\* |\d+\. )/.exec(line);
      if (m) {
        e.preventDefault();
        if (line.trim() === m[2].trim()) { this.replace(lineStart, t.selectionStart, ''); return; }
        const next = /^\d+\. $/.test(m[2]) ? `${parseInt(m[2], 10) + 1}. ` : m[2].replace('[x]', '[ ]');
        this.insert('\n' + m[1] + next);
      }
    }
  }

  /** Places the insert menu just under the caret's line (14.5px × 1.75 line height). */
  openMenuAtCaret() {
    const t = this.ta()?.nativeElement;
    const line = t ? t.value.slice(0, t.selectionStart).split('\n').length : 1;
    this.menuTop.set(line * 25.4 + 4);
    this.menuOpen.set(true);
  }

  pick(it: InsertItem) { this.menuOpen.set(false); this.insert(it.text); }

  wrap(mark: string) {
    const t = this.ta()?.nativeElement;
    if (!t) return;
    const [s, e] = [t.selectionStart, t.selectionEnd];
    const sel = t.value.slice(s, e) || 'text';
    this.replace(s, e, mark + sel + mark, s + mark.length, s + mark.length + sel.length);
  }

  line(prefix: string) {
    const t = this.ta()?.nativeElement;
    if (!t) return;
    const start = t.value.lastIndexOf('\n', t.selectionStart - 1) + 1;
    this.replace(start, start, prefix, t.selectionStart + prefix.length);
  }

  link() {
    const t = this.ta()?.nativeElement;
    if (!t) return;
    const [s, e] = [t.selectionStart, t.selectionEnd];
    const sel = t.value.slice(s, e) || 'link text';
    this.replace(s, e, `[${sel}](https://)`, s + sel.length + 3, s + sel.length + 11);
  }

  insert(text: string) {
    const t = this.ta()?.nativeElement;
    if (!t) return;
    this.replace(t.selectionStart, t.selectionEnd, text);
  }

  private replace(s: number, e: number, text: string, selStart?: number, selEnd?: number) {
    const t = this.ta()!.nativeElement;
    this.body = t.value.slice(0, s) + text + t.value.slice(e);
    t.value = this.body;
    const caret = selStart ?? s + text.length;
    t.focus();
    t.setSelectionRange(caret, selEnd ?? caret);
    this.changed();
    this.autoGrow();
  }

  autoGrow() {
    const t = this.ta()?.nativeElement;
    if (t) { t.style.height = 'auto'; t.style.height = Math.max(420, t.scrollHeight) + 'px'; }
  }
}
