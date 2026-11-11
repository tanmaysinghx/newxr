import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink, RouterOutlet } from '@angular/router';
import { ApiError } from '../core/api';
import { Auth } from '../core/auth';
import { ago, dateTime, label } from '../core/format';
import { APPS, appInfo } from '../core/models';
import { People } from '../core/people';
import { Toasts } from '../core/toasts';
import { AppFrame } from '../shell/shell';
import { Sidebar } from '../shell/sidebar';
import { AppTile, Avatar, Avatars, Empty, Icon, Modal, Status } from '../shared/ui';
import { AdminApi, AuditView, GroupView, LinkResult, Overview, ProductView, SecurityView, UserRow } from './admin-api';

@Component({
  selector: 'nx-admin-layout',
  imports: [RouterOutlet, AppFrame, Sidebar],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<nx-app-frame>
    <nx-sidebar [context]="{ title: auth.options()?.organization ?? 'Organization', subtitle: 'Administration', icon: 'shield' }"
      [items]="[
        { label: 'Overview', icon: 'home', link: '/admin', exact: true }, { label: 'Users', icon: 'user', link: '/admin/users' },
        { label: 'Groups', icon: 'users', link: '/admin/groups' }, { label: 'Products', icon: 'grid', link: '/admin/products' },
        { label: 'Security', icon: 'shield', link: '/admin/security' }, { label: 'Audit log', icon: 'list', link: '/admin/audit' }]"
      [groups]="[{ title: 'Settings', items: [
        { label: 'Organization', icon: 'settings', link: '/admin/settings/organization' }, { label: 'Sign-in', icon: 'lock', link: '/admin/settings/sign-in' },
        { label: 'Database', icon: 'server', link: '/admin/settings/database' }, { label: 'Kafka', icon: 'bolt', link: '/admin/settings/kafka' }] }]" />
    <router-outlet /></nx-app-frame>`,
})
export class AdminLayout {
  readonly auth = inject(Auth);
  constructor() { if (!this.auth.options()) this.auth.loadOptions().catch(() => null); }
}

/** Shows a one-time invitation or reset link with a copy button. */
@Component({
  selector: 'nx-link-dialog',
  imports: [Modal, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<nx-modal [title]="title()" (closed)="closed.emit()">
    <div class="modal-body">
      <div class="small">Send this link to <b>{{ result().user.email }}</b>. It works once and expires {{ dateTime(result().expiresAt) }}.</div>
      <div class="banner warning"><nx-icon name="lock" /><div>Newxr doesn't send email yet, and won't show this link again. Copy it now and share it privately.</div></div>
      <pre class="mono" style="background:var(--surface-hover);border:1px solid var(--border);border-radius:8px;padding:10px 12px;white-space:pre-wrap;word-break:break-all;margin:0">{{ url() }}</pre>
    </div>
    <div class="modal-foot"><button class="btn" (click)="copy()"><nx-icon name="link" /> Copy link</button><button class="btn primary" (click)="closed.emit()">Done</button></div>
  </nx-modal>`,
})
export class LinkDialog {
  private toasts = inject(Toasts);
  readonly result = input.required<LinkResult>();
  readonly title = input('Invitation link');
  readonly closed = output<void>();
  readonly url = computed(() => location.origin + this.result().link);
  dateTime = dateTime;
  copy() { navigator.clipboard?.writeText(this.url()).then(() => this.toasts.info('Link copied')).catch(() => this.toasts.error('Copy failed — select the link instead.')); }
}

// ---- overview ------------------------------------------------------------------------

