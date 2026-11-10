import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Api, ApiError } from '../core/api';
import { Auth } from '../core/auth';
import { Icon, Logo } from '../shared/ui';

interface DbStatus { type: string; product: string; version: string; url: string; embedded: boolean; confirmed: boolean; configFile: string; }
interface SetupStatus {
  setupRequired: boolean; setupKeyIssued: boolean; setupKeyFile: string; licenseId: string; licenseName: string;
  passwordPolicy: string; database: DbStatus | null; suggestedPublicUrl: string; portalRedirectUri: string;
}
interface TestResult { ok: boolean; message: string; }

const STEPS = ['Setup key', 'License', 'Database', 'Administrator & sign-in', 'Kafka', 'Organization', 'Review'];
const STORE = 'newxr.setup';

@Component({
  selector: 'nx-setup',
  imports: [FormsModule, Icon, Logo],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './setup.scss',
  templateUrl: './setup.html',
})
export class Setup {
  private api = inject(Api);
  private auth = inject(Auth);
  private router = inject(Router);

  readonly steps = STEPS;
  readonly step = signal(0);
  readonly status = signal<SetupStatus | null>(null);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  readonly licenseText = signal('');
  readonly restarting = signal(false);

  // step 0
  setupKey = '';
  // step 1
  licenseAccepted = false;
  // step 2
  dbType: 'h2' | 'postgresql' | 'mysql' = 'h2';
  db = { host: 'localhost', port: null as number | null, database: 'newxr', username: 'newxr', password: '', jdbcUrl: '' };
  useJdbcUrl = false;
  readonly dbTest = signal<TestResult | null>(null);
  // step 3
  admin = { name: '', email: '', password: '', confirm: '' };
  builtinEnabled = true;
  portalEnabled = false;
  portal = { issuerUrl: '', clientId: 'newxr', clientSecret: '', buttonLabel: 'Continue with SSO', autoProvision: true };
  readonly portalTest = signal<TestResult | null>(null);
  // step 4
  kafkaEnabled = false;
  kafka = { bootstrapServers: '', topicPrefix: 'newxr', securityProtocol: 'PLAINTEXT', saslMechanism: 'PLAIN', username: '', password: '' };
  readonly kafkaTest = signal<TestResult | null>(null);
  // step 5
  org = { name: '', logoUrl: '', supportUrl: '' };
  sampleData = true;

  readonly dbLabel = computed(() => {
    const d = this.status()?.database;
    return d ? `${d.product ?? d.type} ${d.version ?? ''}`.trim() : '';
  });

  constructor() {
    this.restore();
    this.load();
  }

  async load() {
    try {
      const s = await this.api.get<SetupStatus>('/api/public/setup/status');
      if (!s.setupRequired) { this.router.navigateByUrl('/login'); return; }
      this.status.set(s);
      if (s.database?.confirmed && this.step() <= 2 && this.setupKey && this.licenseAccepted) this.step.set(3);
    } catch (e) {
      this.error.set((e as ApiError).message);
    }
  }

  private headers() { return { 'X-Setup-Key': this.setupKey.trim() }; }

  go(i: number) { this.error.set(null); this.step.set(i); this.persist(); }

  async next() {
    this.error.set(null);
    this.busy.set(true);
    try {
      switch (this.step()) {
        case 0:
          await this.api.post('/api/public/setup/verify-key', {}, this.headers());
          if (!this.licenseText()) this.licenseText.set(await this.api.text('/api/public/setup/license'));
          break;
        case 1:
          if (!this.licenseAccepted) throw { message: 'Accept the license to continue.' };
          break;
        case 2:
          await this.saveDatabase();
          if (this.restarting()) return;
          break;
        case 3:
          this.checkAdmin();
          if (this.portalEnabled && (!this.portal.issuerUrl || !this.portal.clientId)) throw { message: 'Portal SSO needs its address and a client ID.' };
          if (!this.builtinEnabled && !this.portalEnabled) throw { message: 'Choose at least one way to sign in.' };
          break;
        case 4:
          if (this.kafkaEnabled && !this.kafka.bootstrapServers.trim()) throw { message: 'Enter the Kafka bootstrap servers, or turn Kafka off.' };
          break;
        case 5:
          if (!this.org.name.trim()) throw { message: 'Enter your organization name.' };
          break;
        case 6:
          await this.finish();
          return;
      }
      this.go(this.step() + 1);
    } catch (e) {
      this.error.set((e as ApiError).message);
    } finally {
      this.busy.set(false);
    }
  }

  back() { if (this.step() > 0) this.go(this.step() - 1); }

  private dbBody() {
    return { type: this.dbType, host: this.db.host, port: this.db.port, database: this.db.database,
      username: this.db.username, password: this.db.password, jdbcUrl: this.useJdbcUrl ? this.db.jdbcUrl : null };
  }

