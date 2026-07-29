import type { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'products' },
  { path: 'products', loadChildren: () => import('./products/product.routes') },
  { path: 'imports', loadChildren: () => import('./imports/import.routes') },
];