@Component({
  selector: 'nx-admin-overview',
  imports: [RouterLink, Icon, AppTile],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="page">
    <div class="crumbs"><span>Administration</span></div>
    <div class="title-bar"><div><h1>Overview</h1><div class="sub">People, products and how this Newxr is set up.</div></div>
      <div class="actions"><a class="btn primary" routerLink="/admin/users" [queryParams]="{ invite: 1 }"><nx-icon name="plus" /> Invite users</a></div></div>
    @if (o(); as o) {
      <div class="grid grid-4">
        <a class="card stat" routerLink="/admin/users"><div class="stat-label"><nx-icon name="users" [size]="14" /> Active users</div><div class="stat-value">{{ o.active }}</div>
          <div class="stat-note success-text">+{{ o.newThisMonth }} in the last 30 days</div></a>
        <a class="card stat" routerLink="/admin/users" [queryParams]="{ status: 'INVITED' }"><div class="stat-label"><nx-icon name="mail" [size]="14" /> Pending invites</div><div class="stat-value">{{ o.invited }}</div>
          <div class="stat-note muted">Accounts that haven't signed in yet</div></a>
        <a class="card stat" routerLink="/admin/security"><div class="stat-label"><nx-icon name="shield" [size]="14" /> Administrators</div><div class="stat-value">{{ o.admins }}</div>
          <div class="stat-note" [class.danger-text]="o.locked > 0" [class.muted]="!o.locked">{{ o.locked }} locked account{{ o.locked === 1 ? '' : 's' }}</div></a>
        <a class="card stat" routerLink="/admin/users" [queryParams]="{ role: 'GUEST' }"><div class="stat-label"><nx-icon name="user" [size]="14" /> Guests</div><div class="stat-value">{{ o.guests }}</div>
          <div class="stat-note muted">External collaborators</div></a>
      </div>
      <div class="grid grid-2">
        <div class="card"><div class="card-head"><h3>Setup</h3></div>
          <dl class="kv"><dt>Sign-in</dt><dd><a routerLink="/admin/settings/sign-in">{{ o.signIn }}</a></dd>
            <dt>Database</dt><dd><a routerLink="/admin/settings/database">{{ o.database }}</a></dd>
            <dt>Kafka events</dt><dd><a routerLink="/admin/settings/kafka">{{ o.kafka ? 'Publishing' : 'Off (events stay in-process)' }}</a></dd>
            <dt>Products</dt><dd><a routerLink="/admin/products">{{ enabled(o) }} of {{ o.products.length }} enabled</a></dd></dl>
          <div class="row wrap mt-8">@for (p of o.products; track p.code) { @if (p.enabled) { <nx-app-tile [app]="p.code" [size]="22" /> } }</div></div>
        <div class="card flush"><div class="card-head" style="padding:14px 16px 6px;margin:0"><h3>Recent activity</h3><a class="small" routerLink="/admin/audit">Audit log</a></div>
          @for (e of o.recent; track e.id) { <div class="act"><span class="grow small"><b>{{ e.actorName ?? e.actorEmail ?? 'Someone' }}</b> {{ verb(e.action) }} {{ e.target }}</span><span class="tiny subtle nowrap">{{ ago(e.at) }}</span></div> }
          @empty { <div class="small subtle" style="padding:14px 16px">Nothing yet.</div> }</div>
      </div>
    }
  </div>`,
  styles: [`.stat{color:var(--text)} .stat:hover{text-decoration:none;border-color:var(--border-strong)} .kv{display:grid;grid-template-columns:120px 1fr;gap:8px 12px;margin:0;font-size:13px}
    .kv dt{color:var(--text-muted)} .kv dd{margin:0} .act{display:flex;gap:10px;align-items:center;padding:8px 16px;border-top:1px solid var(--border)}`],
})
export class AdminOverview {
  readonly o = signal<Overview | null>(null);
  ago = ago; verb = verb;
  constructor() { inject(AdminApi).overview().then(o => this.o.set(o)); }
  enabled(o: Overview) { return o.products.filter(p => p.enabled).length; }
}

const VERBS: Record<string, string> = {
  'user.invited': 'invited', 'user.updated': 'updated', 'user.deactivated': 'deactivated', 'user.reactivated': 'reactivated', 'user.unlocked': 'unlocked',
  'user.reset-link': 'issued a password reset link for', 'user.invite-resent': 'resent the invitation to', 'user.invite-revoked': 'revoked the invitation for',
  'user.invite-accepted': 'accepted the invitation as', 'user.password-reset': 'reset the password for', 'group.created': 'created group', 'group.updated': 'updated group',
  'group.deleted': 'deleted group', 'product.enabled': 'enabled', 'product.disabled': 'disabled', 'settings.organization': 'changed organization settings',
  'settings.sign-in': 'changed sign-in settings', 'settings.kafka': 'changed Kafka settings', 'signin.password': 'signed in with a password as',
  'signin.portal': 'signed in with Portal SSO as', 'signin.failed': 'failed to sign in as', 'signin.locked': 'was locked out as',
};
export const verb = (a: string) => VERBS[a] ?? a;

// ---- users -------------------------------------------------------------------------------

@Component({
  selector: 'nx-admin-users',
  imports: [FormsModule, Avatar, Icon, Status, AppTile, Modal, LinkDialog],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './admin-users.scss',
  templateUrl: './admin-users.html',
})
export class AdminUsers {
  private adminApi = inject(AdminApi);
  private toasts = inject(Toasts);
  readonly auth = inject(Auth);
  readonly rows = signal<UserRow[]>([]);
  readonly products = signal<ProductView[]>([]);
  readonly selected = signal<UserRow | null>(null);
  readonly inviting = signal(false);
  readonly link = signal<{ result: LinkResult; title: string } | null>(null);
  readonly pageIdx = signal(0);
  readonly size = 25;
  readonly page = computed(() => this.rows().slice(this.pageIdx() * this.size, (this.pageIdx() + 1) * this.size));
  readonly stats = computed(() => {
    const r = this.rows();
    return { active: r.filter(u => u.status === 'ACTIVE').length, invited: r.filter(u => u.status === 'INVITED').length,
      admins: r.filter(u => u.role === 'ADMIN' && u.status !== 'DEACTIVATED').length, guests: r.filter(u => u.role === 'GUEST' && u.status !== 'DEACTIVATED').length };
  });
  readonly enabledApps = computed(() => this.products().filter(p => p.enabled));
  ago = ago; label = label; min = Math.min; appName = (c: string) => appInfo(c)?.short ?? c;
  q = ''; role = ''; status = ''; app = '';
  inv = { email: '', name: '', role: 'MEMBER', allApps: true, apps: new Set<string>(), jobTitle: '' };
  edit = { name: '', jobTitle: '', role: 'MEMBER', allApps: true, apps: new Set<string>() };
  inviteError = signal<string | null>(null);

  constructor() {
    const qp = new URLSearchParams(location.search);
    this.status = qp.get('status') ?? ''; this.role = qp.get('role') ?? '';
    if (qp.get('invite')) this.openInvite();
    this.load();
    this.adminApi.products().then(p => this.products.set(p));
  }
  async load() {
    this.rows.set(await this.adminApi.users({ q: this.q, role: this.role, status: this.status, app: this.app }));
    this.pageIdx.set(0);
    const s = this.selected();
    if (s) this.selected.set(this.rows().find(r => r.id === s.id) ?? null);
  }
  openInvite() { this.inv = { email: '', name: '', role: 'MEMBER', allApps: true, apps: new Set(APPS.map(a => a.code)), jobTitle: '' }; this.inviteError.set(null); this.inviting.set(true); }
  async invite() {
    try {
      const r = await this.adminApi.invite({ email: this.inv.email, name: this.inv.name, role: this.inv.role, jobTitle: this.inv.jobTitle, apps: this.inv.allApps ? null : [...this.inv.apps] });
      this.inviting.set(false); this.link.set({ result: r, title: 'Invitation link' }); this.load();
    } catch (e) { this.inviteError.set((e as ApiError).message); }
  }
  open(u: UserRow) {
    this.selected.set(u);
    this.edit = { name: u.name, jobTitle: u.jobTitle ?? '', role: u.role, allApps: u.allApps, apps: new Set(u.allApps ? APPS.map(a => a.code) : u.apps) };
  }
  toggleApp(set: Set<string>, code: string) { set.has(code) ? set.delete(code) : set.add(code); }
  private async run(f: () => Promise<UserRow>, msg: string) {
    try { const u = await f(); this.toasts.info(msg); await this.load(); this.selected.set(this.rows().find(r => r.id === u.id) ?? null); }
    catch (e) { this.toasts.error((e as ApiError).message); }
  }
  save() {
    const u = this.selected()!;
    this.run(() => this.adminApi.editUser(u.id, { name: this.edit.name, jobTitle: this.edit.jobTitle, role: this.edit.role, allApps: this.edit.allApps,
      apps: this.edit.allApps ? null : [...this.edit.apps] }), 'Saved');
  }
  act(action: 'deactivate' | 'reactivate' | 'unlock') { const u = this.selected()!; this.run(() => this.adminApi.userAction(u.id, action), `${label(action)}d`); }
  async reset() {
    const u = this.selected()!;
    try { const r = await this.adminApi.resetLink(u.id); this.link.set({ result: r, title: u.status === 'INVITED' ? 'New invitation link' : 'Password reset link' }); }
    catch (e) { this.toasts.error((e as ApiError).message); }
  }
  async revoke() {
    const u = this.selected()!;
    try { await this.adminApi.deleteUser(u.id); this.toasts.info('Invitation revoked'); this.selected.set(null); this.load(); }
    catch (e) { this.toasts.error((e as ApiError).message); }
  }
  exportCsv() {
    const esc = (v: unknown) => `"${String(v ?? '').replace(/"/g, '""')}"`;
    const lines = [['Name', 'Email', 'Role', 'Status', 'Apps', 'Groups', 'Last active'].join(',')]
      .concat(this.rows().map(u => [u.name, u.email, u.role, u.status, u.apps.join(' '), u.groups.join(' '), u.lastActiveAt ?? ''].map(esc).join(',')));
    const a = document.createElement('a');
    a.href = URL.createObjectURL(new Blob([lines.join('\n')], { type: 'text/csv' }));
    a.download = 'newxr-users.csv';
    a.click();
    URL.revokeObjectURL(a.href);
  }
}

