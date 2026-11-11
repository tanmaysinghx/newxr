import { Routes } from '@angular/router';
import { CodeBranches, CodeCommits, CodeDeployments, CodeLayout, CodePipeline, CodePipelines, CodeRepos, CodeSettings, CodeSource } from './code-pages';
import { CodeNewPull, CodePull, CodePulls } from './pull-pages';

export const CODE_ROUTES: Routes = [
  { path: '', component: CodeRepos, title: 'Repositories · Code' },
  {
    path: ':slug',
    component: CodeLayout,
    children: [
      { path: '', redirectTo: 'source', pathMatch: 'full' },
      { path: 'source', component: CodeSource, title: 'Source · Code' },
      { path: 'commits', component: CodeCommits, title: 'Commits · Code' },
      { path: 'branches', component: CodeBranches, title: 'Branches · Code' },
      { path: 'pulls', component: CodePulls, title: 'Pull requests · Code' },
      { path: 'pulls/new', component: CodeNewPull, title: 'New pull request · Code' },
      { path: 'pulls/:n', component: CodePull, title: 'Pull request · Code' },
      { path: 'pipelines', component: CodePipelines, title: 'Pipelines · Code' },
      { path: 'pipelines/:n', component: CodePipeline, title: 'Pipeline · Code' },
      { path: 'deployments', component: CodeDeployments, title: 'Deployments · Code' },
      { path: 'settings', component: CodeSettings, title: 'Settings · Code' },
    ],
  },
];
