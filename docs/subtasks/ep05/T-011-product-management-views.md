# T-011 --- Product Management Views

## Metadata

| Field | Value |
|-------|-------|
| Task ID | T-011 |
| Batch | 2 |
| Epic | EP05 --- User Interface |
| Story | [US-011](../../user-stories/US-011-product-management-views.md) |
| Persona | Catalog Manager / Shopper |
| Model Tier | standard |
| Priority | Must Have |
| Depends On | T-002, T-003, T-004 |

## Objective

Implement Angular 22 product management views: product list with pagination, product detail, create/edit forms with Zod-driven inline validation mirroring backend Malli schemas, delete with confirmation dialog, and a Zod/Malli contract test ensuring frontend and backend validation stay in sync.

## Pre-conditions

- [ ] T-002 create product API functional
- [ ] T-003 update product API functional
- [ ] T-004 delete product API functional
- [ ] Angular 22 scaffold exists from T-001
- [ ] Backend running and accessible via Docker Compose

## Context Bundle

| File | Lines | Why Needed |
|------|-------|------------|
| docs/user-stories/US-011-product-management-views.md | all | Acceptance criteria for product views |
| docs/architecture/api-contract.md | all | Product API shapes, pagination params |
| docs/architecture/tech-stack.md | all | Angular 22, Zod version, component patterns |
| frontend/src/app/app.routes.ts | all | Existing routes to extend |
| frontend/src/app/shared/validation/product.schema.ts | all | Existing Zod schemas to reuse or extend |
| src/ecommerce/validation.clj | all | Backend Malli schemas for contract test reference |
| frontend/angular.json | all | Build/test configuration |
| docs/architecture/validation-pruning.md | all | Validation rules that Zod schemas must mirror |
| docs/architecture/error-handling.md | all | API error response shape for form error display |
| docs/architecture/security-guidelines.md | all | XSS: Angular auto-escapes template bindings, never use innerHTML |
| docs/architecture/tdd-workflow.md | all | TDD process for Angular components and Zod validators |
| docs/architecture/pnpm-config.md | all | pnpm configuration for frontend dependencies |

## Deliverables

### Files to Create

| File | Purpose |
|------|---------|
| frontend/src/app/products/product-list/product-list.component.ts | Container: paginated product table with actions |
| frontend/src/app/products/product-list/product-list.component.html | Template for product list |
| frontend/src/app/products/product-list/product-list.component.css | Styles for product list |
| frontend/src/app/products/product-list/product-list.component.spec.ts | Tests for product list |
| frontend/src/app/products/product-detail/product-detail.component.ts | Container: single product view |
| frontend/src/app/products/product-detail/product-detail.component.html | Template for product detail |
| frontend/src/app/products/product-detail/product-detail.component.css | Styles for product detail |
| frontend/src/app/products/product-detail/product-detail.component.spec.ts | Tests for product detail |
| frontend/src/app/products/product-form/product-form.component.ts | Container: create/edit form with Zod validation |
| frontend/src/app/products/product-form/product-form.component.html | Template for product form |
| frontend/src/app/products/product-form/product-form.component.css | Styles for product form |
| frontend/src/app/products/product-form/product-form.component.spec.ts | Tests for product form |
| frontend/src/app/products/product-card/product-card.component.ts | Presentational: product display card |
| frontend/src/app/products/product-card/product-card.component.html | Template for product card |
| frontend/src/app/products/product-card/product-card.component.css | Styles for product card |
| frontend/src/app/products/product-card/product-card.component.spec.ts | Tests for product card |
| frontend/src/app/products/product.service.ts | HTTP service for product CRUD operations |
| frontend/src/app/products/product.routes.ts | Product feature routes (list, detail, create, edit) |
| frontend/src/app/products/product.service.spec.ts | Tests for product service |

### Files to Modify

| File | Change |
|------|--------|
| frontend/src/app/app.routes.ts | Add lazy-loaded product routes |

## Quality Gates