  async testDatabase() {
    this.dbTest.set(null);
    this.busy.set(true);
    try { this.dbTest.set(await this.api.post<TestResult>('/api/public/setup/database/test', this.dbBody(), this.headers())); }
    catch (e) { this.dbTest.set({ ok: false, message: (e as ApiError).message }); }
    finally { this.busy.set(false); }
  }

  private async saveDatabase() {
    if (this.dbType !== 'h2' && !this.dbTest()?.ok) throw { message: 'Test the connection first.' };
    const r = await this.api.post<{ restarting: boolean }>('/api/public/setup/database', this.dbBody(), this.headers());
    if (!r.restarting) return;
    this.restarting.set(true);
    this.step.set(3);
    this.persist();
    await this.waitForRestart();
  }

  /** The server restarts onto the new database; poll until it answers from it. */
  private async waitForRestart() {
    await new Promise(r => setTimeout(r, 2500));
    for (let i = 0; i < 60; i++) {
      try {
        const s = await this.api.get<SetupStatus>('/api/public/setup/status');
        if (s.database && s.database.type === this.dbType) {
          this.status.set(s);
          this.restarting.set(false);
          this.busy.set(false);
          return;
        }
      } catch { /* still restarting */ }
      await new Promise(r => setTimeout(r, 2000));
    }
    this.restarting.set(false);
    this.busy.set(false);
    this.step.set(2);
    this.error.set('The server did not come back on the new database. Check its log; newxr.properties was updated with the new connection.');
  }

  async testPortal() {
    this.portalTest.set(null);
    try { this.portalTest.set(await this.api.post<TestResult>('/api/public/setup/sso/test', { issuerUrl: this.portal.issuerUrl }, this.headers())); }
    catch (e) { this.portalTest.set({ ok: false, message: (e as ApiError).message }); }
  }

  async testKafka() {
    this.kafkaTest.set(null);
    this.busy.set(true);
    try { this.kafkaTest.set(await this.api.post<TestResult>('/api/public/setup/kafka/test', this.kafka, this.headers())); }
    catch (e) { this.kafkaTest.set({ ok: false, message: (e as ApiError).message }); }
    finally { this.busy.set(false); }
  }

  copy(text: string) { navigator.clipboard?.writeText(text); }

  private checkAdmin() {
    if (!this.admin.name.trim()) throw { message: "Enter the administrator's name." };
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(this.admin.email)) throw { message: 'Enter a valid administrator email.' };
    const p = this.admin.password;
    if (p.length < 12 || !/[A-Z]/.test(p) || !/[a-z]/.test(p) || !/\d/.test(p)) throw { message: 'Password: ' + (this.status()?.passwordPolicy ?? '') };
    if (p !== this.admin.confirm) throw { message: 'The passwords do not match.' };
  }

  private async finish() {
    await this.api.post('/api/public/setup/initialize', {
      setupKey: this.setupKey.trim(),
      licenseAccepted: this.licenseAccepted,
      licenseId: this.status()?.licenseId,
      admin: { name: this.admin.name.trim(), email: this.admin.email.trim(), password: this.admin.password },
      organization: { name: this.org.name.trim(), logoUrl: this.org.logoUrl.trim() || null, supportUrl: this.org.supportUrl.trim() || null },
      signIn: { builtinEnabled: this.builtinEnabled, portalEnabled: this.portalEnabled, issuerUrl: this.portal.issuerUrl.trim(),
        clientId: this.portal.clientId.trim(), clientSecret: this.portal.clientSecret, buttonLabel: this.portal.buttonLabel, autoProvision: this.portal.autoProvision },
      kafka: { enabled: this.kafkaEnabled, ...this.kafka },
      sampleData: this.sampleData,
    });
    try { sessionStorage.removeItem(STORE); } catch { /* blocked */ }
    await this.auth.login(this.admin.email.trim(), this.admin.password);
    this.router.navigateByUrl('/');
  }

  /** Survives the restart after the database step (passwords are never stored). */
  private persist() {
    try {
      sessionStorage.setItem(STORE, JSON.stringify({ step: this.step(), setupKey: this.setupKey, licenseAccepted: this.licenseAccepted,
        admin: { name: this.admin.name, email: this.admin.email }, dbType: this.dbType }));
    } catch { /* storage blocked: the wizard still works, it just restarts from the key */ }
  }

  private restore() {
    try {
      const s = JSON.parse(sessionStorage.getItem(STORE) ?? 'null');
      if (!s) return;
      this.setupKey = s.setupKey ?? '';
      this.licenseAccepted = !!s.licenseAccepted;
      this.admin.name = s.admin?.name ?? '';
      this.admin.email = s.admin?.email ?? '';
      this.dbType = s.dbType ?? 'h2';
      this.step.set(Math.min(s.step ?? 0, 3));
      if (this.setupKey) this.api.text('/api/public/setup/license').then(t => this.licenseText.set(t)).catch(() => null);
    } catch { /* ignore */ }
  }
}
