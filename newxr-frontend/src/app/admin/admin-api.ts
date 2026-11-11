import { Injectable, inject } from '@angular/core';
import { Api } from '../core/api';
import { UserRef } from '../core/models';

export interface UserRow {
  id: number; name: string; email: string; initials: string; color: string; jobTitle?: string; role: string; status: string; apps: string[]; allApps: boolean;
  source: string; hasPassword: boolean; lastActiveAt?: string; lockedUntil?: string; createdAt: string; groups: string[];
}
export interface ProductView { code: string; name: string; description: string; color: string; enabled: boolean; users: number; }
export interface AuditView { id: number; actorName?: string; actorEmail?: string; action: string; target?: string; detail?: string; ip?: string; at: string; }
export interface Overview { active: number; invited: number; guests: number; admins: number; deactivated: number; locked: number; newThisMonth: number; products: ProductView[]; signIn: string; database: string; kafka: boolean; recent: AuditView[]; }
export interface GroupView { id: number; name: string; description?: string; members: UserRef[]; }
export interface SecurityView { passwordPolicy: string; maxFailedLogins: number; lockoutMinutes: number; builtinEnabled: boolean; portalEnabled: boolean; ssoOnlyUsers: number; admins: number; locked: UserRow[]; administrators: UserRow[]; sessionTimeout: string; }
export interface LinkResult { user: UserRow; link: string; expiresAt: string; }
export interface OrgSettings { name: string; logoUrl?: string; supportUrl?: string; }
export interface SignInSettings { builtinEnabled: boolean; portalEnabled: boolean; portalIssuerUrl?: string; portalClientId?: string; portalClientSecretSet: boolean; portalButtonLabel: string; autoProvision: boolean; }
export interface KafkaSettings { enabled: boolean; bootstrapServers?: string; topicPrefix: string; securityProtocol: string; saslMechanism: string; username?: string; passwordSet: boolean; }
export interface DbStatus { type: string; product: string; version: string; url: string; embedded: boolean; }
export interface TestResult { ok: boolean; message: string; [k: string]: unknown; }

@Injectable({ providedIn: 'root' })
export class AdminApi {
  private api = inject(Api);
  overview() { return this.api.get<Overview>('/api/admin/overview'); }
  users(f: Record<string, string | undefined> = {}) { return this.api.get<UserRow[]>('/api/admin/users', f); }
  invite(b: unknown) { return this.api.post<LinkResult>('/api/admin/users/invite', b); }
  editUser(id: number, b: unknown) { return this.api.put<UserRow>(`/api/admin/users/${id}`, b); }
  userAction(id: number, action: 'deactivate' | 'reactivate' | 'unlock') { return this.api.post<UserRow>(`/api/admin/users/${id}/${action}`); }
  resetLink(id: number) { return this.api.post<LinkResult>(`/api/admin/users/${id}/reset-link`); }
  deleteUser(id: number) { return this.api.delete(`/api/admin/users/${id}`); }
  groups() { return this.api.get<GroupView[]>('/api/admin/groups'); }
  createGroup(b: unknown) { return this.api.post<GroupView>('/api/admin/groups', b); }
  editGroup(id: number, b: unknown) { return this.api.put<GroupView>(`/api/admin/groups/${id}`, b); }
  deleteGroup(id: number) { return this.api.delete(`/api/admin/groups/${id}`); }
  products() { return this.api.get<ProductView[]>('/api/admin/products'); }
  toggleProduct(code: string, enabled: boolean) { return this.api.put<ProductView[]>(`/api/admin/products/${code}`, { enabled }); }
  security() { return this.api.get<SecurityView>('/api/admin/security'); }
  audit(f: Record<string, string | number | undefined>) { return this.api.get<AuditView[]>('/api/admin/audit', f); }

  org() { return this.api.get<OrgSettings>('/api/admin/settings/org'); }
  saveOrg(b: OrgSettings) { return this.api.put<OrgSettings>('/api/admin/settings/org', b); }
  signIn() { return this.api.get<{ settings: SignInSettings; redirectUri: string }>('/api/admin/settings/sign-in'); }
  saveSignIn(b: unknown) { return this.api.put<{ settings: SignInSettings; redirectUri: string }>('/api/admin/settings/sign-in', b); }
  testPortal(issuerUrl: string) { return this.api.post<TestResult>('/api/admin/settings/sign-in/test', { issuerUrl }); }
  database() { return this.api.get<DbStatus>('/api/admin/settings/database'); }
  testDatabase(b: unknown) { return this.api.post<TestResult>('/api/admin/settings/database/test', b); }
  kafka() { return this.api.get<KafkaSettings>('/api/admin/settings/kafka'); }
  saveKafka(b: unknown) { return this.api.put<KafkaSettings>('/api/admin/settings/kafka', b); }
  testKafka(b: unknown) { return this.api.post<TestResult>('/api/admin/settings/kafka/test', b); }

  invitation(token: string) { return this.api.get<{ email: string; name: string; purpose: string; organization: string; passwordPolicy: string; portalEnabled: boolean }>(`/api/public/invite/${token}`); }
  accept(token: string, password: string) { return this.api.post<{ email: string }>(`/api/public/invite/${token}`, { password }); }
}