| # | Gate | Command/Check | Type | Pass Criteria |
|---|------|---------------|------|---------------|
| 1 | Handoff exists | `test -f docs/subtasks/ep05/T-011-product-management-views.md` | EXE | exit 0 |
| 2 | Frontend tests pass | `docker compose run --rm frontend pnpm exec vitest run` | EXE | exit 0 |
| 3 | Frontend lint | `docker compose run --rm frontend pnpm exec ng lint` | EXE | exit 0 |
| 4 | Product list renders | Navigate to /products -> paginated table displayed | MANUAL | Products visible with columns |
| 5 | Pagination works | Click next/prev -> different page of results | MANUAL | Page changes, data updates |
| 6 | Create form validates | Submit empty form -> inline Zod errors shown per field | MANUAL | All invalid fields show errors |
| 7 | Create submits | Fill valid data, submit -> product created, redirect to list | MANUAL | 201 response, list refreshed |
| 8 | Edit pre-populates | Navigate to edit -> form filled with existing data, SKU disabled | MANUAL | Fields populated, SKU read-only |
| 9 | Edit submits | Change data, submit -> product updated | MANUAL | 200 response, changes visible |
| 10 | Delete confirmation | Click delete -> confirmation dialog shown | MANUAL | Dialog appears before deletion |
| 11 | Delete with references | Delete product in cart -> 409 handled gracefully | MANUAL | Error message shown, no crash |
| 12 | XSS safety | Product with `<script>` in name -> renders as text | MANUAL | No script execution |
| 13 | Loading state | Slow API -> loading indicator shown | MANUAL | Spinner or skeleton visible |
| 14 | Error state | API error -> error message displayed | MANUAL | User-friendly error shown |
| 15 | Empty state | No products -> "No products found" message | MANUAL | Empty state message visible |
| 16 | Zod/Malli contract | Contract test comparing Zod and Malli validation boundaries | EXE | Same inputs produce same valid/invalid results |
| 17 | No side effects | `git diff --stat` | EXE | Only expected files |

## Boundaries

- NOT in scope: CSV import UI (T-012)
- NOT in scope: Product search or filter UI (T-013)
- NOT in scope: Responsive/mobile layout
- NOT in scope: WCAG accessibility compliance
- NOT in scope: Internationalization (i18n)
- NOT in scope: Image upload for products

## Anti-patterns

| What | Why It Fails | Do Instead |
|------|-------------|------------|
| Use NgModule | Angular 22 standalone-only architecture | Use standalone components with `imports` array |
| Use Zone.js | Contradicts zoneless requirement | Use `provideZonelessChangeDetection()`, signals, `resource()` |
| Hardcode API URLs | Breaks when backend host changes | Use environment config or relative paths via nginx proxy |
| Business logic in presentational components | Violates container-presentational separation | Keep logic in containers/services; presentational uses input()/output() |
| Manual change detection | Zone.js habit, unnecessary with signals | Use signal(), computed(), resource() for reactivity |
| Template-driven forms | Less control over validation timing | Use reactive forms with Zod validation adapter |

## Rollback Guidance

```bash
git checkout -- frontend/src/app/products/
git checkout -- frontend/src/app/app.routes.ts
```

This removes all product view components, service, routes, and restores the root routes.

## Compact Rules

### PROJECT-TEST
- All tests must pass before any commit
- TDD (Red/Green/Refactor) is the default
- Breaking an existing test is a blocking issue
- Tests map directly to acceptance criteria
- Test evidence is required for DOD

### PROJECT-ANTI-DRIFT
- Scope is defined by the handoff --- work outside boundaries is a violation
- Version pinning: exact versions only
- Dead code MUST be removed

### PROJECT-PIPELINE
- Pipeline: install -> build -> lint -> test:unit -> test:integration
- Failing stage STOPS the pipeline

## Status Protocol

```
Status: [IN_PROGRESS | BLOCKED | DONE | FAILED]
Progress: X/Y items
Blocker: (if applicable)
```

## Progress Tracker

### Deliverables
- [ ] frontend/src/app/products/product-list/ (component + spec)
- [ ] frontend/src/app/products/product-detail/ (component + spec)
- [ ] frontend/src/app/products/product-form/ (component + spec)
- [ ] frontend/src/app/products/product-card/ (component + spec)
- [ ] frontend/src/app/products/product.service.ts
- [ ] frontend/src/app/products/product.service.spec.ts
- [ ] frontend/src/app/products/product.routes.ts
- [ ] frontend/src/app/app.routes.ts (modified)

### Quality Gates
- [ ] Gate 1: Handoff exists
- [ ] Gate 2: Frontend tests pass
- [ ] Gate 3: Frontend lint passes
- [ ] Gate 4: Product list renders
- [ ] Gate 5: Pagination works
- [ ] Gate 6: Create form validates with Zod
- [ ] Gate 7: Create submits successfully
- [ ] Gate 8: Edit pre-populates (SKU disabled)
- [ ] Gate 9: Edit submits successfully
- [ ] Gate 10: Delete confirmation dialog
- [ ] Gate 11: Delete with references handled (409)
- [ ] Gate 12: XSS safety
- [ ] Gate 13: Loading state
- [ ] Gate 14: Error state
- [ ] Gate 15: Empty state
- [ ] Gate 16: Zod/Malli contract test
- [ ] Gate 17: No side effects
