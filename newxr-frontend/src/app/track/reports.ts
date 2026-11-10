import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { label } from '../core/format';
import { Avatar, Icon } from '../shared/ui';
import { Report, TrackApi } from './track-api';

const W = 560, H = 220, PL = 34, PR = 12, PT = 12, PB = 26;

/** Sprint report: tiles, burndown, velocity, status breakdown and workload. */
@Component({
  selector: 'nx-reports',
  imports: [FormsModule, RouterLink, Avatar, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './reports.scss',
  templateUrl: './reports.html',
})
export class Reports {
  private trackApi = inject(TrackApi);
  private route = inject(ActivatedRoute);
  readonly key = input.required<string>();
  readonly r = signal<Report | null>(null);
  readonly showTable = signal(false);
  readonly burnHover = signal<number | null>(null);
  readonly velHover = signal<number | null>(null);
  readonly W = W; readonly H = H; readonly PL = PL; readonly PB = PB; readonly PT = PT;
  label = label;
  sprintId: number | null = null;

  constructor() {
    this.sprintId = Number(this.route.snapshot.queryParamMap.get('sprint')) || null;
    effect(() => { this.key(); this.load(); });
  }

  async load() {
    const r = await this.trackApi.report(this.key(), this.sprintId);
    this.r.set(r);
    if (!this.sprintId && r.sprint) this.sprintId = r.sprint.id;
  }

  readonly pctDone = computed(() => { const r = this.r(); return r && r.committedPoints ? Math.round(r.completedPoints * 100 / r.committedPoints) : 0; });
  readonly cycleDelta = computed(() => { const r = this.r(); return r?.avgCycleDays != null && r.prevAvgCycleDays != null ? Math.round((r.avgCycleDays - r.prevAvgCycleDays) * 10) / 10 : null; });

  // ---- burndown ----
  readonly burn = computed(() => {
    const pts = this.r()?.burndown ?? [];
    const max = Math.max(1, ...pts.map(p => Math.max(p.value, p.guide)));
    const yMax = niceMax(max);
    const n = Math.max(1, pts.length - 1);
    const x = (i: number) => PL + (W - PL - PR) * i / n;
    const y = (v: number) => PT + (H - PT - PB) * (1 - v / yMax);
    const actual = pts.map((p, i) => ({ i, v: p.value })).filter(p => p.v >= 0);
    return {
      yMax, x, y, pts,
      ticks: [0, yMax / 2, yMax].map(v => ({ v: Math.round(v), y: y(v) })),
      guide: pts.map((p, i) => `${i ? 'L' : 'M'}${x(i).toFixed(1)},${y(p.guide).toFixed(1)}`).join(' '),
      line: actual.map((p, k) => `${k ? 'L' : 'M'}${x(p.i).toFixed(1)},${y(p.v).toFixed(1)}`).join(' '),
      last: actual.at(-1),
      xLabels: pts.map((p, i) => ({ i, label: p.label, x: x(i) })).filter((_, i, a) => i === 0 || i === a.length - 1 || i % Math.ceil(a.length / 5) === 0),
    };
  });

  burnMove(e: PointerEvent, svg: Element) {
    const b = this.burn();
    if (!b.pts.length) return;
    const rect = svg.getBoundingClientRect();
    const px = (e.clientX - rect.left) * W / rect.width;
    const n = Math.max(1, b.pts.length - 1);
    const i = Math.round((px - PL) / ((W - PL - PR) / n));
    this.burnHover.set(Math.max(0, Math.min(b.pts.length - 1, i)));
  }

  // ---- velocity ----
  readonly vel = computed(() => {
    const v = this.r()?.velocity ?? [];
    const yMax = niceMax(Math.max(1, ...v.flatMap(b => [b.committed, b.completed])));
    const band = (W - PL - PR) / Math.max(1, v.length);
    const bw = Math.min(18, band / 3.2);
    const y = (n: number) => PT + (H - PT - PB) * (1 - n / yMax);
    return {
      yMax, band, bw, y, base: H - PB,
      ticks: [0, yMax / 2, yMax].map(t => ({ v: Math.round(t), y: y(t) })),
      bars: v.map((b, i) => {
        const cx = PL + band * i + band / 2;
        return { ...b, i, cx, x1: cx - bw - 1, x2: cx + 1, y1: y(b.committed), y2: y(b.completed) };
      }),
    };
  });

  // ---- status & workload ----
  readonly statusSegs = computed(() => {
    const c = this.r()?.statusCounts ?? {};
    const total = Object.values(c).reduce((a, b) => a + b, 0) || 1;
    const colors: Record<string, string> = { TODO: 'var(--border-strong)', IN_PROGRESS: 'var(--info)', IN_REVIEW: 'var(--ai)', DONE: 'var(--success)' };
    return Object.entries(c).map(([k, n]) => ({ k, n, pct: n * 100 / total, color: colors[k] }));
  });
  readonly maxLoad = computed(() => Math.max(1, ...(this.r()?.workload ?? []).map(w => w.points)));

  path(d: string) { return d; }
}

function niceMax(v: number) {
  const steps = [5, 10, 20, 25, 40, 50, 60, 80, 100, 150, 200, 250, 500, 1000];
  return steps.find(s => s >= v) ?? Math.ceil(v / 100) * 100;
}
