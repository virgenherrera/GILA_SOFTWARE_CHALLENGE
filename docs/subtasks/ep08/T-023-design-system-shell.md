# T-023 --- Design System Foundation & App Shell

## Metadata

| Field | Value |
|-------|-------|
| Task ID | T-023 |
| Epic | EP08 --- UI/UX Overhaul |
| Story | [US-020](../../user-stories/US-020-design-system-shell.md) |
| Persona | Angular UX/Design Systems Specialist |
| Model Tier | standard |
| Priority | Must Have |
| Depends On | none (must complete before T-024, T-025, T-026) |

## Objective

Establish a Tailwind v4 `@theme` design token system in `styles.css`, add global
focus-visible states, a skip-to-content link, route titles via Angular's
`TitleStrategy`, a mobile hamburger nav with `routerLinkActive`, and replace all raw
Tailwind color classes across ALL 15 templates with the new token names. PASS when
`rg 'blue-600|blue-700|blue-800|gray-200|gray-300|gray-500|gray-700|gray-900|red-600|red-700|green-700|green-800' frontend/src/app/`
returns zero matches (all replaced by token names) and all tests are green; FAIL
otherwise.

## Pre-conditions

- [ ] `frontend/src/styles.css` contains only `@import 'tailwindcss'` with no `@theme`
  block (confirmed: 3 lines total)
- [ ] Zero `focus-visible:` or `focus:` classes in any template
  (confirmed: `rg 'focus-visible:|focus:' frontend/src/app/` returns zero)
- [ ] No skip-to-content link in `app.html` (confirmed: 31 lines, no skip link)
- [ ] No route titles in `app.routes.ts` (confirmed: no `title:` property on routes)
- [ ] No `routerLinkActive` in `app.html` (confirmed: nav uses plain `routerLink`)
- [ ] No mobile hamburger menu (confirmed: nav is always-visible flex row)

## Context Bundle

| File | Lines | Why Needed |
|------|-------|------------|
| `frontend/src/styles.css` | 1-3 (full) | Add @theme tokens and global focus/transition rules |
| `frontend/src/app/app.html` | 1-31 (full) | Add skip-to-content, hamburger nav, routerLinkActive, aria-current |
| `frontend/src/app/app.ts` | 1-17 (full) | Add hamburger toggle signal, RouterLinkActive import |
| `frontend/src/app/app.css` | empty | Add hamburger/nav mobile styles, skip-link styles |
| `frontend/src/app/app.routes.ts` | 1-11 (full) | Add title property to each route |
| `frontend/src/app/app.config.ts` | full | Add TitleStrategy provider |
| `frontend/src/app/app.spec.ts` | full | Update tests for new nav structure |
| ALL 15 `.html` template files | full | Replace raw Tailwind colors with token names |

## Deliverables

### Files to Modify

