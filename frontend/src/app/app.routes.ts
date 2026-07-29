import type { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'products' },
  { path: 'products', loadChildren: () => import('./products/product.routes') },
  { path: 'imports', loadChildren: () => import('./imports/import.routes') },
  { path: 'search', loadChildren: () => import('./search/search.routes') },
  { path: 'cart', loadChildren: () => import('./cart/cart.routes') },
  { path: 'checkout', loadChildren: () => import('./checkout/checkout.routes') },
];
