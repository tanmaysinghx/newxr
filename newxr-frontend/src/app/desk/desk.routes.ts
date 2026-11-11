import { Routes } from '@angular/router';
import { HelpDesk, HelpFrame, HelpHomePage, HelpRequest, HelpRequests } from './help-center';
import { DeskCustomers, DeskLayout, DeskProjects, DeskQueue, DeskRequest, DeskSlas } from './desk-pages';

export const DESK_ROUTES: Routes = [
  { path: '', component: DeskProjects, title: 'Service projects · Desk' },
  {
    path: ':key',
    component: DeskLayout,
    children: [
      { path: '', redirectTo: 'queues', pathMatch: 'full' },
      { path: 'queues', component: DeskQueue, title: 'Queues · Desk' },
      { path: 'queues/:queue', component: DeskQueue, title: 'Queues · Desk' },
      { path: 'customers', component: DeskCustomers, title: 'Customers · Desk' },
      { path: 'slas', component: DeskSlas, title: 'SLAs · Desk' },
      { path: ':req', component: DeskRequest, title: 'Request · Desk' },
    ],
  },
];

/** Customer help center, outside the agent shell. */
export const HELP_ROUTES: Routes = [
  {
    path: '',
    component: HelpFrame,
    children: [
      { path: '', component: HelpHomePage, title: 'Help center' },
      { path: 'requests', component: HelpRequests, title: 'My requests · Help center' },
      { path: 'requests/:key', component: HelpRequest, title: 'Request · Help center' },
      { path: ':key', component: HelpDesk, title: 'Raise a request · Help center' },
    ],
  },
];