| File | Change |
|------|--------|
| `frontend/src/styles.css` | Add `@theme` block with tokens: `--color-brand-*` (replacing blue-600/700/800), `--color-surface-*` (replacing gray/white), `--color-danger-*` (replacing red-*), `--color-success-*` (replacing green-*), `--color-focus-ring`. Add global `*:focus-visible` outline rule using the focus ring token. Add global `transition-colors` on buttons and links |
| `frontend/src/app/app.html` | Add skip-to-content `<a>` before header. Add `id="main-content"` on `<main>`. Add `routerLinkActive` + `aria-current="page"` on nav links. Add mobile hamburger button (hidden md:flex pattern). Wrap nav links in a toggleable div |
| `frontend/src/app/app.ts` | Add `menuOpen = signal(false)` for hamburger toggle. Import `RouterLinkActive`. Add `toggleMenu()` method |
| `frontend/src/app/app.css` | Skip-link styles (sr-only until focused). Mobile nav transition styles |
| `frontend/src/app/app.routes.ts` | Add `title: 'Products'`, `title: 'Import'`, etc. to each route definition |
| `frontend/src/app/app.config.ts` | Add custom TitleStrategy that appends ` | E-Commerce` suffix |
| `frontend/src/app/app.spec.ts` | Update to test new nav structure (hamburger, skip-link) |
| `frontend/src/app/products/product-card/product-card.html` | Replace `text-blue-600` → `text-brand-600`, `border-gray-200` → `border-surface-200`, etc. |
| `frontend/src/app/products/product-list/product-list.html` | Same color token replacement |
| `frontend/src/app/products/product-detail/product-detail.html` | Same color token replacement |
| `frontend/src/app/products/product-form/product-form.html` | Same color token replacement |
| `frontend/src/app/search/search-page/search-page.html` | Same color token replacement (read file to check for color classes) |
| `frontend/src/app/search/search-results/search-results.html` | Same color token replacement |
| `frontend/src/app/search/search-filters/search-filters.html` | Same color token replacement |
| `frontend/src/app/cart/cart-page/cart-page.html` | Same color token replacement |
| `frontend/src/app/cart/cart-item/cart-item.html` | Same color token replacement |
| `frontend/src/app/checkout/checkout-page/checkout-page.html` | Same color token replacement |
| `frontend/src/app/checkout/order-confirmation/order-confirmation.html` | Same color token replacement |
| `frontend/src/app/imports/import-upload/import-upload.html` | Same color token replacement |
| `frontend/src/app/imports/import-results/import-results.html` | Same color token replacement |
| `frontend/src/app/imports/import-errors/import-errors.html` | Same color token replacement |

### Token Mapping Reference

This is the mapping from raw Tailwind to @theme tokens. Define these in `@theme`:

| Raw Class | Token Name | Usage |
|-----------|------------|-------|
| `blue-600` | `brand-600` | Primary actions, links |
| `blue-700` | `brand-700` | Hover states |
| `blue-800` | `brand-800` | Active/pressed states |
| `blue-300` | `brand-300` | Secondary button borders |
| `blue-50` | `brand-50` | Secondary button hover bg |
| `red-600` | `danger-600` | Error text, destructive badge |
| `red-700` | `danger-700` | Error messages, destructive buttons |
| `red-300` | `danger-300` | Destructive button borders |
| `red-100` | `danger-100` | Error badge bg |
| `red-50` | `danger-50` | Error alert bg, destructive hover |
| `green-700` | `success-700` | Success values |
| `green-800` | `success-800` | Success text |
| `green-200` | `success-200` | Success alert border |
| `green-50` | `success-50` | Success alert bg |
| `gray-900` | `surface-900` | Primary text |
| `gray-700` | `surface-700` | Secondary text, label text |
| `gray-600` | `surface-600` | Tertiary text |
| `gray-500` | `surface-500` | Muted text, placeholder |
| `gray-400` | `surface-400` | Disabled text |
| `gray-300` | `surface-300` | Borders |
| `gray-200` | `surface-200` | Light borders, dividers |
| `gray-100` | `surface-100` | Subtle backgrounds, table rows |
| `gray-50` | `surface-50` | Table headers, hover bg |

In Tailwind v4 `@theme`, define these as:
```css
@theme {
  --color-brand-50: /* same value as Tailwind blue-50 */;
  --color-brand-300: /* ... */;
  --color-brand-600: /* ... */;
  /* etc. */
}
```

Then in templates: `text-blue-600` becomes `text-brand-600`, `bg-gray-50` becomes
`bg-surface-50`, etc. The class naming convention follows Tailwind's pattern with
your custom token names.

## Quality Gates

| # | Gate | Command | Pass Criteria |
|---|------|---------|----------------|
| 1 | Handoff exists | `test -f docs/subtasks/ep08/T-023-design-system-shell.md` | exit 0 |
| 2 | Frontend tests pass | `cd frontend && CI=true pnpm exec ng test --configuration=ci` | exit 0 |
| 3 | No raw color classes | `rg 'text-blue-\|bg-blue-\|border-blue-\|text-gray-\|bg-gray-\|border-gray-\|text-red-\|bg-red-\|border-red-\|text-green-\|bg-green-\|border-green-' frontend/src/app/ --glob '*.html'` | zero matches |
| 4 | @theme block exists | `rg '@theme' frontend/src/styles.css` | at least 1 match |
| 5 | Focus-visible rule | `rg 'focus-visible' frontend/src/styles.css` | at least 1 match |
| 6 | Skip-to-content link | `rg 'skip.*main\|Skip.*content' frontend/src/app/app.html` | at least 1 match |
| 7 | Route titles | `rg "title:" frontend/src/app/app.routes.ts` | at least 3 matches |
| 8 | routerLinkActive | `rg 'routerLinkActive' frontend/src/app/app.html` | at least 1 match |
| 9 | Mobile hamburger | `rg 'menuOpen\|menu-open\|toggleMenu' frontend/src/app/app.ts` | at least 1 match |
| 10 | No side effects | `git diff --stat` | Only files listed in Deliverables |

