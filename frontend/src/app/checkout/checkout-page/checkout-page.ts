import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

/**
 * Checkout is triggered directly from the cart page (`CartPage.onCheckout()` calls
 * `POST /api/checkout` and navigates straight to the order confirmation on success), so this
 * route only needs to exist as a friendly landing spot for anyone who lands on `/checkout`
 * directly (e.g. a bookmarked or typed URL) without an order to confirm.
 */
@Component({
  selector: 'app-checkout-page',
  imports: [RouterLink],
  templateUrl: './checkout-page.html',
  styleUrl: './checkout-page.css',
})
export class CheckoutPage {}
