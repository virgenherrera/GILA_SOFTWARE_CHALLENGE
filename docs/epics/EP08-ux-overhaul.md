> [INDEX](../INDEX.md) / [Epics](./) / EP08 --- UI/UX Overhaul

# EP08 --- UI/UX Overhaul

## Summary

This epic addresses systemic UX deficits identified by a 3-auditor review (Visual
Design, WCAG 2.1 AA Accessibility, Responsive/Mobile-First) across all 15 frontend
templates. The root cause is a missing design system layer: zero `@theme` tokens,
zero shared components, zero focus states, 1 total ARIA attribute, and only 4/15
templates using responsive breakpoints. The existing markup uses raw Tailwind defaults
(blue-600, gray-200) with no brand identity, and visual patterns (buttons, badges,
alerts, cards) are copy-pasted and already drifting.

## Business Value

A functional UI that fails accessibility, mobile, and visual polish standards
undermines the professionalism of the entire application. This epic transforms the
frontend from "works but looks default" to "works and looks intentional" --- establishing
a design token system, achieving WCAG 2.1 AA baseline, ensuring mobile-first
responsiveness, and eliminating copy-paste component drift.

## Audit Findings Summary

| Dimension | Critical | Major | Minor |
|-----------|----------|-------|-------|
| Visual Design | 2 | 3 | 4 |
| Accessibility | 4 | 4 | 3 |
| Responsive | 2 | 7 | 4 |
| **Total** | **8** | **14** | **11** |

## User Stories

- [ ] **Must Have** --- US-020: Design System & App Shell --- `@theme` tokens, focus states,
  typography scale, skip-to-content, route titles, mobile nav, active link state
- [ ] **Must Have** --- US-021: Component-Level UX --- ARIA attributes, responsive layouts,
  touch targets, table-to-card mobile patterns, component deduplication, empty states

## Acceptance Boundaries

- This epic modifies ONLY frontend templates, styles, and component TypeScript --- no
  backend changes, no API changes, no new routes, no new business logic
- All existing acceptance criteria from EP01-EP05 remain intact --- a passing test
  today must still pass after the overhaul
- Color tokens replace raw Tailwind values; no new colors are invented, only the
  existing palette is systematized under `@theme`
- The app must look and function correctly in both light mode (no dark mode required
  for MVP)
- All changes target WCAG 2.1 AA --- AAA is explicitly out of scope

## Execution Strategy

Wave 1 (sequential): T-023 establishes the design system foundation that all
subsequent tasks consume.

Wave 2 (parallel, 3 workers): T-024 / T-025 / T-026 each own a disjoint set of
templates, applying all three concerns (a11y, responsive, visual polish) to their
owned files. Zero file overlap between parallel workers.

```
T-023 (foundation) ──┬── T-024 (product views)
                     ├── T-025 (search + import views)
                     └── T-026 (cart + checkout views)
```

## Related Architecture

- [Tech Stack](../architecture/tech-stack.md) --- frontend framework and testing config
- [EP05 --- User Interface](EP05-user-interface.md) --- parent feature set being overhauled
