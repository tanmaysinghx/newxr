import { Injectable, inject, signal } from '@angular/core';
import { Api } from '../core/api';
import { UserRef } from '../core/models';

export interface ProjectView { id: number; key: string; name: string; description?: string; type: string; lead: UserRef | null; color: string; issueCount: number; activeSprint?: string; }
export interface SprintView { id: number; name: string; goal?: string; state: 'FUTURE' | 'ACTIVE' | 'CLOSED'; startDate?: string; endDate?: string; issueCount: number; points: number; donePoints: number; committedPoints?: number; completedPoints?: number; }
export interface IssueRow { id: number; key: string; type: string; summary: string; status: string; priority: string; assignee: UserRef | null; points?: number; labels: string[]; sprintId?: number; epicKey?: string; epicSummary?: string; rank: number; updatedAt: string; dueDate?: string; childCount: number; childDone: number; }
export interface ChecklistItem { text: string; done: boolean; }
export interface CommentView { id: number; author: UserRef | null; body: string; edited: boolean; createdAt: string; }
export interface HistoryView { actor: UserRef | null; field: string; from?: string; to?: string; at: string; }
export interface IssueDetail {
  id: number; key: string; type: string; summary: string; description?: string; status: string; priority: string;
  assignee: UserRef | null; reporter: UserRef | null; points?: number; labels: string[]; sprintId?: number; sprintName?: string;
  fixVersion?: string; startDate?: string; dueDate?: string; parentKey?: string; parentSummary?: string; parentType?: string;
  checklist: ChecklistItem[]; children: { key: string; type: string; summary: string; status: string; assignee: UserRef | null }[];
  comments: CommentView[]; history: HistoryView[]; project: ProjectView; createdAt: string; updatedAt: string;
}
export interface BoardView { project: ProjectView; sprint: SprintView | null; columns: Record<string, IssueRow[]>; people: UserRef[]; }
export interface BacklogView { project: ProjectView; sprints: { sprint: SprintView; issues: IssueRow[] }[]; backlog: IssueRow[]; epics: IssueRow[]; }
export interface EpicView { key: string; summary: string; status: string; startDate?: string; dueDate?: string; childCount: number; childDone: number; assignee: UserRef | null; }
export interface Report {
  sprint: SprintView | null; sprints: SprintView[]; committedPoints: number; completedPoints: number; committedIssues: number;
  addedPoints: number; addedIssues: number; avgCycleDays?: number; prevAvgCycleDays?: number;
  burndown: { label: string; value: number; guide: number }[]; velocity: { sprint: string; committed: number; completed: number }[];
  statusCounts: Record<string, number>; workload: { user: UserRef; points: number; issues: number }[];
}

export const STATUSES = ['TODO', 'IN_PROGRESS', 'IN_REVIEW', 'DONE'];
export const TYPES = ['STORY', 'BUG', 'TASK', 'EPIC', 'SUBTASK'];
export const PRIORITIES = ['HIGHEST', 'HIGH', 'MEDIUM', 'LOW', 'LOWEST'];

@Injectable({ providedIn: 'root' })
export class TrackApi {
  private api = inject(Api);
  /** Bumped after any create/update so open views reload. */
  readonly changed = signal(0);
  touch() { this.changed.update(n => n + 1); }

  projects() { return this.api.get<ProjectView[]>('/api/track/projects'); }
  project(key: string) { return this.api.get<ProjectView>(`/api/track/projects/${key}`); }
  createProject(b: unknown) { return this.api.post<ProjectView>('/api/track/projects', b); }
  updateProject(key: string, b: unknown) { return this.api.put<ProjectView>(`/api/track/projects/${key}`, b); }
  board(key: string, f: Record<string, unknown> = {}) { return this.api.get<BoardView>(`/api/track/projects/${key}/board`, f as never); }
  backlog(key: string, f: Record<string, unknown> = {}) { return this.api.get<BacklogView>(`/api/track/projects/${key}/backlog`, f as never); }
  issues(key: string, f: Record<string, unknown> = {}) { return this.api.get<IssueRow[]>(`/api/track/projects/${key}/issues`, f as never); }
  timeline(key: string) { return this.api.get<EpicView[]>(`/api/track/projects/${key}/timeline`); }
  report(key: string, sprint?: number | null) { return this.api.get<Report>(`/api/track/projects/${key}/report`, { sprint }); }
  sprints(key: string) { return this.api.get<SprintView[]>(`/api/track/projects/${key}/sprints`); }
  createSprint(key: string, b: unknown = {}) { return this.api.post<SprintView>(`/api/track/projects/${key}/sprints`, b); }
  startSprint(id: number, b: unknown = {}) { return this.api.post<SprintView>(`/api/track/sprints/${id}/start`, b); }
  completeSprint(id: number, moveTo: string) { return this.api.post<SprintView>(`/api/track/sprints/${id}/complete`, { moveTo }); }
  deleteSprint(id: number) { return this.api.delete(`/api/track/sprints/${id}`); }
  issue(key: string) { return this.api.get<IssueDetail>(`/api/track/issues/${key}`); }
  create(b: unknown) { return this.api.post<IssueDetail>('/api/track/issues', b); }
  update(key: string, b: unknown) { return this.api.patch<IssueDetail>(`/api/track/issues/${key}`, b); }
  move(key: string, b: unknown) { return this.api.post<IssueRow>(`/api/track/issues/${key}/move`, b); }
  remove(key: string) { return this.api.delete(`/api/track/issues/${key}`); }
  comment(key: string, body: string) { return this.api.post<CommentView>(`/api/track/issues/${key}/comments`, { body }); }
  editComment(id: number, body: string) { return this.api.put<CommentView>(`/api/track/comments/${id}`, { body }); }
  deleteComment(id: number) { return this.api.delete(`/api/track/comments/${id}`); }
}
