import { Routes } from '@angular/router';
import { authGuard } from './core/auth';
import { Login } from './auth/login';
import { Home } from './home/home';
import { Apps, People, Recent, Search, Starred } from './home/lists';
import { Profile } from './home/profile';
import { YourWork } from './home/your-work';
import { Setup } from './setup/setup';
import { Shell } from './shell/shell';

export const routes: Routes = [
  { path: 'setup', component: Setup, title: 'Set up Newxr' },
  { path: 'login', component: Login, title: 'Log in · Newxr' },
  {
    path: 'pages/:key/:id/edit',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/page-editor').then(m => m.PageEditor),
    title: 'Edit · Pages',
  },
  {
    path: 'invite/:token',
    loadComponent: () => import('./admin/admin-settings').then(m => m.InvitePage),
    title: 'Join · Newxr',
  },
  {
    path: 'status/:slug',
    loadComponent: () => import('./pulse/status-pages').then(m => m.PublicStatus),
    title: 'Status',
  },
  {
    path: 'help',
    canActivate: [authGuard],
    loadChildren: () => import('./desk/desk.routes').then(m => m.HELP_ROUTES),
  },
  {
    path: '',
    component: Shell,
    canActivate: [authGuard],
    children: [
      { path: 'pages', loadChildren: () => import('./pages/pages.routes').then(m => m.PAGES_ROUTES) },
      { path: 'boards', loadChildren: () => import('./boards/boards.routes').then(m => m.BOARDS_ROUTES) },
      { path: 'goals', loadChildren: () => import('./goals/goals.routes').then(m => m.GOALS_ROUTES) },
      { path: 'admin', loadChildren: () => import('./admin/admin.routes').then(m => m.ADMIN_ROUTES) },
      { path: 'code', loadChildren: () => import('./code/code.routes').then(m => m.CODE_ROUTES) },
      { path: 'pulse', loadChildren: () => import('./pulse/pulse.routes').then(m => m.PULSE_ROUTES) },
      { path: 'desk', loadChildren: () => import('./desk/desk.routes').then(m => m.DESK_ROUTES) },
      { path: 'track', loadChildren: () => import('./track/track.routes').then(m => m.TRACK_ROUTES) },
      {
        path: '',
        component: YourWork,
        children: [
          { path: '', component: Home, title: 'For you · Newxr' },
          { path: 'recent', component: Recent, title: 'Recent · Newxr' },
          { path: 'starred', component: Starred, title: 'Starred · Newxr' },
          { path: 'apps', component: Apps, title: 'Apps · Newxr' },
          { path: 'people', component: People, title: 'People · Newxr' },
          { path: 'people/:id', component: People, title: 'People · Newxr' },
          { path: 'search', component: Search, title: 'Search · Newxr' },
          { path: 'profile', component: Profile, title: 'Profile · Newxr' },
        ],
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
