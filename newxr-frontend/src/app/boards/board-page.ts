import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Api } from '../core/api';
import { Auth } from '../core/auth';
import { ago, shortDate } from '../core/format';
import { Hub } from '../core/hub';
import { markdown } from '../core/markdown';
import { People } from '../core/people';
import { Toasts } from '../core/toasts';
import { Avatar, Avatars, Icon } from '../shared/ui';
import { BACKGROUNDS, BoardView, BoardsApi, CardSummary, CardView, bg, labelColor } from './boards-api';

type Panel = 'members' | 'labels' | 'dates' | 'cover' | 'link' | 'move' | null;

@Component({
  selector: 'nx-board-page',
  imports: [FormsModule, RouterLink, Avatar, Avatars, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './board-page.scss',
  templateUrl: './board-page.html',
})
export class BoardPage {
  private boardsApi = inject(BoardsApi);
  private api = inject(Api);
  private hub = inject(Hub);
  private toasts = inject(Toasts);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  readonly auth = inject(Auth);
  readonly people = inject(People);
  readonly id = input.required<string>();
  readonly data = signal<BoardView | null>(null);
  readonly card = signal<CardView | null>(null);
  readonly dragging = signal<CardSummary | null>(null);
  readonly overList = signal<number | null>(null);
  readonly overCard = signal<number | null>(null);
  readonly adding = signal<number | null>(null);
  readonly addingList = signal(false);
  readonly panel = signal<Panel>(null);
  readonly editingDesc = signal(false);
  readonly starred = signal(false);
  readonly bgOpen = signal(false);
  readonly backgrounds = BACKGROUNDS;
  bg = bg; labelColor = labelColor; ago = ago; shortDate = shortDate;
  newCard = ''; newList = ''; newCheck = ''; newComment = ''; desc = ''; newLabel = ''; dueLocal = ''; linkIssue = '';

  readonly checkPct = computed(() => { const c = this.card(); return c && c.checklist.length ? Math.round(c.checklist.filter(x => x.done).length * 100 / c.checklist.length) : 0; });
  readonly descHtml = computed(() => markdown(this.card()?.description));
  readonly allLabels = computed(() => [...new Set(['Design', 'Copy', 'Launch', 'Social', 'Legal', 'Blocked', ...(this.data()?.lists ?? []).flatMap(l => l.cards.flatMap(c => c.labels))])]);

  constructor() {
    this.people.load();
    effect(() => { this.id(); this.load(); });
    this.route.queryParamMap.subscribe(q => { const c = q.get('card'); if (c) this.openCard(+c); else this.card.set(null); });
  }

  async load() {
    const d = await this.boardsApi.board(+this.id());
    this.data.set(d);
    this.hub.viewed({ app: 'boards', kind: 'board', title: d.board.name, subtitle: 'Boards', link: `/boards/${d.board.id}` });
    this.hub.isStarred(`/boards/${d.board.id}`).then(s => this.starred.set(s));
  }

  dueState(c: { due?: string; dueDone: boolean }) {
    if (!c.due) return '';
    if (c.dueDone) return 'done';
    const t = new Date(c.due).getTime();
    return t < Date.now() ? 'overdue' : t < Date.now() + 2 * 86400000 ? 'soon' : '';
  }

  // ---- drag & drop ----
  async drop(e: DragEvent, listId: number) {
    e.preventDefault();
    const c = this.dragging();
    this.dragging.set(null);
    this.overList.set(null);
    if (!c) return;
    const d = this.data()!;
    const target = d.lists.find(l => l.id === listId)!.cards.filter(x => x.id !== c.id);
    const idx = this.overCard() ? target.findIndex(x => x.id === this.overCard()) : -1;
    const lists = d.lists.map(l => ({ ...l, cards: l.cards.filter(x => x.id !== c.id) }));
    const dest = lists.find(l => l.id === listId)!;
    dest.cards.splice(idx >= 0 ? idx : dest.cards.length, 0, { ...c, listId });
    this.data.set({ ...d, lists });
    this.overCard.set(null);
    await this.api.quiet(this.boardsApi.moveCard(c.id, { listId, beforeId: idx > 0 ? target[idx - 1].id : idx < 0 ? target.at(-1)?.id ?? null : null, afterId: idx >= 0 ? target[idx].id : null }));
  }

  // ---- lists & cards ----
  async addCard(listId: number) {
    if (!this.newCard.trim()) { this.adding.set(null); return; }
    const c = await this.api.quiet(this.boardsApi.createCard(listId, this.newCard.trim()));
    if (c) { this.data.update(d => d && { ...d, lists: d.lists.map(l => l.id === listId ? { ...l, cards: [...l.cards, c] } : l) }); this.newCard = ''; }
  }
  async addList() {
    if (!this.newList.trim()) { this.addingList.set(false); return; }
    const l = await this.api.quiet(this.boardsApi.createList(+this.id(), this.newList.trim()));
    if (l) { this.data.update(d => d && { ...d, lists: [...d.lists, l] }); this.newList = ''; }
  }
  async renameList(id: number, name: string) { if (name.trim()) await this.api.quiet(this.boardsApi.renameList(id, name.trim())); }
  async archiveList(id: number) { await this.api.quiet(this.boardsApi.archiveList(id)); this.load(); }
  async setBackground(b: string) { await this.api.quiet(this.boardsApi.update(+this.id(), { background: b })); this.bgOpen.set(false); this.load(); }
  async star() { const b = this.data()!.board; this.starred.set(await this.hub.toggleStar({ app: 'boards', kind: 'board', title: b.name, link: `/boards/${b.id}` })); }

  // ---- card dialog ----
  open(c: CardSummary) { this.router.navigate([], { queryParams: { card: c.id } }); }
  close() { this.router.navigate([], { queryParams: { card: null } }); this.panel.set(null); this.editingDesc.set(false); this.load(); }
  async openCard(id: number) {
    const c = await this.api.quiet(this.boardsApi.card(id));
    if (c) { this.card.set(c); this.dueLocal = c.due ? new Date(c.due).toISOString().slice(0, 16) : ''; this.linkIssue = c.linkedIssue ?? ''; }
  }
  private async patch(b: Record<string, unknown>) {
    const c = await this.api.quiet(this.boardsApi.updateCard(this.card()!.id, b));
    if (c) this.card.set(c);
  }
  saveTitle(t: string) { if (t.trim() && t !== this.card()!.title) this.patch({ title: t }); }
  editDesc() { this.desc = this.card()!.description ?? ''; this.editingDesc.set(true); }
  async saveDesc() { await this.patch({ description: this.desc }); this.editingDesc.set(false); }
  toggleMember(id: number) {
    const ids = this.card()!.members.map(m => m.id);
    this.patch({ memberIds: ids.includes(id) ? ids.filter(x => x !== id) : [...ids, id] });
  }
  isMember(id: number) { return this.card()?.members.some(m => m.id === id) ?? false; }
  toggleLabel(l: string) { const ls = this.card()!.labels; this.patch({ labels: ls.includes(l) ? ls.filter(x => x !== l) : [...ls, l] }); }
  addLabel() { if (this.newLabel.trim()) { this.patch({ labels: [...this.card()!.labels, this.newLabel.trim()] }); this.newLabel = ''; } }
  saveDue() { this.dueLocal ? this.patch({ due: new Date(this.dueLocal).toISOString() }) : this.patch({ clear: ['due'] }); }
  toggleDueDone() { this.patch({ dueDone: !this.card()!.dueDone }); }
  setCover(c: string | null) { c ? this.patch({ cover: c }) : this.patch({ clear: ['cover'] }); }
  toggleCheck(i: number) { this.patch({ checklist: this.card()!.checklist.map((c, j) => j === i ? { ...c, done: !c.done } : c) }); }
  addCheck() { if (this.newCheck.trim()) { this.patch({ checklist: [...this.card()!.checklist, { text: this.newCheck.trim(), done: false }] }); this.newCheck = ''; } }
  removeCheck(i: number) { this.patch({ checklist: this.card()!.checklist.filter((_, j) => j !== i) }); }
  saveLink() { this.linkIssue.trim() ? this.patch({ linkedIssue: this.linkIssue }) : this.patch({ clear: ['linkedIssue'] }); this.panel.set(null); }
  async moveTo(listId: number) { await this.api.quiet(this.boardsApi.moveCard(this.card()!.id, { listId })); this.panel.set(null); this.openCard(this.card()!.id); }
  async archiveCard() { await this.api.quiet(this.boardsApi.archiveCard(this.card()!.id)); this.toasts.info('Card archived'); this.close(); }
  async comment() {
    if (!this.newComment.trim()) return;
    const c = await this.api.quiet(this.boardsApi.comment(this.card()!.id, this.newComment));
    if (c) { this.card.update(x => x && { ...x, comments: [c, ...x.comments] }); this.newComment = ''; }
  }
  md(s: string) { return markdown(s); }
}
