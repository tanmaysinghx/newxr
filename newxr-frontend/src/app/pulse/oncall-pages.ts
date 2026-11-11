import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiError } from '../core/api';
import { dateTime } from '../core/format';
import { Toasts } from '../core/toasts';
import { Avatar, Icon, Modal } from '../shared/ui';
import { ALERT_PRIORITIES, PulseApi, Schedule, ShiftView, StepView } from './pulse-api';
import { PulseContext } from './pulse-pages';

const DAY = 86400000;

@Component({
  selector: 'nx-pulse-oncall',
  imports: [FormsModule, RouterLink, Avatar, Icon, Modal],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './oncall-page.scss',
  templateUrl: './oncall-page.html',
})
export class PulseOnCall {
  private pulseApi = inject(PulseApi);
  private toasts = inject(Toasts);
  readonly ctx = inject(PulseContext);
  readonly s = signal<Schedule | null>(null);
  readonly week = signal<string | undefined>(undefined);
  readonly dialog = signal<'override' | 'page' | 'message' | null>(null);
  readonly priorities = ALERT_PRIORITIES;
  dateTime = dateTime;
  ov = { userId: null as number | null, startsAt: '', endsAt: '', reason: '' };
  pg = { message: '', priority: 'P2' };

  readonly days = computed(() => {
    const s = this.s(); if (!s) return [];
    const from = new Date(s.from).getTime();
    const today = new Date(s.now).toISOString().slice(0, 10);
    return Array.from({ length: 7 }, (_, i) => {
      const d = new Date(from + i * DAY);
      return { label: d.toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', timeZone: 'UTC' }), today: d.toISOString().slice(0, 10) === today };
    });
  });
  /** Position of "now" across the week, in percent, or null when another week is shown. */
  readonly nowPct = computed(() => {
    const s = this.s(); if (!s) return null;
    const p = this.pct(s.now);
    return p > 0 && p < 100 ? p : null;
  });
  readonly rows = computed(() => {
    const s = this.s(); if (!s) return [];
    return [
      ...s.layers.map((l, i) => ({ name: l.name, shifts: l.shifts, tone: i === 0 ? 'primary' : 'secondary' })),
      { name: 'Overrides', shifts: s.overrides.map(o => ({ start: o.startsAt, end: o.endsAt, user: o.user, override: true }) as ShiftView), tone: 'override' },
      { name: 'Final schedule', shifts: s.finalShifts, tone: 'final' },
    ];
  });

  constructor() {
    effect(() => { const k = this.ctx.key(); const w = this.week(); if (k) this.load(k, w); });
  }
  async load(k: string, w?: string) { this.s.set(await this.pulseApi.schedule(k, w)); }
  pct(iso: string) {
    const s = this.s()!; const a = new Date(s.from).getTime(), b = new Date(s.to).getTime();
    return Math.max(0, Math.min(100, (new Date(iso).getTime() - a) / (b - a) * 100));
  }
  move(weeks: number) {
    const from = weeks === 0 ? undefined : new Date(new Date(this.s()!.from).getTime() + weeks * 7 * DAY).toISOString().slice(0, 10);
    this.week.set(from);
  }
  title(sh: ShiftView) { return `${sh.user?.name ?? 'Nobody'} · ${dateTime(sh.start)} – ${dateTime(sh.end)}${sh.override ? ' (override)' : ''}`; }
  icsUrl() { return `/api/pulse/teams/${this.ctx.key()}/oncall.ics`; }

  openOverride() {
    const start = new Date(Math.ceil(Date.now() / 3600000) * 3600000);
    this.ov = { userId: null, startsAt: this.local(start), endsAt: this.local(new Date(start.getTime() + 8 * 3600000)), reason: '' };
    this.dialog.set('override');
  }
  private local(d: Date) { const z = new Date(d.getTime() - d.getTimezoneOffset() * 60000); return z.toISOString().slice(0, 16); }
  async saveOverride() {
    try {
      await this.pulseApi.addOverride(this.ctx.key(), { userId: this.ov.userId, reason: this.ov.reason,
        startsAt: new Date(this.ov.startsAt).toISOString(), endsAt: new Date(this.ov.endsAt).toISOString() });
      this.dialog.set(null); this.toasts.info('Override added'); this.load(this.ctx.key(), this.week()); this.ctx.touch();
    } catch (e) { this.toasts.error((e as ApiError).message); }
  }
  async removeOverride(id: number) {
    try { await this.pulseApi.removeOverride(this.ctx.key(), id); this.load(this.ctx.key(), this.week()); this.ctx.touch(); }
    catch (e) { this.toasts.error((e as ApiError).message); }
  }
  async send() {
    const mode = this.dialog();
    try {
      if (mode === 'page') { await this.pulseApi.page(this.ctx.key(), this.pg); this.toasts.info(`Paged ${this.s()?.current?.user?.name}`); this.ctx.touch(); }
      else { const r = await this.pulseApi.message(this.ctx.key(), { message: this.pg.message }); this.toasts.info(`Message sent to ${r.sentTo}`); }
      this.dialog.set(null); this.pg = { message: '', priority: 'P2' };
    } catch (e) { this.toasts.error((e as ApiError).message); }
  }
}

// ---- escalation policy -------------------------------------------------------------

interface DraftStep { target: string; userId: number | null; delayMinutes: number; }

@Component({
  selector: 'nx-pulse-escalations',
  imports: [FormsModule, RouterLink, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [`.chain{display:flex;align-items:stretch;gap:8px;flex-wrap:wrap} .step{flex:1;min-width:170px;background:var(--surface-hover);border-radius:8px;padding:10px 12px}
    .arrow{display:flex;align-items:center;color:var(--text-subtle)} .edit{display:grid;grid-template-columns:28px 200px 200px 140px 32px;gap:8px;align-items:center;padding:8px 0;border-bottom:1px solid var(--border)}
    @media(max-width:800px){.edit{grid-template-columns:28px 1fr;}}`],
  template: `<div class="page narrow">
    <div class="crumbs"><a routerLink="/pulse">Operations</a><span class="sep">/</span><span>{{ ctx.team()?.name }}</span></div>
    <div class="title-bar"><div><h1>Escalations</h1><div class="sub">Who gets notified, and when, while an alert stays unacknowledged.</div></div></div>
    <div class="card"><h3>Escalation policy · {{ ctx.team()?.name }}</h3>
      <div class="chain mt-8">@for (s of saved(); track s.id; let last = $last) {
        <div class="step"><div class="tiny subtle">STEP {{ s.position + 1 }}</div><div class="small strong">{{ s.label.split(' · ')[0] }}</div><div class="tiny muted">{{ s.label.split(' · ')[1] }}</div></div>
        @if (!last) { <span class="arrow"><nx-icon name="arrow-right" [size]="14" /></span> }
      } @empty { <div class="small subtle">No policy: alerts only notify their owner.</div> }</div>
    </div>
    <div class="card"><h3>Edit steps</h3>
      @for (d of draft; track $index; let i = $index) {
        <div class="edit"><b class="small">{{ i + 1 }}</b>
          <select class="input" [(ngModel)]="d.target" [attr.aria-label]="'Step ' + (i + 1) + ' target'"><option value="ON_CALL_PRIMARY">On-call primary</option><option value="ON_CALL_SECONDARY">On-call secondary</option>
            <option value="USER">A person</option><option value="TEAM">Whole team</option></select>
          @if (d.target === 'USER') { <select class="input" [(ngModel)]="d.userId" aria-label="Person"><option [ngValue]="null">Pick someone</option>
            @for (u of ctx.team()?.members ?? []; track u.id) { <option [ngValue]="u.id">{{ u.name }}</option> }</select> } @else { <span></span> }
          <div class="row"><input class="input" type="number" min="0" max="1440" [(ngModel)]="d.delayMinutes" style="width:80px" aria-label="Delay in minutes" /><span class="small muted">min</span></div>
          <button class="btn ghost icon" (click)="draft.splice(i, 1)" aria-label="Remove step"><nx-icon name="x" /></button></div>
      }
      <div class="row mt-8"><button class="btn" (click)="draft.push({ target: 'TEAM', userId: null, delayMinutes: (draft.at(-1)?.delayMinutes ?? 0) + 15 })"><nx-icon name="plus" /> Add step</button>
        <span class="grow"></span><button class="btn primary" (click)="save()">Save policy</button></div>
      <div class="small muted mt-8">Delays count from when the alert was raised. Notifications are in-app; email and SMS need a provider, which Newxr doesn't have yet.</div>
    </div>
  </div>`,
})
export class PulseEscalations {
  private pulseApi = inject(PulseApi);
  private toasts = inject(Toasts);
  readonly ctx = inject(PulseContext);
  readonly saved = signal<StepView[]>([]);
  draft: DraftStep[] = [];
  constructor() { effect(() => { const k = this.ctx.key(); if (k) this.pulseApi.escalation(k).then(s => this.set(s)); }); }
  private set(s: StepView[]) { this.saved.set(s); this.draft = s.map(x => ({ target: x.target, userId: x.user?.id ?? null, delayMinutes: x.delayMinutes })); }
  async save() {
    try { this.set(await this.pulseApi.saveEscalation(this.ctx.key(), this.draft)); this.toasts.info('Escalation policy saved'); }
    catch (e) { this.toasts.error((e as ApiError).message); }
  }
}
