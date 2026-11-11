import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Api } from '../core/api';
import { Auth } from '../core/auth';
import { ago, dateTime, label, longDate } from '../core/format';
import { Hub } from '../core/hub';
import { markdown } from '../core/markdown';
import { People } from '../core/people';
import { Toasts } from '../core/toasts';
import { AppFrame } from '../shell/shell';
import { Sidebar } from '../shell/sidebar';
import { Avatar, Icon, IssueType, Modal, Priority, Status } from '../shared/ui';
import { CreateIssue } from './create-issue';
import { IssueDetail, PRIORITIES, STATUSES, SprintView, TYPES, TrackApi } from './track-api';

interface LinkedWork { kind: string; title: string; status: string; link: string; }

@Component({
  selector: 'nx-issue-page',
  imports: [FormsModule, RouterLink, AppFrame, Sidebar, Avatar, Icon, IssueType, Priority, Status, Modal, CreateIssue],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './issue-page.scss',
  templateUrl: './issue-page.html',
})
export class IssuePage {
  private trackApi = inject(TrackApi);
  private api = inject(Api);
  private hub = inject(Hub);
  private router = inject(Router);
  private toasts = inject(Toasts);
  readonly auth = inject(Auth);
  readonly people = inject(People);
  readonly key = input.required<string>();
  readonly issue = signal<IssueDetail | null>(null);
  readonly sprints = signal<SprintView[]>([]);
  readonly linked = signal<LinkedWork[]>([]);
  /** Linked-work URLs may carry a query string (?branch=…), which [routerLink] would encode. */
  openLink(url: string) { this.router.navigateByUrl(url); }
  readonly tab = signal<'comments' | 'history'>('comments');
  readonly editingSummary = signal(false);
  readonly editingDesc = signal(false);
  readonly statusOpen = signal(false);
  readonly addingChild = signal(false);
  readonly confirmDelete = signal(false);
  readonly starred = signal(false);
  readonly statuses = STATUSES;
  readonly types = TYPES;
  readonly priorities = PRIORITIES;
  label = label; ago = ago; longDate = longDate; dateTime = dateTime;
  summary = '';
  description = '';
  newComment = '';
  newCheck = '';
  editingComment: number | null = null;
  commentDraft = '';
  labelsText = '';

  readonly descHtml = computed(() => markdown(this.issue()?.description));
  readonly checkDone = computed(() => (this.issue()?.checklist ?? []).filter(c => c.done).length);
  readonly childDone = computed(() => (this.issue()?.children ?? []).filter(c => c.status === 'DONE').length);
  readonly childPct = computed(() => { const n = this.issue()?.children.length ?? 0; return n ? Math.round(this.childDone() * 100 / n) : 0; });
  readonly nav = computed(() => {
    const k = this.issue()?.project.key ?? '';
    return [
      { label: 'Summary', icon: 'home', link: `/track/${k}/summary` }, { label: 'Timeline', icon: 'timeline', link: `/track/${k}/timeline` },
      { label: 'Backlog', icon: 'backlog', link: `/track/${k}/backlog` }, { label: 'Board', icon: 'board', link: `/track/${k}/board` },
      { label: 'List', icon: 'list', link: `/track/${k}/list` }, { label: 'Reports', icon: 'chart', link: `/track/${k}/reports` },
      { label: 'Project settings', icon: 'settings', link: `/track/${k}/settings` },
    ];
  });

  constructor() {
    this.people.load();
    effect(() => { this.key(); this.trackApi.changed(); this.load(); });
  }

  async load() {
    try {
      const i = await this.trackApi.issue(this.key());
      this.issue.set(i);
      this.summary = i.summary;
      this.labelsText = i.labels.join(', ');
      this.hub.viewed({ app: 'track', kind: i.type, key: i.key, title: i.summary, subtitle: `Track · ${i.project.name}`, link: `/track/issue/${i.key}` });
      this.trackApi.sprints(i.project.key).then(s => this.sprints.set(s.filter(x => x.state !== 'CLOSED')));
      this.hub.isStarred(`/track/issue/${i.key}`).then(s => this.starred.set(s));
      this.api.get<LinkedWork[]>(`/api/code/linked/${i.key}`).then(l => this.linked.set(l)).catch(() => this.linked.set([]));
    } catch {
      this.toasts.error(`${this.key()} was not found`);
      this.router.navigateByUrl('/track');
    }
  }

