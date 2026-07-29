import { Component, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { CartService } from './cart/cart.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  protected readonly title = signal('E-Commerce');
  protected readonly menuOpen = signal(false);

  private readonly cartService = inject(CartService);
  protected readonly cartItemCount = this.cartService.cartItemCount;

  protected toggleMenu(): void {
    this.menuOpen.update((open) => !open);
  }
}
