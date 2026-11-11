import { inject } from '@angular/core';
import { CanActivateFn, Router, Routes } from '@angular/router';
import { Auth } from '../core/auth';
import { AdminAudit, AdminGroups, AdminLayout, AdminOverview, AdminProducts, AdminSecurity, AdminUsers } from './admin-pages';
import { AdminDatabase, AdminKafka, AdminOrg, AdminSignIn } from './admin-settings';

/** The API enforces the ADMIN role; this just keeps members away from screens they can't use. */
const adminGuard: CanActivateFn = async () => {
  const auth = inject(Auth);
  const me = await auth.ensure();
  return me?.role === 'ADMIN' ? true : inject(Router).parseUrl('/');
};

export const ADMIN_ROUTES: Routes = [
  {
    path: '',
    component: AdminLayout,
    canActivate: [adminGuard],
    children: [
      { path: '', component: AdminOverview, title: 'Administration' },
      { path: 'users', component: AdminUsers, title: 'Users · Administration' },
      { path: 'groups', component: AdminGroups, title: 'Groups · Administration' },
      { path: 'products', component: AdminProducts, title: 'Products · Administration' },
      { path: 'security', component: AdminSecurity, title: 'Security · Administration' },
      { path: 'audit', component: AdminAudit, title: 'Audit log · Administration' },
      { path: 'settings/organization', component: AdminOrg, title: 'Organization · Administration' },
      { path: 'settings/sign-in', component: AdminSignIn, title: 'Sign-in · Administration' },
      { path: 'settings/database', component: AdminDatabase, title: 'Database · Administration' },
      { path: 'settings/kafka', component: AdminKafka, title: 'Kafka · Administration' },
    ],
  },
];
