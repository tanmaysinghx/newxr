import { Routes } from '@angular/router';
import { PageViewPage } from './page-view';
import { SpaceBlog, SpaceSettings, SpaceTemplates } from './space-extras';
import { SpaceLayout } from './space-layout';
import { SpaceOverview } from './space-overview';
import { Spaces } from './spaces';

export const PAGES_ROUTES: Routes = [
  { path: '', component: Spaces, title: 'Spaces · Pages' },
  {
    path: ':key',
    component: SpaceLayout,
    children: [
      { path: '', component: SpaceOverview, title: 'Space · Pages' },
      { path: 'blog', component: SpaceBlog, title: 'Blog · Pages' },
      { path: 'templates', component: SpaceTemplates, title: 'Templates · Pages' },
      { path: 'settings', component: SpaceSettings, title: 'Space settings · Pages' },
      { path: ':id', component: PageViewPage, title: 'Page · Pages' },
    ],
  },
];
