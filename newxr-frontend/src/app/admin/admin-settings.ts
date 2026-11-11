import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiError } from '../core/api';
import { Auth } from '../core/auth';
import { Toasts } from '../core/toasts';
import { Icon, Logo } from '../shared/ui';
import { AdminApi, DbStatus, KafkaSettings, TestResult } from './admin-api';

/** Result banner for "Test connection" buttons. */
@Component({
  selector: 'nx-test-result',
  imports: [Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `@if (r(); as r) { <div class="banner" [class.success]="r.ok" [class.danger]="!r.ok"><nx-icon [name]="r.ok ? 'check-circle' : 'alert'" /><div>{{ r.message }}</div></div> }`,
})
export class TestResultBanner {
  readonly r = input<TestResult | null>(null);
}

@Component({
  selector: 'nx-admin-org',
  imports: [FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="page narrow">
    <div class="crumbs"><span>Administration</span><span class="sep">/</span><span>Settings</span></div>
    <div class="title-bar"><div><h1>Organization</h1><div class="sub">Shown on the sign-in page, in the help center and on repository names.</div></div></div>
    <div class="card col" style="gap:14px">
      <div class="field"><label>Organization name</label><input class="input" [(ngModel)]="f.name" /></div>
      <div class="field"><label>Logo URL</label><input class="input" [(ngModel)]="f.logoUrl" placeholder="https://example.com/logo.svg" /></div>
      <div class="field"><label>Support link</label><input class="input" [(ngModel)]="f.supportUrl" placeholder="https://help.example.com or mailto:it@example.com" />
        <div class="tiny muted">Opened from the help (?) button.</div></div>
      <div class="row"><span class="grow"></span><button class="btn primary" (click)="save()">Save</button></div>
    </div>
  </div>`,
})
export class AdminOrg {
  private adminApi = inject(AdminApi);
  private toasts = inject(Toasts);
  private auth = inject(Auth);
  f = { name: '', logoUrl: '', supportUrl: '' };
  constructor() { this.adminApi.org().then(o => this.f = { name: o.name, logoUrl: o.logoUrl ?? '', supportUrl: o.supportUrl ?? '' }); }
  async save() {
    try { await this.adminApi.saveOrg(this.f); await this.auth.loadOptions(); this.toasts.info('Organization saved'); }
    catch (e) { this.toasts.error((e as ApiError).message); }
  }
}

@Component({
  selector: 'nx-admin-sign-in',
  imports: [FormsModule, TestResultBanner],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="page narrow">
    <div class="crumbs"><span>Administration</span><span class="sep">/</span><span>Settings</span></div>
    <div class="title-bar"><div><h1>Sign-in</h1><div class="sub">Built-in passwords, Portal SSO, or both.</div></div></div>
    <div class="card col" style="gap:12px">
      <label class="row"><input type="checkbox" [(ngModel)]="f.builtinEnabled" /> <b>Built-in passwords</b></label>
      <div class="small muted" style="margin-left:24px">Administrators can always sign in with their password, so you can't lock yourselves out if SSO is down.</div>
      <label class="row"><input type="checkbox" [(ngModel)]="f.portalEnabled" /> <b>Portal SSO</b> (OpenID Connect)</label>
      @if (f.portalEnabled) {
        <div class="col" style="gap:12px;margin-left:24px">
          <div class="field"><label>Issuer URL</label><div class="row"><input class="input" [(ngModel)]="f.issuerUrl" placeholder="https://sso.example.com" />
            <button class="btn" (click)="test()">Test</button></div></div>
          <nx-test-result [r]="result()" />
          <div class="form-grid"><div class="field"><label>Client ID</label><input class="input" [(ngModel)]="f.clientId" /></div>
            <div class="field"><label>Client secret</label><input class="input" type="password" [(ngModel)]="secret" [placeholder]="secretSet ? 'Saved — leave empty to keep' : 'Paste the client secret'" autocomplete="new-password" /></div></div>
          <div class="field"><label>Redirect URI to register in Portal SSO</label><input class="input mono" [value]="redirectUri" readonly /></div>
          <div class="field"><label>Button label</label><input class="input" [(ngModel)]="f.buttonLabel" /></div>
          <label class="row small"><input type="checkbox" [(ngModel)]="f.autoProvision" /> Create a Newxr account the first time someone signs in with SSO</label>
        </div>
      }
      <div class="row"><span class="grow"></span><button class="btn primary" (click)="save()">Save</button></div>
    </div>
  </div>`,
})
export class AdminSignIn {
  private adminApi = inject(AdminApi);
  private toasts = inject(Toasts);
  readonly result = signal<TestResult | null>(null);
  f = { builtinEnabled: true, portalEnabled: false, issuerUrl: '', clientId: '', buttonLabel: 'Continue with SSO', autoProvision: true };
  secret = ''; secretSet = false; redirectUri = '';
  constructor() {
    this.adminApi.signIn().then(r => {
      const s = r.settings;
      this.f = { builtinEnabled: s.builtinEnabled, portalEnabled: s.portalEnabled, issuerUrl: s.portalIssuerUrl ?? '', clientId: s.portalClientId ?? '',
        buttonLabel: s.portalButtonLabel, autoProvision: s.autoProvision };
      this.secretSet = s.portalClientSecretSet; this.redirectUri = r.redirectUri;
    });
  }
  async test() { try { this.result.set(await this.adminApi.testPortal(this.f.issuerUrl)); } catch (e) { this.result.set({ ok: false, message: (e as ApiError).message }); } }
  async save() {
    try {
      const r = await this.adminApi.saveSignIn({ ...this.f, clientSecret: this.secret ? this.secret : null });
      this.secretSet = r.settings.portalClientSecretSet; this.secret = '';
      this.toasts.info('Sign-in settings saved');
    } catch (e) { this.toasts.error((e as ApiError).message); }
  }
}

@Component({
  selector: 'nx-admin-database',
  imports: [FormsModule, Icon, TestResultBanner],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="page narrow">
    <div class="crumbs"><span>Administration</span><span class="sep">/</span><span>Settings</span></div>
    <div class="title-bar"><div><h1>Database</h1><div class="sub">Where Newxr keeps its data.</div></div></div>
    @if (s(); as s) {
      <div class="card"><dl class="kv"><dt>Database</dt><dd>{{ s.product }} {{ s.version }}</dd><dt>Type</dt><dd>{{ s.embedded ? 'Embedded H2 (single file in the Newxr home)' : s.type }}</dd>
        <dt>Connection</dt><dd class="mono small" style="word-break:break-all">{{ s.url }}</dd></dl></div>
      @if (s.embedded) { <div class="banner warning"><nx-icon name="info" /><div>The embedded database suits evaluation and small teams. For production, use PostgreSQL or MySQL.</div></div> }
    }
    <div class="card col" style="gap:12px"><h3>Test another database</h3>
      <div class="small muted">Check that Newxr can reach a PostgreSQL or MySQL server. Moving existing data to it needs the data copy tool, which isn't available yet, so switching
        here would start from an empty database. To switch, set the JDBC URL in newxr.properties and restart.</div>
      <div class="form-grid">
        <div class="field"><label>Type</label><select class="input" [(ngModel)]="t.type"><option value="postgresql">PostgreSQL</option><option value="mysql">MySQL</option></select></div>
        <div class="field"><label>Host</label><input class="input" [(ngModel)]="t.host" placeholder="db.internal" /></div>
        <div class="field"><label>Port</label><input class="input" type="number" [(ngModel)]="t.port" /></div>
        <div class="field"><label>Database</label><input class="input" [(ngModel)]="t.database" placeholder="newxr" /></div>
        <div class="field"><label>Username</label><input class="input" [(ngModel)]="t.username" /></div>
        <div class="field"><label>Password</label><input class="input" type="password" [(ngModel)]="t.password" autocomplete="new-password" /></div>
      </div>
      <nx-test-result [r]="result()" />
      <div class="row"><span class="grow"></span><button class="btn" (click)="test()">Test connection</button></div>
    </div>
  </div>`,
  styles: ['.kv{display:grid;grid-template-columns:120px 1fr;gap:8px 12px;margin:0;font-size:13px} .kv dt{color:var(--text-muted)} .kv dd{margin:0}'],
})
export class AdminDatabase {
  private adminApi = inject(AdminApi);
  readonly s = signal<DbStatus | null>(null);
  readonly result = signal<TestResult | null>(null);
  t = { type: 'postgresql', host: '', port: 5432, database: 'newxr', username: '', password: '' };
  constructor() { this.adminApi.database().then(s => this.s.set(s)); }
  async test() { try { this.result.set(await this.adminApi.testDatabase(this.t)); } catch (e) { this.result.set({ ok: false, message: (e as ApiError).message }); } }
}

@Component({
  selector: 'nx-admin-kafka',
  imports: [FormsModule, TestResultBanner],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="page narrow">
    <div class="crumbs"><span>Administration</span><span class="sep">/</span><span>Settings</span></div>
    <div class="title-bar"><div><h1>Kafka</h1><div class="sub">Publish every domain event (issue created, alert raised, page published…) to <span class="mono">{{ f.topicPrefix || 'newxr' }}.events</span>.</div></div></div>
    <div class="card col" style="gap:12px">
      <label class="row"><input type="checkbox" [(ngModel)]="f.enabled" /> <b>Publish events to Kafka</b></label>
      <div class="small muted">When off, events still drive in-app notifications; they just stay inside Newxr.</div>
      <div class="form-grid">
        <div class="field full"><label>Bootstrap servers</label><input class="input" [(ngModel)]="f.bootstrapServers" placeholder="kafka-1:9092,kafka-2:9092" /></div>
        <div class="field"><label>Topic prefix</label><input class="input" [(ngModel)]="f.topicPrefix" /></div>
        <div class="field"><label>Security protocol</label><select class="input" [(ngModel)]="f.securityProtocol">@for (p of ['PLAINTEXT', 'SSL', 'SASL_PLAINTEXT', 'SASL_SSL']; track p) { <option [value]="p">{{ p }}</option> }</select></div>
        @if (f.securityProtocol.startsWith('SASL')) {
          <div class="field"><label>SASL mechanism</label><select class="input" [(ngModel)]="f.saslMechanism">@for (m of ['PLAIN', 'SCRAM-SHA-256', 'SCRAM-SHA-512']; track m) { <option [value]="m">{{ m }}</option> }</select></div>
          <div class="field"><label>Username</label><input class="input" [(ngModel)]="f.username" /></div>
          <div class="field"><label>Password</label><input class="input" type="password" [(ngModel)]="password" [placeholder]="passwordSet ? 'Saved — leave empty to keep' : ''" autocomplete="new-password" /></div>
        }
      </div>
      <nx-test-result [r]="result()" />
      <div class="row"><span class="grow"></span><button class="btn" (click)="test()">Test connection</button><button class="btn primary" (click)="save()">Save</button></div>
    </div>
  </div>`,
})
export class AdminKafka {
  private adminApi = inject(AdminApi);
  private toasts = inject(Toasts);
  readonly result = signal<TestResult | null>(null);
  f: Omit<KafkaSettings, 'passwordSet'> = { enabled: false, bootstrapServers: '', topicPrefix: 'newxr', securityProtocol: 'PLAINTEXT', saslMechanism: 'PLAIN', username: '' };
  password = ''; passwordSet = false;
  constructor() { this.adminApi.kafka().then(k => { const { passwordSet, ...rest } = k; this.f = { ...rest, bootstrapServers: rest.bootstrapServers ?? '', username: rest.username ?? '' }; this.passwordSet = passwordSet; }); }
  body() { return { ...this.f, password: this.password ? this.password : null }; }
  async test() { try { this.result.set(await this.adminApi.testKafka({ ...this.f, password: this.password })); } catch (e) { this.result.set({ ok: false, message: (e as ApiError).message }); } }
  async save() {
    try { const k = await this.adminApi.saveKafka(this.body()); this.passwordSet = k.passwordSet; this.password = ''; this.toasts.info('Kafka settings saved'); }
    catch (e) { this.toasts.error((e as ApiError).message); }
  }
}

// ---- accept an invitation / reset (public) ------------------------------------------------------

@Component({
  selector: 'nx-invite',
  imports: [FormsModule, Icon, Logo],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [`:host{display:flex;min-height:100vh;align-items:center;justify-content:center;background:var(--bg);padding:16px}
    .box{width:100%;max-width:420px;display:flex;flex-direction:column;gap:14px}`],
  template: `<div class="card box">
    <nx-logo [mark]="26" />
    @if (info(); as i) {
      <h1 style="font-size:20px">{{ i.purpose === 'INVITE' ? 'Join ' + i.organization + ' on Newxr' : 'Choose a new password' }}</h1>
      <div class="small muted">{{ i.name }} · {{ i.email }}</div>
      @if (error()) { <div class="banner danger"><nx-icon name="alert" /><div>{{ error() }}</div></div> }
      <form class="col" style="gap:12px" (submit)="$event.preventDefault(); accept()">
        <input type="email" name="username" [value]="i.email" autocomplete="username" hidden />
        <div class="field"><label for="pw">Password</label><input id="pw" class="input lg" type="password" name="password" [(ngModel)]="password" autocomplete="new-password" autofocus /></div>
        <div class="field"><label for="pw2">Confirm password</label><input id="pw2" class="input lg" type="password" name="confirm" [(ngModel)]="confirm" autocomplete="new-password" /></div>
        <div class="tiny muted">{{ i.passwordPolicy }}</div>
        <button class="btn primary lg" type="submit">{{ i.purpose === 'INVITE' ? 'Create my account' : 'Save password' }}</button>
      </form>
      @if (i.portalEnabled && i.purpose === 'INVITE') { <div class="small muted">Your organization also uses SSO — you can skip this and <a href="/login">sign in with SSO</a>.</div> }
    } @else if (gone()) {
      <h1 style="font-size:20px">This link doesn't work any more</h1><div class="small muted">{{ gone() }}</div><a class="btn" href="/login">Go to sign in</a>
    }
  </div>`,
})
export class InvitePage {
  private adminApi = inject(AdminApi);
  private router = inject(Router);
  private toasts = inject(Toasts);
  readonly token = input.required<string>();
  readonly info = signal<{ email: string; name: string; purpose: string; organization: string; passwordPolicy: string; portalEnabled: boolean } | null>(null);
  readonly gone = signal<string | null>(null);
  readonly error = signal<string | null>(null);
  password = ''; confirm = '';
  constructor() { queueMicrotask(() => this.adminApi.invitation(this.token()).then(i => this.info.set(i)).catch(e => this.gone.set((e as ApiError).message))); }
  async accept() {
    if (this.password !== this.confirm) { this.error.set("The passwords don't match."); return; }
    try { const r = await this.adminApi.accept(this.token(), this.password); this.toasts.info('Password saved — sign in to continue'); this.router.navigate(['/login'], { queryParams: { email: r.email } }); }
    catch (e) { this.error.set((e as ApiError).message); }
  }
}
