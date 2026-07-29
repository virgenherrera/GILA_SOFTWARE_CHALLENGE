> [INDEX](../INDEX.md) / [Epics](./) / EP07 --- Angular 22 Modernization

# EP07 --- Angular 22 Modernization

## Summary

This epic remediates an architectural deviation between the documented frontend contract
and the actual EP05 implementation. `docs/architecture/tech-stack.md` (line 148) commits
the project to "signals everywhere": component state via `signal()`, computed state via
`computed()`, and async data via `resource()`, with **no `subscribe()` calls in
components**. The delivered code instead uses `HttpClient` Observables with manual
`subscribe()`/`takeUntilDestroyed()` wiring across every feature, and domain types were
originally duplicated as local interfaces instead of living in shared Zod schemas. This
epic closes both gaps: it consolidates domain types under `shared/validation/` (done) and
migrates GET-based data loading to `rxResource` (in progress).

## Business Value

An architecture document that contradicts the shipped code is a defect in its own right --
it misleads the next agent or reviewer who trusts `tech-stack.md` as the source of truth,
and it signals that the Architect-phase decision was never enforced. Consolidating domain
types onto Zod schemas removes duplicated, divergence-prone type definitions across
services and consumers. Migrating to `rxResource` closes the signals-everywhere gap for
read paths, reduces manual subscription bookkeeping (and the leak risk that comes with
it), and brings the frontend into compliance with the tech stack it claims to follow --
without touching working business logic or backend contracts.

## User Stories

- [x] **Must Have** --- US-017: Zod Schema Consistency (DONE) --- every domain entity has
  a single canonical Zod schema; services and consumers import types from it instead of
  redeclaring local interfaces.
- [ ] **Should Have** --- US-018: rxResource Signal-Based Data Loading --- GET-based data
  loading in Product, Import, and Checkout views uses `rxResource` and Resource signals
  instead of `subscribe()`, closing the tech-stack.md compliance gap for read paths.

## Acceptance Boundaries

- This epic performs a mechanical remediation of an existing, already-shipped feature
  set (EP01-EP05); it introduces no new business rules, API endpoints, or user-facing
  behavior.
- Every story in this epic must leave existing acceptance criteria from EP01-EP05
  intact -- a passing test today must still pass after migration.
- Mutations (POST/PUT/DELETE) remain HttpClient Observables in every story of this
  epic; `resource()`/`rxResource` is documented and scoped to GET/data-fetching only.
- CartService's Observable-based global mutable state is explicitly out of scope for
  signal/resource migration in this epic (see US-018 AC-018.5); it requires a separate
  architectural decision, not a mechanical swap.

## Related Architecture

- [Tech Stack](../architecture/tech-stack.md) --- line 148 states the signals-everywhere
  / no-`subscribe()`-in-components invariant this epic remediates.
- [EP05 --- User Interface](EP05-user-interface.md) --- parent feature set whose views
  are being migrated.
- [US-011 --- Product Management Views](../user-stories/US-011-product-management-views.md),
  [US-012 --- CSV Import View](../user-stories/US-012-csv-import-view.md),
  [US-014 --- Cart & Checkout Views](../user-stories/US-014-cart-checkout-views.md) ---
  original stories whose implementations deviated from the documented pattern.