// ---- groups --------------------------------------------------------------------------------

@Component({
  selector: 'nx-admin-groups',
  imports: [FormsModule, Avatars, Icon, Modal, Empty],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="page narrow">
    <div class="crumbs"><span>Administration</span></div>
    <div class="title-bar"><div><h1>Groups</h1><div class="sub">Teams of people, for keeping track of who belongs where.</div></div>
      <div class="actions"><button class="btn primary" (click)="open(null)"><nx-icon name="plus" /> Create group</button></div></div>
    <div class="card flush"><div class="table-wrap"><table class="table">
      <thead><tr><th>Group</th><th>Members</th><th></th></tr></thead>
      <tbody>@for (g of rows(); track g.id) {
        <tr class="clickable" (click)="open(g)"><td><div class="strong">{{ g.name }}</div><div class="tiny muted">{{ g.description }}</div></td>
          <td><div class="row"><nx-avatars [users]="g.members" [size]="22" [max]="6" /><span class="small muted">{{ g.members.length }}</span></div></td>
          <td style="width:40px"><button class="btn sm ghost icon" (click)="$event.stopPropagation(); remove(g)" [attr.aria-label]="'Delete ' + g.name"><nx-icon name="x" /></button></td></tr>
      } @empty { <tr><td colspan="3"><nx-empty icon="users" title="No groups yet" /></td></tr> }</tbody></table></div></div>
  </div>
  @if (editing()) {
    <nx-modal [title]="editing()!.id ? 'Edit group' : 'Create group'" (closed)="editing.set(null)">
      <div class="modal-body">
        <div class="field"><label>Name</label><input class="input" [(ngModel)]="f.name" autofocus /></div>
        <div class="field"><label>Description</label><input class="input" [(ngModel)]="f.description" /></div>
        <div class="field"><label>Members</label><div class="members">@for (u of people.all(); track u.id) {
          <label class="row small"><input type="checkbox" [checked]="f.members.has(u.id)" (change)="toggle(u.id)" /> {{ u.name }} <span class="subtle">{{ u.email }}</span></label> }</div></div>
      </div>
      <div class="modal-foot"><button class="btn ghost" (click)="editing.set(null)">Cancel</button><button class="btn primary" (click)="save()">Save</button></div>
    </nx-modal>
  }`,
  styles: ['.members{max-height:280px;overflow:auto;display:flex;flex-direction:column;gap:6px;border:1px solid var(--border);border-radius:8px;padding:10px}'],
})
export class AdminGroups {
  private adminApi = inject(AdminApi);
  private toasts = inject(Toasts);
  readonly people = inject(People);
  readonly rows = signal<GroupView[]>([]);
  readonly editing = signal<{ id: number | null } | null>(null);
  f = { name: '', description: '', members: new Set<number>() };
  constructor() { this.people.load(); this.load(); }
  async load() { this.rows.set(await this.adminApi.groups()); }
  open(g: GroupView | null) {
    this.f = { name: g?.name ?? '', description: g?.description ?? '', members: new Set(g?.members.map(m => m.id) ?? []) };
    this.editing.set({ id: g?.id ?? null });
  }
  toggle(id: number) { this.f.members.has(id) ? this.f.members.delete(id) : this.f.members.add(id); }
  async save() {
    const id = this.editing()!.id;
    const b = { name: this.f.name, description: this.f.description, memberIds: [...this.f.members] };
    try { id ? await this.adminApi.editGroup(id, b) : await this.adminApi.createGroup(b); this.editing.set(null); this.load(); }
    catch (e) { this.toasts.error((e as ApiError).message); }
  }
  async remove(g: GroupView) {
    try { await this.adminApi.deleteGroup(g.id); this.toasts.info(`Deleted ${g.name}`); this.load(); } catch (e) { this.toasts.error((e as ApiError).message); }
  }
}

// ---- products -------------------------------------------------------------------------------

@Component({
  selector: 'nx-admin-products',
  imports: [AppTile],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="page narrow">
    <div class="crumbs"><span>Administration</span></div>
    <div class="title-bar"><div><h1>Products</h1><div class="sub">Turn apps on or off for everyone. Per-person access is set on each user.</div></div></div>
    <div class="card flush">@for (p of rows(); track p.code) {
      <div class="prod"><nx-app-tile [app]="p.code" [size]="32" /><div class="grow"><div class="strong small">{{ p.name }}</div><div class="tiny muted">{{ p.description }}</div></div>
        <span class="small muted nowrap">{{ p.users }} users</span>
        <span class="lozenge" [class.success]="p.enabled">{{ p.enabled ? 'Active' : 'Off' }}</span>
        <button class="btn sm" (click)="toggle(p)">{{ p.enabled ? 'Turn off' : 'Turn on' }}</button></div>
    }</div>
    <div class="small muted">Turning an app off hides it for everyone and keeps its data; turn it back on any time.</div>
  </div>`,
  styles: ['.prod{display:flex;align-items:center;gap:12px;padding:12px 16px;border-bottom:1px solid var(--border)} .prod:last-child{border-bottom:0}'],
})
export class AdminProducts {
  private adminApi = inject(AdminApi);
  private toasts = inject(Toasts);
  readonly rows = signal<ProductView[]>([]);
  constructor() { this.adminApi.products().then(p => this.rows.set(p)); }
  async toggle(p: ProductView) {
    try { this.rows.set(await this.adminApi.toggleProduct(p.code, !p.enabled)); this.toasts.info(`${p.name} turned ${p.enabled ? 'off' : 'on'}. Reload to update the app switcher.`); }
    catch (e) { this.toasts.error((e as ApiError).message); }
  }
}

