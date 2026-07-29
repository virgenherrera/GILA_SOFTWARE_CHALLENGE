import type { Routes } from '@angular/router';

const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./search-page/search-page').then((m) => m.SearchPage),
  },
];

export default routes;
