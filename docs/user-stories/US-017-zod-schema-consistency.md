> [INDEX](../INDEX.md) / [User Stories](./) / US-017 --- Zod Schema Consistency

# US-017 --- Zod Schema Consistency

## 1. Metadata

| Field | Value |
| ----- | ----- |
| Epic | [EP07 --- Angular 22 Modernization](../epics/EP07-angular-22-modernization.md) |
| Priority | Must Have |
| Status | Done |
| Estimation | S |

## 2. Story

As a developer, I want every domain entity to have a single canonical Zod schema in
`shared/validation/`, so that type definitions are not duplicated as local interfaces
across services and consumers and cannot silently drift from one another.

## 3. Definition of Ready

- [x] Domain entity contract frozen (Cart, Order/Checkout, Import Job/Error already
  frozen by EP04/EP02 API contracts)
- [x] Interface or API contract frozen (`docs/architecture/api-contract.md`)
- [x] Input validation rules enumerated with exact boundaries (inherited from existing
  Malli/API contracts -- this story does not change validation rules, only their home)
- [x] Edge cases identified with boundary behavior defined (N/A -- pure refactor, no new
  behavior)
- [x] Dependencies identified and resolved (`product.schema.ts` already existed from
  T-011 and served as the pattern to replicate)
- [x] Test plan exists with test names mapped to ACs
- [x] Out-of-scope items listed
- [x] Role-gate review completed (retroactive documentation of already-executed work,
  recorded 2026-07-29)

## 4. Acceptance Criteria