// ---- security --------------------------------------------------------------------------------

@Component({
  selector: 'nx-admin-security',
  imports: [RouterLink, Avatar, Status],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="page narrow">
    <div class="crumbs"><span>Administration</span></div>
    <div class="title-bar"><div><h1>Security</h1><div class="sub">How people sign in and what protects their accounts.</div></div></div>
    @if (s(); as s) {
      <div class="card"><h3>Sign-in</h3><dl class="kv mt-8">
        <dt>Built-in passwords</dt><dd>{{ s.builtinEnabled ? 'On' : 'Off — administrators keep a password as a way back in' }}</dd>
        <dt>Portal SSO</dt><dd>{{ s.portalEnabled ? 'On' : 'Off' }} · <a routerLink="/admin/settings/sign-in">Sign-in settings</a></dd>
        <dt>SSO-only accounts</dt><dd>{{ s.ssoOnlyUsers }}</dd>
        <dt>Password policy</dt><dd>{{ s.passwordPolicy }}</dd>
        <dt>Lockout</dt><dd>{{ s.maxFailedLogins }} failed attempts lock an account for {{ s.lockoutMinutes }} minutes</dd>
        <dt>Sessions</dt><dd>Expire after {{ s.sessionTimeout }} of inactivity</dd></dl>
        <div class="tiny muted mt-8">Lockout and session length come from newxr.properties (newxr.security.*, server.servlet.session.timeout).</div></div>
      <div class="card flush"><div style="padding:14px 16px 6px"><h3>Locked accounts</h3></div>
        @for (u of s.locked; track u.id) { <div class="urow"><nx-avatar [user]="u" [size]="24" /><span class="grow small">{{ u.name }} <span class="subtle">{{ u.email }}</span></span>
          <span class="tiny muted">until {{ dateTime(u.lockedUntil) }}</span><button class="btn sm" (click)="unlock(u.id)">Unlock</button></div> }
        @empty { <div class="small subtle" style="padding:6px 16px 14px">No locked accounts.</div> }</div>
      <div class="card flush"><div style="padding:14px 16px 6px"><h3>Administrators ({{ s.admins }})</h3></div>
        @for (u of s.administrators; track u.id) { <div class="urow"><nx-avatar [user]="u" [size]="24" /><span class="grow small">{{ u.name }} <span class="subtle">{{ u.email }}</span></span>
          <span class="tiny muted">{{ u.hasPassword ? 'Has a password' : 'SSO only' }}</span><nx-status [value]="u.status" /></div> }</div>
    }
  </div>`,
  styles: [`.kv{display:grid;grid-template-columns:170px 1fr;gap:8px 12px;margin:0;font-size:13px} .kv dt{color:var(--text-muted)} .kv dd{margin:0}
    .urow{display:flex;align-items:center;gap:10px;padding:9px 16px;border-top:1px solid var(--border)}`],
})
export class AdminSecurity {
  private adminApi = inject(AdminApi);
  private toasts = inject(Toasts);
  readonly s = signal<SecurityView | null>(null);
  dateTime = dateTime;
  constructor() { this.load(); }
  async load() { this.s.set(await this.adminApi.security()); }
  async unlock(id: number) { try { await this.adminApi.userAction(id, 'unlock'); this.toasts.info('Unlocked'); this.load(); } catch (e) { this.toasts.error((e as ApiError).message); } }
}

// ---- audit log ---------------------------------------------------------------------------------

@Component({
  selector: 'nx-admin-audit',
  imports: [FormsModule, Icon, Empty],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="page">
    <div class="crumbs"><span>Administration</span></div>
    <div class="title-bar"><div><h1>Audit log</h1><div class="sub">Sign-ins and administrative changes.</div></div>
      <div class="actions"><button class="btn" (click)="exportCsv()"><nx-icon name="download" /> Export</button></div></div>
    <div class="row wrap">
      <div class="search-input"><nx-icon name="search" /><input class="input" placeholder="Search people, targets, details" [(ngModel)]="q" (ngModelChange)="reset()" style="width:260px" /></div>
      <select class="input" style="width:auto" [(ngModel)]="action" (ngModelChange)="reset()"><option value="">All events</option><option value="signin">Sign-ins</option>
        <option value="user.">Users</option><option value="group.">Groups</option><option value="product.">Products</option><option value="settings.">Settings</option></select>
    </div>
    <div class="card flush"><div class="table-wrap"><table class="table">
      <thead><tr><th>When</th><th>Who</th><th>Event</th><th>Target</th><th>Details</th><th>IP</th></tr></thead>
      <tbody>@for (e of rows(); track e.id) {
        <tr><td class="nowrap muted">{{ dateTime(e.at) }}</td><td>{{ e.actorName ?? e.actorEmail ?? '—' }}</td>
          <td><span class="lozenge" [class.danger]="e.action.startsWith('signin.f') || e.action === 'signin.locked'">{{ e.action }}</span></td>
          <td>{{ e.target }}</td><td class="small muted">{{ e.detail }}</td><td class="mono tiny subtle">{{ e.ip }}</td></tr>
      } @empty { <tr><td colspan="6"><nx-empty icon="list" title="No events match" /></td></tr> }</tbody></table></div>
      <div class="table-foot"><span>{{ rows().length }} events</span>@if (more()) { <button class="btn sm" (click)="next()">Load more</button> }</div></div>
  </div>`,
})
export class AdminAudit {
  private adminApi = inject(AdminApi);
  readonly rows = signal<AuditView[]>([]);
  readonly more = signal(false);
  dateTime = dateTime;
  q = ''; action = ''; page = 0;
  constructor() { this.reset(); }
  async reset() { this.page = 0; const r = await this.adminApi.audit({ q: this.q, action: this.action, page: 0 }); this.rows.set(r); this.more.set(r.length === 100); }
  async next() { this.page++; const r = await this.adminApi.audit({ q: this.q, action: this.action, page: this.page }); this.rows.update(x => [...x, ...r]); this.more.set(r.length === 100); }
  exportCsv() {
    const esc = (v: unknown) => `"${String(v ?? '').replace(/"/g, '""')}"`;
    const lines = [['When', 'Who', 'Email', 'Event', 'Target', 'Details', 'IP'].join(',')]
      .concat(this.rows().map(e => [e.at, e.actorName, e.actorEmail, e.action, e.target, e.detail, e.ip].map(esc).join(',')));
    const a = document.createElement('a');
    a.href = URL.createObjectURL(new Blob([lines.join('\n')], { type: 'text/csv' }));
    a.download = 'newxr-audit.csv';
    a.click();
    URL.revokeObjectURL(a.href);
  }
}