## Boundaries

- NOT in scope: Dark mode theme --- only light theme tokens for MVP
- NOT in scope: Component-level a11y (form aria, table scope, button labels) --- those
  belong to T-024/T-025/T-026
- NOT in scope: Responsive layout changes per component (table-to-cards, touch targets,
  form stacking) --- those belong to T-024/T-025/T-026
- NOT in scope: Component deduplication (search-results reusing product-card) --- T-025
- NOT in scope: New shared component files (app-button, app-badge) --- apply tokens to
  existing inline classes; shared components are a future refactor

## Anti-patterns

| What | Why It Fails | Do Instead |
|------|---------------|------------|
| Define tokens with CSS custom properties outside @theme | Tailwind v4 requires @theme for utility generation | Use `@theme { --color-brand-600: #2563eb; }` so `text-brand-600` works as a utility class |
| Add focus-visible per element in templates | 200+ elements, unmaintainable | Add ONE global rule in styles.css: `*:focus-visible { outline: 2px solid var(--color-focus-ring); outline-offset: 2px; }` |
| Create a separate hamburger component | Over-engineering for a toggle button | Inline in app.html with a signal in app.ts |
| Leave some templates with raw colors "for later" | Creates inconsistency, parallel workers in Wave 2 will need to know which token to use | Replace ALL 15 templates in this task |

## Rollback Guidance

```bash
git checkout -- frontend/src/styles.css
git checkout -- frontend/src/app/app.html
git checkout -- frontend/src/app/app.ts
git checkout -- frontend/src/app/app.css
git checkout -- frontend/src/app/app.routes.ts
git checkout -- frontend/src/app/app.config.ts
git checkout -- frontend/src/app/app.spec.ts
# For templates: git checkout -- frontend/src/app/
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
Status: [IN_PROGRESS | BLOCKED | DONE | FAILED]
Progress: X/Y items
Blocker: (if applicable)
```

## Progress Tracker

### Deliverables

- [x] `styles.css` (@theme tokens, focus-visible, transitions)
- [x] `app.html` (skip-to-content, hamburger nav, routerLinkActive)
- [x] `app.ts` (menuOpen signal, RouterLinkActive import)
- [x] `app.css` (skip-link styles, nav mobile styles)
- [x] `app.routes.ts` (route titles)
- [x] `app.config.ts` (TitleStrategy)
- [x] `app.spec.ts` (updated tests)
- [x] All 15 templates (color token replacement)

### Quality Gates

- [x] Gate 1: Handoff exists
- [x] Gate 2: Frontend tests pass (149/149 green)
- [x] Gate 3: No raw color classes (zero matches in *.html)
- [x] Gate 4: @theme block exists
- [x] Gate 5: Focus-visible rule
- [x] Gate 6: Skip-to-content link
- [x] Gate 7: Route titles (5 routes)
- [x] Gate 8: routerLinkActive (4 nav links)
- [x] Gate 9: Mobile hamburger (menuOpen + toggleMenu)
- [x] Gate 10: No side effects (21 files in scope)

### Notes

- Worker extended @theme with `--color-brand-400` and `--color-success-300` for two
  color classes not in the original mapping (border-blue-400 in import-upload,
  border-green-300 in search-page). Correct approach.
- `import-results.ts` has STATUS_CLASSES map with raw Tailwind colors in TypeScript.
  Not in scope (html-only gate). Flagged for T-025.