  private async patch(body: Record<string, unknown>) {
    const r = await this.api.quiet(this.trackApi.update(this.key(), body));
    if (r) { this.issue.set(r); this.labelsText = r.labels.join(', '); }
    return r;
  }

  async saveSummary() {
    this.editingSummary.set(false);
    if (this.summary.trim() && this.summary !== this.issue()?.summary) await this.patch({ summary: this.summary });
  }

  editDesc() { this.description = this.issue()?.description ?? ''; this.editingDesc.set(true); }
  async saveDesc() { await this.patch({ description: this.description }); this.editingDesc.set(false); }

  async setStatus(s: string) { this.statusOpen.set(false); await this.patch({ status: s }); this.trackApi.touch(); }
  set(field: string, value: unknown) {
    if (value === null || value === '') return this.patch({ clear: [field === 'assigneeId' ? 'assignee' : field === 'sprintId' ? 'sprint' : field] });
    return this.patch({ [field]: value });
  }
  saveLabels() { return this.patch({ labels: this.labelsText.split(',').map(s => s.trim()).filter(Boolean) }); }

  async toggleCheck(idx: number) {
    const list = this.issue()!.checklist.map((c, i) => i === idx ? { ...c, done: !c.done } : c);
    await this.patch({ checklist: list });
  }
  async addCheck() {
    if (!this.newCheck.trim()) return;
    await this.patch({ checklist: [...this.issue()!.checklist, { text: this.newCheck.trim(), done: false }] });
    this.newCheck = '';
  }
  async removeCheck(idx: number) { await this.patch({ checklist: this.issue()!.checklist.filter((_, i) => i !== idx) }); }

  async comment() {
    if (!this.newComment.trim()) return;
    const c = await this.api.quiet(this.trackApi.comment(this.key(), this.newComment));
    if (c) { this.issue.update(i => i && { ...i, comments: [...i.comments, c] }); this.newComment = ''; }
  }
  startEditComment(id: number, body: string) { this.editingComment = id; this.commentDraft = body; }
  async saveComment(id: number) {
    const c = await this.api.quiet(this.trackApi.editComment(id, this.commentDraft));
    if (c) this.issue.update(i => i && { ...i, comments: i.comments.map(x => x.id === id ? c : x) });
    this.editingComment = null;
  }
  async deleteComment(id: number) {
    await this.api.quiet(this.trackApi.deleteComment(id));
    this.issue.update(i => i && { ...i, comments: i.comments.filter(x => x.id !== id) });
  }
  commentHtml(body: string) { return markdown(body); }

  async star() {
    const i = this.issue()!;
    this.starred.set(await this.hub.toggleStar({ app: 'track', kind: i.type, title: `${i.key} ${i.summary}`, link: `/track/issue/${i.key}` }));
  }

  copyLink() { navigator.clipboard?.writeText(location.href); this.toasts.info('Link copied'); }

  async remove() {
    const k = this.issue()!.project.key;
    if (await this.api.quiet(this.trackApi.remove(this.key())) !== null) {
      this.toasts.info(`${this.key()} deleted`);
      this.trackApi.touch();
      this.router.navigate(['/track', k, 'backlog']);
    }
  }

  childClosed() { this.addingChild.set(false); this.load(); }

  historyText(h: { field: string; from?: string; to?: string }) {
    if (h.field === 'created') return 'created the issue';
    if (h.field === 'description') return 'updated the description';
    const fmt = (v?: string) => v ? (/^[A-Z_]+$/.test(v) ? label(v) : v) : 'None';
    return `changed ${h.field} from ${fmt(h.from)} to ${fmt(h.to)}`;
  }
}
