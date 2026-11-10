import { Injectable, inject } from '@angular/core';
import { Api } from '../core/api';
import { UserRef } from '../core/models';

export interface BoardSummary { id: number; name: string; description?: string; background: string; visibility: string; owner: UserRef | null; members: UserRef[]; cardCount: number; }
export interface CardSummary { id: number; listId: number; title: string; labels: string[]; cover?: string; due?: string; dueDone: boolean; members: UserRef[]; checkDone: number; checkTotal: number; comments: number; hasDescription: boolean; position: number; }
export interface ListView { id: number; name: string; position: number; cards: CardSummary[]; }
export interface BoardView { board: BoardSummary; lists: ListView[]; }
export interface CardCheck { text: string; done: boolean; }
export interface CardView {
  id: number; boardId: number; boardName: string; listId: number; listName: string; title: string; description?: string; labels: string[];
  cover?: string; due?: string; dueDone: boolean; members: UserRef[]; checklist: CardCheck[]; linkedIssue?: string; linkedPage?: number;
  comments: { id: number; author: UserRef | null; body: string; createdAt: string }[]; createdAt: string;
}

const LABEL_COLORS: Record<string, string> = { design: '#d9466f', copy: '#e8892b', launch: '#5a4bf5', social: '#0e9f8e', legal: '#2f6fde', blocked: '#d23b3b', bug: '#d23b3b' };
const PALETTE = ['#5a4bf5', '#0e9f8e', '#e8892b', '#2f6fde', '#d9466f', '#2f9a57', '#7c3aed'];
export function labelColor(name: string) {
  const k = name.toLowerCase();
  if (LABEL_COLORS[k]) return LABEL_COLORS[k];
  let h = 0; for (const ch of k) h = (h * 31 + ch.charCodeAt(0)) | 0;
  return PALETTE[Math.abs(h) % PALETTE.length];
}
export function bg(v?: string | null) {
  if (!v) return 'var(--neutral-soft)';
  const [a, b] = v.split(',');
  return b ? `linear-gradient(135deg, ${a}, ${b})` : a;
}
export const BACKGROUNDS = ['#6d3ce0,#d9466f', '#5a4bf5,#2f6fde', '#0e9f8e,#2f6fde', '#e8892b,#d9466f', '#2f9a57,#0e9f8e', '#14122b,#2b2470', '#d9466f,#e8892b'];

@Injectable({ providedIn: 'root' })
export class BoardsApi {
  private api = inject(Api);
  list() { return this.api.get<BoardSummary[]>('/api/boards'); }
  create(b: unknown) { return this.api.post<BoardSummary>('/api/boards', b); }
  board(id: number) { return this.api.get<BoardView>(`/api/boards/${id}`); }
  update(id: number, b: unknown) { return this.api.put<BoardSummary>(`/api/boards/${id}`, b); }
  addMember(id: number, userId: number) { return this.api.post<BoardSummary>(`/api/boards/${id}/members/${userId}`); }
  archive(id: number) { return this.api.delete(`/api/boards/${id}`); }
  createList(id: number, name: string) { return this.api.post<ListView>(`/api/boards/${id}/lists`, { name }); }
  renameList(id: number, name: string) { return this.api.put(`/api/boards/lists/${id}`, { name }); }
  archiveList(id: number) { return this.api.delete(`/api/boards/lists/${id}`); }
  createCard(listId: number, title: string) { return this.api.post<CardSummary>('/api/boards/cards', { listId, title }); }
  card(id: number) { return this.api.get<CardView>(`/api/boards/cards/${id}`); }
  updateCard(id: number, b: unknown) { return this.api.patch<CardView>(`/api/boards/cards/${id}`, b); }
  moveCard(id: number, b: unknown) { return this.api.post<CardSummary>(`/api/boards/cards/${id}/move`, b); }
  archiveCard(id: number) { return this.api.delete(`/api/boards/cards/${id}`); }
  comment(id: number, body: string) { return this.api.post<CardView['comments'][number]>(`/api/boards/cards/${id}/comments`, { body }); }
}
