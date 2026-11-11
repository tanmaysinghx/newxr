import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink, RouterOutlet } from '@angular/router';
import { ApiError } from '../core/api';
import { Auth } from '../core/auth';
import { ago, dateTime, label } from '../core/format';
import { Toasts } from '../core/toasts';
import { Avatar, Empty, Icon, Logo, Status } from '../shared/ui';
import { DeskApi, DeskSummary, HelpHome, RequestRow, RequestView, TypeView } from './desk-api';

/** Customer-facing frame: its own slim header, no app chrome. */
@Component({
  selector: 'nx-help-frame',
  imports: [RouterOutlet, RouterLink, Logo, Avatar],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [`:host{display:block;min-height:100vh;background:var(--surface)}
    header{display:flex;align-items:center;gap:10px;height:52px;padding:0 24px;border-bottom:1px solid var(--border);background:var(--surface)}
    header a{color:var(--text);font-size:13px} header .hc{color:var(--text-muted);font-size:13px}
    nav{margin-left:auto;display:flex;align-items:center;gap:18px}`],
  template: `<header><a routerLink="/help" aria-label="Help center home"><nx-logo [mark]="22" /></a><span class="hc">Help center</span>
      <nav><a routerLink="/help/requests">My requests</a>@if (auth.canUse('desk')) { <a routerLink="/desk">Agent view</a> }
        <a routerLink="/profile" [attr.aria-label]="auth.me()?.name"><nx-avatar [user]="auth.me()" [size]="26" /></a></nav></header>
    <router-outlet />`,
})
export class HelpFrame {
  readonly auth = inject(Auth);
}

const firstName = (n?: string | null) => (n ?? '').split(' ')[0];

@Component({
  selector: 'nx-help-home',
  imports: [FormsModule, RouterLink, Icon, Status, Empty],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './help-center.scss',
  template: `<div class="hero"><h1>Hi {{ first() }}, how can we help?</h1>
      <div class="search"><nx-icon name="search" /><input class="input" [(ngModel)]="q" placeholder="Search for help articles or request types" aria-label="Search request types" /></div></div>
    <div class="wrap">
      @if (q) {
        <h2>Request types matching “{{ q }}”</h2>
        <div class="card flush">@for (t of matches(); track t.id) { <a class="type-row" [routerLink]="['/help', t.deskKey]" [queryParams]="{ type: t.id }">
            <span class="ti"><nx-icon [name]="t.icon" [size]="15" /></span><div class="grow"><div class="small strong">{{ t.name }}</div><div class="tiny muted">{{ t.deskName }} · {{ t.description }}</div></div><nx-icon name="chevron-right" [size]="14" /></a>
          } @empty { <nx-empty icon="search" title="No request types match" text="Pick a service project below and describe your problem." /> }</div>
      }
      <h2>Service projects</h2>
      <div class="desks">@for (d of home()?.desks ?? []; track d.id) {
        <a class="card desk" [routerLink]="['/help', d.key]"><span class="di" [style.background]="d.color"><nx-icon [name]="d.icon" [size]="18" /></span>
          <div class="strong small">{{ d.name }}</div><div class="tiny muted">{{ d.description }}</div></a>
      }</div>
      <div class="two">
        <div class="card flush"><div class="ch"><h3>Popular request types</h3></div>
          @for (t of home()?.popular ?? []; track t.id) { <a class="type-row" [routerLink]="['/help', t.deskKey]" [queryParams]="{ type: t.id }">
            <span class="ti"><nx-icon [name]="t.icon" [size]="15" /></span><div class="grow"><div class="small strong">{{ t.name }}</div><div class="tiny muted">{{ t.description }}</div></div><nx-icon name="chevron-right" [size]="14" /></a> }
        </div>
        <div class="card flush"><div class="ch"><h3>Your recent requests</h3><a class="small" routerLink="/help/requests">View all</a></div>
          @for (r of home()?.mine ?? []; track r.key) { <a class="req-row" [routerLink]="['/help/requests', r.key]">
            <div class="grow"><div class="small">{{ r.summary }}</div><div class="tiny subtle">{{ r.key }}</div></div><nx-status [value]="r.status" /></a>
          } @empty { <div class="small subtle" style="padding:16px">You haven't raised any requests yet.</div> }
        </div>
      </div>
    </div>`,
})
export class HelpHomePage {
  readonly auth = inject(Auth);
  readonly home = signal<HelpHome | null>(null);
  readonly types = signal<TypeView[]>([]);
  q = '';
  readonly first = computed(() => firstName(this.auth.me()?.name) || 'there');
  constructor() {
    const api = inject(DeskApi);
    api.help().then(async h => {
      this.home.set(h);
      const all = await Promise.all(h.desks.map(d => api.helpTypes(d.key)));
      this.types.set(all.flat());
    });
  }
  matches() {
    const q = this.q.trim().toLowerCase();
    return this.types().filter(t => (t.name + ' ' + (t.description ?? '') + ' ' + (t.category ?? '')).toLowerCase().includes(q));
  }
}

