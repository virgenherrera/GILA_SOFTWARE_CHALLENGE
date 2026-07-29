import type { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'products' },
  {
    path: 'products',
    title: 'Products',
    loadChildren: () => import('./products/product.routes'),
  },
  {
    path: 'imports',
    title: 'Imports',
    loadChildren: () => import('./imports/import.routes'),
  },
  {
    path: 'search',
    title: 'Search',
    loadChildren: () => import('./search/search.routes'),
  },
  {
    path: 'cart',
    title: 'Cart',
    loadChildren: () => import('./cart/cart.routes'),
  },
  {
    path: 'checkout',
    title: 'Checkout',
    loadChildren: () => import('./checkout/checkout.routes'),
  },
];
