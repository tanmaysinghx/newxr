import { Routes } from '@angular/router';
import { BoardPage } from './board-page';
import { BoardsHome } from './boards-home';

export const BOARDS_ROUTES: Routes = [
  { path: '', component: BoardsHome, title: 'Boards' },
  { path: ':id', component: BoardPage, title: 'Board · Boards' },
];
