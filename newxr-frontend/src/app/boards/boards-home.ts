import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ApiError } from '../core/api';
import { AppFrame } from '../shell/shell';
import { Sidebar } from '../shell/sidebar';
import { Avatars, Icon, Modal } from '../shared/ui';
import { BACKGROUNDS, BoardSummary, BoardsApi, bg } from './boards-api';

@Component({
  selector: 'nx-boards-home',
  imports: [FormsModule, RouterLink, AppFrame, Sidebar, Avatars, Icon, Modal],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [`.tile{height:110px;border-radius:10px;padding:12px 14px;color:#fff;display:flex;flex-direction:column;justify-content:space-between;text-decoration:none!important;box-shadow:var(--shadow-1);transition:transform .12s}
    .tile:hover{transform:translateY(-2px)} .tile .n{font-weight:700;font-size:15px} .new{border:1px dashed var(--border-strong);background:var(--surface);color:var(--text-muted);align-items:center;justify-content:center;cursor:pointer;font:500 13px var(--font)}
    .bgs{display:flex;gap:8px;flex-wrap:wrap} .bgs button{width:44px;height:30px;border-radius:6px;border:0;cursor:pointer;outline-offset:2px}`],
  template: `<nx-app-frame>
    <nx-sidebar [context]="{ title: 'Newxr Boards', subtitle: 'Workspace', app: 'boards' }" [items]="[{ label: 'Boards', icon: 'kanban', link: '/boards', exact: true }]" />
    <div class="page">
      <div class="title-bar"><div><h1>Boards</h1><div class="sub">Visual boards for anything — launches, hiring, events.</div></div>
        <div class="actions"><button class="btn primary" (click)="creating.set(true)"><nx-icon name="plus" /> Create board</button></div></div>
      <div class="grid grid-4">
        @for (b of boards(); track b.id) {
          <a class="tile" [routerLink]="['/boards', b.id]" [style.background]="bg(b.background)"><span class="n">{{ b.name }}</span>
            <div class="row between"><nx-avatars [users]="b.members" [size]="22" [max]="4" /><span class="small">{{ b.cardCount }} cards</span></div></a>
        }
        <button class="tile new" (click)="creating.set(true)"><nx-icon name="plus" /> Create new board</button>
      </div>
    </div>
  </nx-app-frame>
  @if (creating()) {
    <nx-modal title="Create board" (closed)="creating.set(false)">
      <div class="modal-body">
        @if (error()) { <div class="banner danger"><nx-icon name="alert" /><div>{{ error() }}</div></div> }
        <div style="height:90px;border-radius:10px" [style.background]="bg(form.background)"></div>
        <div class="field"><label>Background</label><div class="bgs">@for (b of backgrounds; track b) {
          <button [style.background]="bg(b)" (click)="form.background = b" [style.outline]="form.background === b ? '2px solid var(--text)' : 'none'" [attr.aria-label]="b"></button> }</div></div>
        <div class="field"><label>Board title <span class="req">*</span></label><input class="input" [(ngModel)]="form.name" (keydown.enter)="create()" autofocus /></div>
        <div class="field"><label>Visibility</label><select class="input" [(ngModel)]="form.visibility">
          <option value="WORKSPACE">Workspace — everyone can see it</option><option value="PRIVATE">Private — members only</option></select></div>
      </div>
      <div class="modal-foot"><button class="btn ghost" (click)="creating.set(false)">Cancel</button><button class="btn primary" (click)="create()">Create</button></div>
    </nx-modal>
  }`,
})
export class BoardsHome {
  private boardsApi = inject(BoardsApi);
  private router = inject(Router);
  readonly boards = signal<BoardSummary[]>([]);
  readonly creating = signal(false);
  readonly error = signal<string | null>(null);
  readonly backgrounds = BACKGROUNDS;
  bg = bg;
  form = { name: '', background: BACKGROUNDS[0], visibility: 'WORKSPACE' };
  constructor() {
    this.boardsApi.list().then(b => this.boards.set(b));
    inject(ActivatedRoute).queryParamMap.subscribe(q => { if (q.get('create')) this.creating.set(true); });
  }
  async create() {
    this.error.set(null);
    try { const b = await this.boardsApi.create(this.form); this.router.navigate(['/boards', b.id]); }
    catch (e) { this.error.set((e as ApiError).message); }
  }
}
