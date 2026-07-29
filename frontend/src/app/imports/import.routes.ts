import type { Routes } from '@angular/router';

const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./import-upload/import-upload').then((m) => m.ImportUpload),
  },
  {
    path: ':id',
    loadComponent: () => import('./import-results/import-results').then((m) => m.ImportResults),
  },
];

export default routes;
