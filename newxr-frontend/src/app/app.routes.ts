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
    path: '',
    component: Shell,
    canActivate: [authGuard],
    children: [
      { path: 'pages', loadChildren: () => import('./pages/pages.routes').then(m => m.PAGES_ROUTES) },
      { path: 'boards', loadChildren: () => import('./boards/boards.routes').then(m => m.BOARDS_ROUTES) },
      { path: 'goals', loadChildren: () => import('./goals/goals.routes').then(m => m.GOALS_ROUTES) },
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
