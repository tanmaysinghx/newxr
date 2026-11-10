import { Routes } from '@angular/router';
import { Backlog } from './backlog';
import { Board } from './board';
import { IssuePage } from './issue-page';
import { IssueList } from './list';
import { TrackProjects } from './projects';
import { Reports } from './reports';
import { TrackSettings } from './settings';
import { TrackSummary } from './summary';
import { Timeline } from './timeline';
import { TrackLayout } from './track-layout';

export const TRACK_ROUTES: Routes = [
  { path: '', component: TrackProjects, title: 'Projects · Track' },
  { path: 'issue/:key', component: IssuePage, title: 'Issue · Track' },
  {
    path: ':key',
    component: TrackLayout,
    children: [
      { path: '', redirectTo: 'board', pathMatch: 'full' },
      { path: 'summary', component: TrackSummary, title: 'Summary · Track' },
      { path: 'board', component: Board, title: 'Board · Track' },
      { path: 'backlog', component: Backlog, title: 'Backlog · Track' },
      { path: 'list', component: IssueList, title: 'List · Track' },
      { path: 'timeline', component: Timeline, title: 'Timeline · Track' },
      { path: 'reports', component: Reports, title: 'Reports · Track' },
      { path: 'settings', component: TrackSettings, title: 'Settings · Track' },
    ],
  },
];
