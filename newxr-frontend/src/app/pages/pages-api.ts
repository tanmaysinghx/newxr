import { Injectable, inject, signal } from '@angular/core';
import { Api } from '../core/api';
import { UserRef } from '../core/models';

export interface SpaceView { id: number; key: string; name: string; description?: string; color: string; type: string; owner: UserRef | null; linkedProject?: string; pageCount: number; archived: boolean; }
export interface TreeNode { id: number; title: string; status: string; children: TreeNode[]; }
export interface PageSummary { id: number; title: string; kind: string; status: string; lastEditor: UserRef | null; updatedAt: string; excerpt?: string; }
export interface Overview { space: SpaceView; tree: TreeNode[]; pinned: PageSummary[]; recent: PageSummary[]; contributors: UserRef[]; }
export interface Reaction { emoji: string; count: number; mine: boolean; }
export interface PageComment { id: number; author: UserRef | null; body: string; createdAt: string; }
export interface PageView {
  id: number; title: string; body?: string; draftTitle?: string; draftBody?: string; hasDraft: boolean; kind: string; status: string; version: number;
  author: UserRef | null; lastEditor: UserRef | null; createdAt: string; updatedAt: string; publishedAt?: string; parentId?: number; pinned: boolean;
  space: SpaceView; crumbs: { id: number; title: string }[]; reactions: Reaction[]; comments: PageComment[]; readMinutes: number;
}
export interface VersionView { id: number; version: number; title: string; author: UserRef | null; createdAt: string; }

export const TEMPLATES: { name: string; description: string; icon: string; title: string; body: string }[] = [
  { name: 'Blank page', description: 'Start from scratch', icon: 'page', title: 'Untitled', body: '' },
  { name: 'Meeting notes', description: 'Agenda, notes and action items', icon: 'calendar', title: 'Meeting notes',
    body: '**Date:** \n**Attendees:** \n\n## Agenda\n- \n\n## Notes\n- \n\n## Action items\n- [ ] ' },
  { name: 'Retrospective', description: 'What went well, what to improve', icon: 'refresh', title: 'Sprint retrospective',
    body: '## What went well\n- \n\n## What could be better\n- \n\n## Action items\n- [ ] ' },
  { name: 'Decision', description: 'Record a decision and why', icon: 'check-circle', title: 'Decision: ',
    body: '| Field | Value |\n| --- | --- |\n| Status | In progress |\n| Owner | |\n| Due date | |\n\n## Background\n\n## Options considered\n1. \n2. \n\n## Outcome\n' },
  { name: 'How-to article', description: 'Step-by-step instructions', icon: 'book', title: 'How to ',
    body: '> [!info] Use this article when …\n\n## Steps\n1. \n2. \n3. \n\n## Related articles\n- ' },
  { name: 'Technical design', description: 'Context, design, rollout', icon: 'code', title: 'Technical design: ',
    body: '## Context\n\n## Goals and non-goals\n\n## Design\n\n## Rollout\n| Phase | Audience | Date | Status |\n| --- | --- | --- | --- |\n| | | | |\n' },
];

@Injectable({ providedIn: 'root' })
export class PagesApi {
  private api = inject(Api);
  readonly treeChanged = signal(0);
  touch() { this.treeChanged.update(n => n + 1); }

  spaces(archived = false) { return this.api.get<SpaceView[]>('/api/pages/spaces', { archived }); }
  createSpace(b: unknown) { return this.api.post<SpaceView>('/api/pages/spaces', b); }
  updateSpace(key: string, b: unknown) { return this.api.put<SpaceView>(`/api/pages/spaces/${key}`, b); }
  archive(key: string, archived: boolean) { return this.api.post<SpaceView>(`/api/pages/spaces/${key}/archive?archived=${archived}`); }
  overview(key: string) { return this.api.get<Overview>(`/api/pages/spaces/${key}`); }
  tree(key: string) { return this.api.get<TreeNode[]>(`/api/pages/spaces/${key}/tree`); }
  blog(key: string) { return this.api.get<PageSummary[]>(`/api/pages/spaces/${key}/blog`); }
  page(id: number) { return this.api.get<PageView>(`/api/pages/${id}`); }
  create(b: unknown) { return this.api.post<PageView>('/api/pages', b); }
  save(id: number, b: unknown) { return this.api.put<PageView>(`/api/pages/${id}`, b); }
  discard(id: number) { return this.api.post<PageView>(`/api/pages/${id}/discard`); }
  move(id: number, b: unknown) { return this.api.post<TreeNode[]>(`/api/pages/${id}/move`, b); }
  remove(id: number) { return this.api.delete(`/api/pages/${id}`); }
  versions(id: number) { return this.api.get<VersionView[]>(`/api/pages/${id}/versions`); }
  restore(id: number, v: number) { return this.api.post<PageView>(`/api/pages/${id}/versions/${v}/restore`); }
  comment(id: number, body: string) { return this.api.post<PageComment>(`/api/pages/${id}/comments`, { body }); }
  deleteComment(id: number) { return this.api.delete(`/api/pages/comments/${id}`); }
  react(id: number, emoji: string) { return this.api.post<Reaction[]>(`/api/pages/${id}/reactions`, { emoji }); }
}
