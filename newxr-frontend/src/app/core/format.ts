/** "5 min ago", "Yesterday", "14 Nov" */
export function ago(iso?: string | null): string {
  if (!iso) return '—';
  const t = new Date(iso).getTime();
  const s = Math.round((Date.now() - t) / 1000);
  if (s < 45) return 'Just now';
  if (s < 3600) return `${Math.round(s / 60)} min ago`;
  if (s < 86400) return `${Math.round(s / 3600)} h ago`;
  if (s < 172800) return 'Yesterday';
  if (s < 604800) return `${Math.round(s / 86400)} d ago`;
  return shortDate(iso);
}

export function shortDate(iso?: string | null): string {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString(undefined, { day: 'numeric', month: 'short' });
}

export function longDate(iso?: string | null): string {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' });
}

export function dateTime(iso?: string | null): string {
  if (!iso) return '—';
  return new Date(iso).toLocaleString(undefined, { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });
}

export function greeting(): string {
  const h = new Date().getHours();
  return h < 12 ? 'Good morning' : h < 18 ? 'Good afternoon' : 'Good evening';
}

/** Minutes → "38 min", "3h 10m", "2d 4h" */
export function duration(mins: number): string {
  const m = Math.abs(Math.round(mins));
  if (m < 60) return `${m} min`;
  if (m < 1440) return `${Math.floor(m / 60)}h ${m % 60 ? (m % 60) + 'm' : ''}`.trim();
  return `${Math.floor(m / 1440)}d ${Math.floor((m % 1440) / 60)}h`;
}

export const STATUS_TONE: Record<string, string> = {
  TODO: '', BACKLOG: '', OPEN: '', IN_PROGRESS: 'info', IN_REVIEW: 'violet', DONE: 'success', BLOCKED: 'danger',
  WAITING_FOR_CUSTOMER: 'warning', WAITING_FOR_SUPPORT: 'info', RESOLVED: 'success', ESCALATED: 'danger',
  MERGED: 'violet', APPROVED: 'success', NEEDS_WORK: 'warning', DECLINED: 'danger', PASSED: 'success', FAILED: 'danger',
  RUNNING: 'info', ON_TRACK: 'success', AT_RISK: 'warning', OFF_TRACK: 'danger', PAUSED: '', COMPLETED: 'violet',
  ACKNOWLEDGED: 'warning', ACKED: 'warning', CLOSED: '', OPERATIONAL: 'success', DEGRADED: 'warning', PARTIAL_OUTAGE: 'orange',
  MAJOR_OUTAGE: 'danger', OUTAGE: 'danger', ADMIN: 'violet', MEMBER: '', GUEST: 'warning', ACTIVE: 'success', INVITED: 'info',
  DEACTIVATED: '', DRAFT: '', PUBLISHED: 'success', P1: 'danger', P2: 'orange', P3: 'warning', P4: 'info', P5: '',
  INVESTIGATING: 'danger', IDENTIFIED: 'warning', MONITORING: 'info', FUTURE: '', CANCELLED: '',
  SUCCEEDED: 'success', LOCKED: 'danger', NEEDS_REVIEW: 'violet', PENDING: '', QUEUED: '', STOPPED: '', COMMITTED: '',
};

export function label(v?: string | null): string {
  if (!v) return '';
  if (/^P\d$/.test(v)) return v;
  const s = v.replace(/_/g, ' ').toLowerCase();
  return s.charAt(0).toUpperCase() + s.slice(1);
}
