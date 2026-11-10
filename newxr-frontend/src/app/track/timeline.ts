import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { shortDate } from '../core/format';
import { Empty, Icon, IssueType } from '../shared/ui';
import { EpicView, TrackApi } from './track-api';

type Zoom = 'weeks' | 'months' | 'quarters';

/** Epics on a time axis, with today's line and progress from child issues. */
@Component({
  selector: 'nx-timeline',
  imports: [RouterLink, Empty, Icon, IssueType],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [`.wrap{overflow-x:auto} .tl{min-width:860px}
    .grid-row{display:grid;grid-template-columns:260px 1fr;border-bottom:1px solid var(--border);min-height:40px;align-items:center}
    .grid-row.head{font:600 11.5px var(--font);color:var(--text-muted);min-height:34px}
    .name{display:flex;align-items:center;gap:8px;padding:0 12px;min-width:0;border-right:1px solid var(--border);height:100%}
    .lane{position:relative;height:100%;min-height:40px}
    .tick{position:absolute;top:0;bottom:0;border-left:1px solid var(--grid);font-size:11px;padding:8px 6px;color:var(--text-subtle);white-space:nowrap}
    .bar{position:absolute;top:9px;height:22px;border-radius:6px;display:flex;align-items:center;padding:0 8px;color:#fff;font:600 11px var(--font);text-decoration:none!important;min-width:24px;overflow:hidden}
    .bar .fill{position:absolute;inset:0 auto 0 0;background:rgba(255,255,255,.22)} .bar span{position:relative}
    .today{position:absolute;top:0;bottom:0;width:2px;background:var(--danger);z-index:2}
    .seg button{border:0;background:none;padding:4px 10px;border-radius:6px;font:500 12px var(--font);color:var(--text-muted);cursor:pointer}
    .seg button.on{background:var(--accent-soft);color:var(--accent-hover)}`],
  template: `<div class="page">
    <div class="crumbs"><a routerLink="/track">Projects</a><span class="sep">/</span><span>{{ key() }}</span></div>
    <div class="title-bar"><h1>Timeline</h1>
      <div class="actions"><div class="seg">@for (z of zooms; track z) { <button [class.on]="zoom() === z" (click)="zoom.set(z)">{{ z[0].toUpperCase() + z.slice(1) }}</button> }</div>
        <button class="btn" [routerLink]="[]" [queryParams]="{ create: 'issue' }"><nx-icon name="plus" /> Create epic</button></div></div>
    @if (epics().length) {
    <div class="card flush wrap"><div class="tl">
      <div class="grid-row head"><div class="name">Epic</div><div class="lane">@for (t of view().ticks; track t.x) { <div class="tick" [style.left.%]="t.x">{{ t.label }}</div> }</div></div>
      @for (e of epics(); track e.key) {
        <div class="grid-row"><a class="name" [routerLink]="['/track/issue', e.key]"><nx-itype type="EPIC" /><span class="key">{{ e.key }}</span><span class="truncate" style="color:var(--text)">{{ e.summary }}</span></a>
          <div class="lane">@for (t of view().ticks; track t.x) { <div class="tick" [style.left.%]="t.x" style="padding:0"></div> }
            @if (view().today != null) { <div class="today" [style.left.%]="view().today"></div> }
            @if (bar(e); as b) {
              <a class="bar" [routerLink]="['/track/issue', e.key]" [style.left.%]="b.left" [style.width.%]="b.width" [style.background]="b.color"
                [title]="e.summary + ' · ' + shortDate(e.startDate) + ' – ' + shortDate(e.dueDate) + ' · ' + b.pct + '% done'">
                <div class="fill" [style.width.%]="b.pct"></div><span>{{ b.pct }}%</span></a>
            } @else { <span class="small subtle" style="padding-left:12px">No dates — set start and due dates on the epic</span> }
          </div></div>
      }
    </div></div>
    } @else { <div class="card"><nx-empty icon="timeline" title="No epics yet" text="Create an epic with start and due dates to plan it here." /></div> }
  </div>`,
})
export class Timeline {
  private trackApi = inject(TrackApi);
  readonly key = input.required<string>();
  readonly epics = signal<EpicView[]>([]);
  readonly zoom = signal<Zoom>('months');
  readonly zooms: Zoom[] = ['weeks', 'months', 'quarters'];
  shortDate = shortDate;
  private colors = ['#5a4bf5', '#2f9a57', '#e8892b', '#d9466f', '#2f6fde', '#0e9f8e', '#7c3aed'];

  constructor() { effect(() => { this.key(); this.trackApi.changed(); this.trackApi.timeline(this.key()).then(e => this.epics.set(e)); }); }

  readonly view = computed(() => {
    const day = 86400000;
    const span = { weeks: 56, months: 180, quarters: 365 }[this.zoom()];
    const start = new Date(Date.now() - span * 0.3 * day);
    start.setDate(1);
    const end = new Date(start.getTime() + span * day);
    const pos = (d: Date) => (d.getTime() - start.getTime()) * 100 / (end.getTime() - start.getTime());
    const ticks: { x: number; label: string }[] = [];
    const t = new Date(start);
    while (t < end) {
      ticks.push({ x: pos(t), label: this.zoom() === 'weeks' ? t.toLocaleDateString(undefined, { day: 'numeric', month: 'short' })
        : t.toLocaleDateString(undefined, { month: 'short', year: t.getMonth() === 0 ? 'numeric' : undefined }) });
      if (this.zoom() === 'weeks') t.setDate(t.getDate() + 7); else t.setMonth(t.getMonth() + (this.zoom() === 'quarters' ? 3 : 1));
    }
    const today = pos(new Date());
    return { start, end, pos, ticks, today: today >= 0 && today <= 100 ? today : null };
  });

  bar(e: EpicView) {
    if (!e.startDate || !e.dueDate) return null;
    const v = this.view();
    const l = Math.max(0, v.pos(new Date(e.startDate)));
    const r = Math.min(100, v.pos(new Date(e.dueDate)));
    if (r <= 0 || l >= 100) return null;
    const idx = this.epics().indexOf(e);
    return { left: l, width: Math.max(1.5, r - l), color: this.colors[idx % this.colors.length], pct: e.childCount ? Math.round(e.childDone * 100 / e.childCount) : (e.status === 'DONE' ? 100 : 0) };
  }
}
