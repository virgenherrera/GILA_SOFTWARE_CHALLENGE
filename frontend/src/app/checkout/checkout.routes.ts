import type { Routes } from '@angular/router';

const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./checkout-page/checkout-page').then((m) => m.CheckoutPage),
  },
  {
    path: 'confirmation/:id',
    loadComponent: () =>
      import('./order-confirmation/order-confirmation').then((m) => m.OrderConfirmation),
  },
];

export default routes;