- [x] **AC-017.1: Every domain entity has a Zod schema in shared/validation/**
  - **Given** the domain entities Cart, Order, and Import Job/Error are used across
    multiple services and components
  - **When** the codebase is inspected under `frontend/src/app/shared/validation/`
  - **Then** `cart.schema.ts`, `checkout.schema.ts`, and `import.schema.ts` exist
    alongside the pre-existing `product.schema.ts`
  - **And** each schema exports both the Zod schema object and its inferred TypeScript
    type for consumption elsewhere

- [x] **AC-017.2: No manual TypeScript interfaces for domain entities in service files**
  - **Given** `cart.service.ts`, `checkout.service.ts`, and `import.service.ts`
    previously declared local `interface` blocks duplicating entity shapes
  - **When** those service files are inspected after this story
  - **Then** no service file redeclares a domain entity shape as a local `interface`
  - **And** each service imports its entity types from the corresponding
    `shared/validation/*.schema.ts` file

- [x] **AC-017.3: All consumer imports point to schema files**
  - **Given** components that consume Cart, Order, or Import data (cart items, cart
    page, checkout's order confirmation, import results, import errors, search page)
  - **When** those component files are inspected after this story
  - **Then** every type reference for a migrated entity imports from
    `shared/validation/*.schema.ts`, not from a service-local interface
  - **And** the corresponding `.spec.ts` files are updated to import from the same
    schema source, keeping test fixtures in sync with the runtime types

## 5. Definition of Done

- [x] All ACs pass with automated test evidence (existing suites re-run green after the
  import-path changes)
- [x] No regressions in existing test suite -- diffs are additive schema files plus
  import-path edits, no assertion changes
- [x] Code reviewed
- [ ] INDEX.md updated (pending orchestrator action outside this handoff's scope)

## 6. Deliverables

### Files Created

| File Path | Contents |
| --------- | -------- |
| `frontend/src/app/shared/validation/cart.schema.ts` | Zod schema + inferred type for the Cart entity (cart items, totals) |
| `frontend/src/app/shared/validation/checkout.schema.ts` | Zod schema + inferred type for the Order entity returned by checkout/order endpoints |
| `frontend/src/app/shared/validation/import.schema.ts` | Zod schemas + inferred types for `ImportJob`, `ImportStatus`, and `ImportError` |

### Files Modified

| File Path | Change |
| --------- | ------ |
| `frontend/src/app/cart/cart.service.ts` | Removed local Cart interfaces; imports types from `cart.schema.ts` |
| `frontend/src/app/cart/cart.service.spec.ts` | Updated fixtures to import types from `cart.schema.ts` |
| `frontend/src/app/cart/cart-item/cart-item.ts` | Updated type import to `cart.schema.ts` |
| `frontend/src/app/cart/cart-item/cart-item.spec.ts` | Updated fixture type import |
| `frontend/src/app/cart/cart-page/cart-page.spec.ts` | Updated fixture type import |
| `frontend/src/app/checkout/checkout.service.ts` | Removed local Order interface; imports `Order` from `checkout.schema.ts` |
| `frontend/src/app/checkout/checkout.service.spec.ts` | Updated fixtures to import from `checkout.schema.ts` |
| `frontend/src/app/checkout/order-confirmation/order-confirmation.ts` | Updated type import to `checkout.schema.ts` |
| `frontend/src/app/checkout/order-confirmation/order-confirmation.spec.ts` | Updated fixture type import |
| `frontend/src/app/imports/import.service.ts` | Removed local ImportJob/ImportError interfaces; imports from `import.schema.ts` |
| `frontend/src/app/imports/import.service.spec.ts` | Updated fixtures to import from `import.schema.ts` |
| `frontend/src/app/imports/import-results/import-results.ts` | Updated type import to `import.schema.ts` |
| `frontend/src/app/imports/import-results/import-results.spec.ts` | Updated fixture type import |
| `frontend/src/app/imports/import-errors/import-errors.ts` | Updated type import to `import.schema.ts` |
| `frontend/src/app/imports/import-errors/import-errors.spec.ts` | Updated fixture type import |
| `frontend/src/app/search/search-page/search-page.spec.ts` | Updated fixture type import (search page already consumed `product.schema.ts`) |

## 7. Test Plan

| Test Name | AC | Assertion |
| --------- | -- | --------- |
| `cart-service-uses-schema-types` | AC-017.1, AC-017.2 | `CartService` methods type-check against `Cart`/`CartItem` imported from `cart.schema.ts`; existing cart service spec suite passes unchanged |
| `checkout-service-uses-schema-types` | AC-017.1, AC-017.2 | `CheckoutService.checkout()`/`getOrder()` return `Order` imported from `checkout.schema.ts`; existing checkout spec suite passes unchanged |
| `import-service-uses-schema-types` | AC-017.1, AC-017.2 | `ImportService` methods type-check against `ImportJob`/`ImportError` imported from `import.schema.ts`; existing import spec suite passes unchanged |
| `no-local-interface-duplication` | AC-017.2 | No `interface Cart`, `interface Order`, `interface ImportJob`, or `interface ImportError` declaration remains in `cart.service.ts`, `checkout.service.ts`, or `import.service.ts` |
| `consumers-import-from-schema` | AC-017.3 | `rg "from '.*schema'" frontend/src/app/{cart,checkout,imports,search}/**/*.ts` covers every file that references a migrated entity type |

## 8. Validation Rules

N/A -- this story relocates existing type/validation definitions into `shared/validation/`
without changing any validation rule, boundary, or accepted/rejected value. Field-level
rules remain governed by the original API contract (`docs/architecture/api-contract.md`).

## 9. Risks

| Severity | Risk | Mitigation |
| -------- | ---- | ---------- |
| LOW | A consumer file is missed and still references a stale local interface | `rg` sweep (see Test Plan) run across `cart/`, `checkout/`, `imports/`, `search/` before marking done |
| LOW | Type-only refactor accidentally changes runtime behavior via a renamed/reshaped field | No field renames performed; schemas mirror the exact shapes of the interfaces they replace |

## 10. Out of Scope

- Adding Zod runtime validation calls (`.parse()`/`.safeParse()`) to Cart/Order/Import
  flows -- this story only consolidates *types*; `product.schema.ts` remains the only
  schema currently invoked at runtime for validation (in `product-form.ts`)
- Contract parity testing against backend Malli schemas for Cart/Order/Import (only
  `product.schema.ts` has a Malli contract test, per US-011 AC-011.13)
- Any change to `rxResource`/`resource()` usage -- that is US-018's scope
- Any change to validation rules, field boundaries, or accepted/rejected values

## 11. Notes

- This story was executed directly by a prior agent working session before this
  retroactive handoff/documentation pass; the Deliverables and Test Plan sections above
  describe what was actually done, verified via `git status`/`git diff --stat` against
  the working tree at documentation time (2026-07-29).
- `product.schema.ts` (created during T-011) was the reference pattern for the three new
  schema files -- no changes were needed to it.
- INDEX.md and `docs/subtasks/ep07/` handoff back-linking are updated by the
  orchestrator as part of closing this retroactive record, not by this document.

## 12. Related Documents

- [EP07 --- Angular 22 Modernization](../epics/EP07-angular-22-modernization.md) --- parent epic
- [US-011 --- Product Management Views](US-011-product-management-views.md) --- origin of the `product.schema.ts` pattern
- [US-018 --- rxResource Signal-Based Data Loading](US-018-rxresource-migration.md) --- sibling story completing the tech-stack.md compliance remediation

## 13. Handoff Files

No dedicated handoff file was created for this story -- the work was completed directly
prior to this retroactive documentation pass. This document itself serves as the
retroactive record required by `AGENTS.md`'s artifact-based ownership model.

## 14. Change Log

| Date | Change | Reason |
| ---- | ------ | ------ |
| 2026-07-29 | Initial creation (retroactive) | Documenting already-completed Zod schema consolidation work under the new EP07 umbrella |
