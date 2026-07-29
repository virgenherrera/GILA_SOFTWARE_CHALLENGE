# T-024 --- Product Views UX Overhaul

## Metadata

| Field | Value |
|-------|-------|
| Task ID | T-024 |
| Epic | EP08 --- UI/UX Overhaul |
| Story | [US-021](../../user-stories/US-021-component-ux.md) |
| Persona | Angular A11y & Responsive Specialist |
| Model Tier | standard |
| Priority | Must Have |
| Depends On | T-023 (design tokens must exist first) |

## Objective

Apply accessibility (ARIA), responsive, and visual polish improvements to the 4
product view components: product-card, product-list, product-detail, product-form.
PASS when all form error `<p>` elements have `id` + input has `aria-describedby` +
`aria-invalid`, all repeated buttons have contextual `aria-label`, touch targets are
at least 44px (py-2.5 or h-11 on mobile), filter inputs have sr-only labels, and all
tests are green; FAIL otherwise.

## Pre-conditions

- [ ] T-023 is DONE --- `@theme` tokens are defined, color classes already replaced
  with token names in all templates
- [ ] `product-form.html` has 6 error `<p>` elements with no `id` or
  `aria-describedby` (confirmed by reading the file)
- [ ] `product-card.html` buttons say "View", "Edit", "Delete" with no product
  context (confirmed: lines 24-42)
- [ ] `product-list.html` search input has no `<label>`, uses `placeholder` only
  (confirmed: line 15)
- [ ] `product-card.html` button height is `py-1.5` (~32px) --- below 44px
  (confirmed: lines 24-42)

## Context Bundle

| File | Lines | Why Needed |
|------|-------|------------|
| `frontend/src/app/products/product-card/product-card.html` | 1-45 (full) | Add aria-labels to buttons, increase touch targets |
| `frontend/src/app/products/product-card/product-card.ts` | full | May need product() signal for aria-label binding |
| `frontend/src/app/products/product-card/product-card.spec.ts` | full | Add a11y tests |
| `frontend/src/app/products/product-list/product-list.html` | 1-82 (full) | Add sr-only label for search input, responsive pagination, touch targets, empty state |
| `frontend/src/app/products/product-list/product-list.ts` | full | Component logic |
| `frontend/src/app/products/product-list/product-list.spec.ts` | full | Add a11y tests |
| `frontend/src/app/products/product-detail/product-detail.html` | 1-77 (full) | Add aria-labels to Edit/Delete buttons, responsive layout |
| `frontend/src/app/products/product-detail/product-detail.ts` | full | May need product() for aria-label |
| `frontend/src/app/products/product-detail/product-detail.spec.ts` | full | Add a11y tests |
| `frontend/src/app/products/product-form/product-form.html` | 1-123 (full) | Wire aria-describedby + aria-invalid on all 6 fields, responsive form |
| `frontend/src/app/products/product-form/product-form.ts` | full | Component logic |
| `frontend/src/app/products/product-form/product-form.spec.ts` | full | Add form a11y tests |

## Deliverables

### Files to Modify

| File | Change |
|------|--------|
| `frontend/src/app/products/product-card/product-card.html` | Add `[attr.aria-label]="'View ' + product().name"` (and Edit, Delete) to each button. Add mobile touch target: `py-1.5 sm:py-1.5` → `py-2.5 sm:py-1.5` (44px mobile, compact desktop) |
| `frontend/src/app/products/product-list/product-list.html` | Add sr-only `<label for="product-search">` before the search `<input>`. Add `id="product-search"` to the input. Add sr-only `<label for="product-category">` before the category `<select>`. Add `id="product-category"` to the select. Pagination: stack vertically on mobile (`flex-col sm:flex-row`). Empty state: add icon + CTA link |
| `frontend/src/app/products/product-list/product-list.spec.ts` | Add test: search input has associated label |
| `frontend/src/app/products/product-detail/product-detail.html` | Add `[attr.aria-label]="'Edit ' + p.name"` and `[attr.aria-label]="'Delete ' + p.name"` to buttons. Touch targets: `py-1.5 sm:py-1.5` → `py-2.5 sm:py-1.5` |
| `frontend/src/app/products/product-form/product-form.html` | For each of the 6 form fields: add `id="sku-error"` to error `<p>`, add `[attr.aria-describedby]="form.controls.sku.invalid && form.controls.sku.touched ? 'sku-error' : null"` and `[attr.aria-invalid]="form.controls.sku.invalid && form.controls.sku.touched"` to the `<input>`. Add `border-danger-300` class when invalid. Price/Stock row: `flex gap-4` → `flex flex-col gap-4 sm:flex-row sm:gap-4` |
| `frontend/src/app/products/product-form/product-form.spec.ts` | Add test: invalid field has aria-invalid and aria-describedby |
| `frontend/src/app/products/product-card/product-card.spec.ts` | Add test: buttons have aria-label including product name |

