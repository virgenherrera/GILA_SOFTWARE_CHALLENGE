> [INDEX](../INDEX.md) / [EP08](../epics/EP08-ux-overhaul.md) / US-021

# US-021 --- Component-Level UX

## Metadata

| Field | Value |
|-------|-------|
| Epic | EP08 --- UI/UX Overhaul |
| Priority | Must Have |
| Estimation | L |
| Status | Ready |

## Story

As a user interacting with product cards, forms, tables, and import flows, I want
each component to be accessible, responsive, and visually polished, so that the
application is usable on any device with any assistive technology.

## Acceptance Criteria

- [ ] **AC-021.1: Form error wiring**
  - **Given** product-form with validation errors
  - **When** a field is invalid and touched
  - **Then** the error message has an `id`, the input has `aria-describedby` pointing
    to that `id`, and the input has `aria-invalid="true"`

- [ ] **AC-021.2: Table accessibility**
  - **Given** any data table (cart, order-confirmation, import-errors)
  - **When** a screen reader encounters the table
  - **Then** `<th>` elements have `scope="col"` and the table has a `<caption>`
    (visually hidden via sr-only)

- [ ] **AC-021.3: Contextual button labels**
  - **Given** buttons in repeated list items (product-card, cart-item, search-results)
  - **When** a screen reader encounters the button
  - **Then** the button has an `aria-label` that includes the product name
    (e.g., `aria-label="View Wireless Mouse"`)

- [ ] **AC-021.4: Dynamic content announcement**
  - **Given** import-results polling or cart updates
  - **When** content changes dynamically
  - **Then** an `aria-live="polite"` region announces the change

- [ ] **AC-021.5: Tables responsive on mobile**
  - **Given** a data table (cart, order-confirmation, import-errors)
  - **When** the viewport is below `md` breakpoint
  - **Then** the table transforms to a stacked card layout

- [ ] **AC-021.6: Touch targets**
  - **Given** any button or interactive control
  - **When** rendered on a touch device
  - **Then** the touch target is at least 44px in the smallest dimension

- [ ] **AC-021.7: Search-results deduplication**
  - **Given** the search-results component
  - **When** rendering product cards
  - **Then** it reuses the `app-product-card` component instead of duplicating markup

- [ ] **AC-021.8: Input labels**
  - **Given** filter inputs (product-list search, search-filters keyword/category)
  - **When** a screen reader encounters the input
  - **Then** the input has an associated `<label>` (visible or sr-only)

## Definition of Done

- [ ] All ACs pass with automated test evidence
- [ ] All existing frontend tests still pass (zero regressions)
- [ ] INDEX.md updated

## Handoff Files

- [T-024](../subtasks/ep08/T-024-product-views-ux.md) --- Product views overhaul
- [T-025](../subtasks/ep08/T-025-search-import-views-ux.md) --- Search & import views overhaul
- [T-026](../subtasks/ep08/T-026-cart-checkout-views-ux.md) --- Cart, checkout & order views overhaul
