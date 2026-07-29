import type { Routes } from '@angular/router';

const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./cart-page/cart-page').then((m) => m.CartPage),
  },
];

export default routes;
