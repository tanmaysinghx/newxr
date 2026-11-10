import { Routes } from '@angular/router';
import { GoalPage, GoalUpdates, GoalsLayout, GoalsList } from './goals-pages';

export const GOALS_ROUTES: Routes = [
  {
    path: '',
    component: GoalsLayout,
    children: [
      { path: '', component: GoalsList, title: 'Goals' },
      { path: 'updates', component: GoalUpdates, title: 'Updates · Goals' },
      { path: ':id', component: GoalPage, title: 'Goal · Goals' },
    ],
  },
];