@Component({
  selector: 'nx-help-desk',
  imports: [FormsModule, RouterLink, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './help-center.scss',
  template: `<div class="wrap narrow">
    <div class="crumbs"><a routerLink="/help">Help center</a><span class="sep">/</span><span>{{ desk()?.name }}</span></div>
    <h1 class="mt">{{ desk()?.name }}</h1><div class="muted small">{{ desk()?.description }}</div>
    @if (!chosen()) {
      <h2>What can we help you with?</h2>
      <div class="card flush">@for (t of types(); track t.id) {
        <button class="type-row" (click)="pick(t.id)"><span class="ti"><nx-icon [name]="t.icon" [size]="15" /></span>
          <div class="grow"><div class="small strong">{{ t.name }}</div><div class="tiny muted">{{ t.description }}</div></div><nx-icon name="chevron-right" [size]="14" /></button>
      }</div>
    } @else {
      <div class="card form">
        <div class="row"><span class="ti"><nx-icon [name]="chosen()!.icon" [size]="15" /></span><div class="grow"><div class="strong">{{ chosen()!.name }}</div><div class="tiny muted">{{ chosen()!.description }}</div></div>
          <button class="btn sm ghost" (click)="pick(null)">Change</button></div>
        @if (error()) { <div class="banner danger"><nx-icon name="alert" /><div>{{ error() }}</div></div> }
        <div class="field"><label for="hs">Summary <span class="req">*</span></label><input id="hs" class="input" [(ngModel)]="summary" placeholder="Tell us in a few words" /></div>
        <div class="field"><label for="hd">Description</label><textarea id="hd" class="input" rows="6" [(ngModel)]="description" placeholder="What happened? What have you tried?"></textarea></div>
        <div class="field"><label for="ho">Office or team</label><input id="ho" class="input" [(ngModel)]="organization" placeholder="Lisbon office" /></div>
        <div class="row"><span class="grow"></span><a class="btn ghost" routerLink="/help">Cancel</a><button class="btn primary" (click)="send()" [disabled]="sending()">Send</button></div>
      </div>
    }
  </div>`,
})
export class HelpDesk {
  private api = inject(DeskApi);
  private router = inject(Router);
  readonly desk = signal<DeskSummary | null>(null);
  readonly types = signal<TypeView[]>([]);
  readonly error = signal<string | null>(null);
  readonly sending = signal(false);
  readonly key = input.required<string>();
  readonly type = input<string | undefined>();
  readonly chosen = computed(() => this.types().find(t => String(t.id) === this.type()) ?? null);
  summary = ''; description = ''; organization = '';
  constructor() {
    effect(() => {
      const k = this.key();
      this.api.helpTypes(k).then(t => this.types.set(t)).catch(() => this.router.navigateByUrl('/help'));
      this.api.desks().then(ds => this.desk.set(ds.find(d => d.key.toLowerCase() === k.toLowerCase()) ?? null)).catch(() => null);
    });
  }
  pick(id: number | null) { this.router.navigate([], { queryParams: { type: id } }); }
  async send() {
    this.sending.set(true);
    try {
      const r = await this.api.helpRaise(this.key(), { typeId: this.chosen()?.id, summary: this.summary, description: this.description, organization: this.organization });
      this.router.navigate(['/help/requests', r.key]);
    } catch (e) { this.error.set((e as ApiError).message); }
    finally { this.sending.set(false); }
  }
}

@Component({
  selector: 'nx-help-requests',
  imports: [RouterLink, Status, Empty],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './help-center.scss',
  template: `<div class="wrap narrow">
    <div class="crumbs"><a routerLink="/help">Help center</a><span class="sep">/</span><span>My requests</span></div>
    <h1 class="mt">My requests</h1>
    <div class="card flush">@for (r of rows(); track r.key) {
      <a class="req-row" [routerLink]="['/help/requests', r.key]"><div class="grow"><div class="small">{{ r.summary }}</div>
        <div class="tiny subtle">{{ r.key }} · {{ r.type ?? 'Request' }} · {{ ago(r.createdAt) }}</div></div><nx-status [value]="r.status" /></a>
    } @empty { <nx-empty icon="inbox" title="No requests yet" text="Raise one from the help center home." /> }</div>
  </div>`,
})
export class HelpRequests {
  readonly rows = signal<RequestRow[]>([]);
  ago = ago;
  constructor() { inject(DeskApi).myRequests().then(r => this.rows.set(r)); }
}

@Component({
  selector: 'nx-help-request',
  imports: [FormsModule, RouterLink, Avatar, Icon, Status],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './help-center.scss',
  template: `@if (r(); as r) { <div class="wrap narrow">
    <div class="crumbs"><a routerLink="/help">Help center</a><span class="sep">/</span><a routerLink="/help/requests">My requests</a><span class="sep">/</span><span>{{ r.key }}</span></div>
    <div class="row mt"><h1 class="grow">{{ r.summary }}</h1><nx-status [value]="r.status" /></div>
    <div class="tiny subtle">{{ r.key }} · {{ r.deskName }}@if (r.type) { · {{ r.type.name }} } · raised {{ dateTime(r.createdAt) }}</div>
    @if (r.status === 'WAITING_FOR_CUSTOMER') { <div class="banner warning"><nx-icon name="message" /><div>The team is waiting for your reply.</div></div> }
    <div class="card"><div class="desc">{{ r.description || 'No description.' }}</div></div>
    <h2>Activity</h2>
    <div class="card">
      @for (c of r.comments; track c.id) {
        <div class="cm"><nx-avatar [user]="c.author" [size]="26" /><div class="grow"><div class="small"><b>{{ c.author?.name }}</b>&nbsp;<span class="subtle">{{ ago(c.createdAt) }}</span></div>
          <div class="desc">{{ c.body }}</div></div></div>
      } @empty { <div class="small subtle">No replies yet. We'll let you know as soon as someone picks this up.</div> }
      @if (r.status !== 'CLOSED') {
        <div class="field mt"><label for="hr">Add a comment</label><textarea id="hr" class="input" rows="3" [(ngModel)]="body"></textarea></div>
        <div class="row"><span class="grow"></span>
          @if (!done(r.status)) { <button class="btn ghost" (click)="resolve()">This is resolved</button> }
          <button class="btn primary" (click)="send()">{{ done(r.status) ? 'Reopen with comment' : 'Comment' }}</button></div>
      }
    </div>
    @if (done(r.status)) {
      <div class="card"><div class="small strong">How did we do?</div>
        <div class="row mt">@for (n of [1, 2, 3, 4, 5]; track n) {
          <button class="btn sm" [class.primary]="r.csat === n" (click)="rate(n)" [attr.aria-label]="n + ' out of 5'">{{ n }}</button> }
          @if (r.csat) { <span class="small muted">Thanks for rating {{ r.csat }} / 5.</span> }</div></div>
    }
  </div> }`,
})
export class HelpRequest {
  private api = inject(DeskApi);
  private toasts = inject(Toasts);
  private router = inject(Router);
  readonly key = input.required<string>();
  readonly r = signal<RequestView | null>(null);
  ago = ago; dateTime = dateTime; label = label;
  body = '';
  constructor() { effect(() => { this.api.helpRequest(this.key()).then(r => this.r.set(r)).catch(() => this.router.navigateByUrl('/help/requests')); }); }
  done(s: string) { return s === 'RESOLVED' || s === 'CLOSED'; }
  async send() {
    try { this.r.set(await this.api.helpComment(this.key(), this.body)); this.body = ''; }
    catch (e) { this.toasts.error((e as ApiError).message); }
  }
  async resolve() { try { this.r.set(await this.api.helpResolve(this.key())); } catch (e) { this.toasts.error((e as ApiError).message); } }
  async rate(n: number) { try { this.r.set(await this.api.rate(this.key(), n)); } catch (e) { this.toasts.error((e as ApiError).message); } }
}
