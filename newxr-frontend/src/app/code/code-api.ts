import { Injectable, inject } from '@angular/core';
import { Api } from '../core/api';
import { UserRef } from '../core/models';

export interface RepoSummary { id: number; namespace: string; name: string; slug: string; description?: string; defaultBranch: string; language?: string; openPulls: number; branches: number; lastCommitMessage?: string; updatedAt: string; lastPipelineStatus?: string; }
export interface RepoView { repo: RepoSummary; watchers: UserRef[]; watching: boolean; branchNames: string[]; ciConfigured: boolean; ciTokenHint?: string; }
export interface CommitView { sha: string; message: string; author: UserRef | null; branch: string; additions: number; deletions: number; paths?: string; at: string; checks?: string; }
export interface Entry { name: string; path: string; dir: boolean; lastCommitMessage?: string; lastCommitAt?: string; lastCommitSha?: string; }
export interface Tree { branch: string; path: string; entries: Entry[]; readmePath?: string; readme?: string; lastCommit?: CommitView; }
export interface FileView { branch: string; path: string; content: string; lines: number; lastCommitSha?: string; lastCommitMessage?: string; lastCommitAt?: string; }
export interface BranchView { name: string; baseBranch?: string; isDefault: boolean; head?: CommitView; createdBy?: UserRef | null; updatedAt: string; openPull?: number; pipelineStatus?: string; }
export interface ReviewerView { user: UserRef; state: string; }
export interface PullRow { number: number; title: string; sourceBranch: string; targetBranch: string; author: UserRef | null; status: string; reviewers: ReviewerView[]; comments: number; createdAt: string; updatedAt: string; checks?: string; }
export interface DiffLine { type: ' ' | '+' | '-'; oldNo?: number; newNo?: number; text: string; }
export interface Hunk { oldStart: number; newStart: number; lines: DiffLine[]; }
export interface FileDiff { path: string; additions: number; deletions: number; added: boolean; conflict: boolean; tooLarge: boolean; hunks: Hunk[]; }
export interface PullComment { id: number; author: UserRef | null; body: string; path?: string; line?: number; at: string; }
export interface PullView {
  number: number; repoSlug: string; repoName: string; title: string; description?: string; sourceBranch: string; targetBranch: string; author: UserRef | null;
  status: string; createdAt: string; updatedAt: string; closedBy?: UserRef | null; closedAt?: string; mergeSha?: string; reviewers: ReviewerView[];
  comments: PullComment[]; commits: CommitView[]; files: FileDiff[]; checks?: { pipeline: number; status: string; stages: { name: string; status: string; durationSeconds?: number }[] };
  issues: { key: string; summary: string; status: string }[]; mergeable: boolean; conflicts: string[]; additions: number; deletions: number;
}
export interface PipelineRow { number: number; branch: string; commitSha?: string; commitMessage?: string; status: string; triggeredBy?: UserRef | null; triggeredByName?: string; startedAt?: string; finishedAt?: string; durationSeconds?: number; url?: string; }
export interface StageView { name: string; status: string; durationSeconds?: number; log?: string; }
export interface DeploymentView { id: number; environment: string; status: string; branch?: string; commitSha?: string; version?: string; pipelineNumber?: number; deployedBy?: string; url?: string; at: string; }

@Injectable({ providedIn: 'root' })
export class CodeApi {
  private api = inject(Api);
  repos() { return this.api.get<RepoSummary[]>('/api/code/repos'); }
  createRepo(b: unknown) { return this.api.post<RepoView>('/api/code/repos', b); }
  repo(slug: string) { return this.api.get<RepoView>(`/api/code/repos/${slug}`); }
  editRepo(slug: string, b: unknown) { return this.api.put<RepoView>(`/api/code/repos/${slug}`, b); }
  watch(slug: string) { return this.api.post<RepoView>(`/api/code/repos/${slug}/watch`); }
  tree(slug: string, branch?: string, path = '') { return this.api.get<Tree>(`/api/code/repos/${slug}/tree`, { branch, path }); }
  file(slug: string, path: string, branch?: string) { return this.api.get<FileView>(`/api/code/repos/${slug}/file`, { branch, path }); }
  save(slug: string, b: { branch?: string; path: string; content: string; message: string; newBranch?: string }) { return this.api.put<CommitView>(`/api/code/repos/${slug}/file`, b); }
  branches(slug: string) { return this.api.get<BranchView[]>(`/api/code/repos/${slug}/branches`); }
  createBranch(slug: string, name: string, from?: string) { return this.api.post<BranchView>(`/api/code/repos/${slug}/branches`, { name, from }); }
  deleteBranch(slug: string, name: string) { return this.api.delete(`/api/code/repos/${slug}/branches?name=${encodeURIComponent(name)}`); }
  commits(slug: string, branch?: string) { return this.api.get<CommitView[]>(`/api/code/repos/${slug}/commits`, { branch }); }

  pulls(slug: string, state = 'OPEN') { return this.api.get<PullRow[]>(`/api/code/repos/${slug}/pulls`, { state }); }
  createPull(slug: string, b: unknown) { return this.api.post<PullView>(`/api/code/repos/${slug}/pulls`, b); }
  pull(slug: string, n: number) { return this.api.get<PullView>(`/api/code/repos/${slug}/pulls/${n}`); }
  editPull(slug: string, n: number, b: unknown) { return this.api.put<PullView>(`/api/code/repos/${slug}/pulls/${n}`, b); }
  review(slug: string, n: number, state: string) { return this.api.post<PullView>(`/api/code/repos/${slug}/pulls/${n}/review`, { state }); }
  comment(slug: string, n: number, b: { body: string; path?: string; line?: number }) { return this.api.post<PullView>(`/api/code/repos/${slug}/pulls/${n}/comments`, b); }
  merge(slug: string, n: number) { return this.api.post<PullView>(`/api/code/repos/${slug}/pulls/${n}/merge`); }
  decline(slug: string, n: number) { return this.api.post<PullView>(`/api/code/repos/${slug}/pulls/${n}/decline`); }
  reopen(slug: string, n: number) { return this.api.post<PullView>(`/api/code/repos/${slug}/pulls/${n}/reopen`); }
  updateBranch(slug: string, n: number) { return this.api.post<{ updated: number; pull: PullView }>(`/api/code/repos/${slug}/pulls/${n}/update-branch`); }
  resolve(slug: string, n: number, path: string) { return this.api.post<PullView>(`/api/code/repos/${slug}/pulls/${n}/resolve`, { path }); }

  pipelines(slug: string, branch?: string) { return this.api.get<PipelineRow[]>(`/api/code/repos/${slug}/pipelines`, { branch }); }
  pipeline(slug: string, n: number) { return this.api.get<{ pipeline: PipelineRow; stages: StageView[] }>(`/api/code/repos/${slug}/pipelines/${n}`); }
  stop(slug: string, n: number) { return this.api.post<{ pipeline: PipelineRow; stages: StageView[] }>(`/api/code/repos/${slug}/pipelines/${n}/stop`); }
  deployments(slug: string) { return this.api.get<DeploymentView[]>(`/api/code/repos/${slug}/deployments`); }
  ciToken(slug: string) { return this.api.post<{ webhookPath: string; tokenHint: string }>(`/api/code/repos/${slug}/ci-token`); }
}