## Quality Gates

| # | Gate | Command | Pass Criteria |
|---|------|---------|----------------|
| 1 | Handoff exists | `test -f docs/subtasks/ep08/T-024-product-views-ux.md` | exit 0 |
| 2 | Frontend tests pass | `cd frontend && CI=true pnpm exec ng test --configuration=ci` | exit 0 |
| 3 | Form aria wiring | `rg 'aria-describedby\|aria-invalid' frontend/src/app/products/product-form/product-form.html` | at least 6 matches (one per field) |
| 4 | Button aria-labels | `rg 'aria-label' frontend/src/app/products/product-card/product-card.html` | at least 3 matches |
| 5 | Input labels | `rg 'sr-only.*label\|label.*sr-only\|<label' frontend/src/app/products/product-list/product-list.html` | at least 2 matches |
| 6 | Touch targets | `rg 'py-2.5\|h-11' frontend/src/app/products/product-card/product-card.html` | at least 1 match |
| 7 | No side effects | `git diff --stat` | Only files listed in Deliverables |

## Boundaries

- NOT in scope: Modifying any file outside `frontend/src/app/products/` --- other
  components belong to T-025 and T-026
- NOT in scope: Design tokens or global styles --- T-023 handles those
- NOT in scope: Creating new shared component files --- keep existing structure, apply
  improvements inline
- NOT in scope: search-results deduplication --- T-025 will handle reusing product-card

## Anti-patterns

| What | Why It Fails | Do Instead |
|------|---------------|------------|
| Add aria-label as static string "View" | Same ambiguity for screen readers | Use dynamic: `[attr.aria-label]="'View ' + product().name"` |
| Use `aria-hidden="true"` on buttons to "fix" the issue | Hides the button from AT entirely | Keep button visible to AT, add descriptive aria-label |
| Change `py-1.5` to `py-3` everywhere (even desktop) | Desktop buttons become oversized | Mobile-first: `py-2.5 sm:py-1.5` (44px mobile, compact desktop) |

## Rollback Guidance

```bash
git checkout -- frontend/src/app/products/
```

## Compact Rules

### PROJECT-TEST

- AXIOM-ECHO: every code change runs the Echo System before commit
- All tests must pass before any commit
- TDD Cycle (Red/Green/Refactor) is mandatory
- Breaking an existing test is a blocking issue

### PROJECT-TDD

- Red: write test -> run -> MUST fail -> verify failure is assertion not syntax
- Green: write MINIMUM code -> run -> MUST pass -> full suite -> no regressions
- Refactor: apply SOLID/KISS/DRY/YAGNI -> after EACH refactor: full suite -> if fail: REVERT

### PROJECT-ANTI-DRIFT

- AXIOM-HANDOFF: no code without an approved handoff file
- Scope is defined by the handoff -- work outside boundaries is a violation
- Dead code and unused dependencies MUST be removed

## Status Protocol

```text
Status: DONE
Progress: 7/7 items
Blocker: none
```

## Progress Tracker

### Deliverables

- [x] `product-card.html` (aria-labels, touch targets)
- [x] `product-card.spec.ts` (aria-label test)
- [x] `product-list.html` (sr-only labels, responsive pagination, empty state)
- [x] `product-list.spec.ts` (label test)
- [x] `product-detail.html` (aria-labels, touch targets)
- [x] `product-form.html` (aria-describedby, aria-invalid, responsive layout)
- [x] `product-form.spec.ts` (form a11y test)

### Quality Gates

- [x] Gate 1: Handoff exists
- [x] Gate 2: Frontend tests pass
- [x] Gate 3: Form aria wiring
- [x] Gate 4: Button aria-labels
- [x] Gate 5: Input labels
- [x] Gate 6: Touch targets
- [x] Gate 7: No side effects
