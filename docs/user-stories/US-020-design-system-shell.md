> [INDEX](../INDEX.md) / [EP08](../epics/EP08-ux-overhaul.md) / US-020

# US-020 --- Design System & App Shell

## Metadata

| Field | Value |
|-------|-------|
| Epic | EP08 --- UI/UX Overhaul |
| Priority | Must Have |
| Estimation | M |
| Status | Ready |

## Story

As a user navigating the application, I want a consistent visual identity with
accessible navigation and clear page context, so that the interface feels
professional and I can orient myself regardless of device or assistive technology.

## Acceptance Criteria

- [ ] **AC-020.1: Design tokens defined**
  - **Given** `frontend/src/styles.css`
  - **When** the file is read
  - **Then** a `@theme` block defines brand colors, a focus ring token, and a
    typography scale --- no raw `blue-600` or `gray-200` appear in any template

- [ ] **AC-020.2: Focus-visible on all interactive elements**
  - **Given** any button, link, or input in the application
  - **When** the element receives keyboard focus
  - **Then** a visible focus ring appears (using `focus-visible:` utilities)

- [ ] **AC-020.3: Skip-to-content link**
  - **Given** any page in the application
  - **When** the user presses Tab as the first action
  - **Then** a "Skip to main content" link appears and, when activated, moves focus
    to the `<main>` element

- [ ] **AC-020.4: Route titles**
  - **Given** any route in `app.routes.ts`
  - **When** the user navigates to that route
  - **Then** the browser tab title updates to reflect the current page
    (e.g., "Products | E-Commerce")

- [ ] **AC-020.5: Mobile navigation**
  - **Given** a viewport below `md` breakpoint (768px)
  - **When** the user views the header
  - **Then** navigation links collapse into a hamburger menu that toggles open/closed

- [ ] **AC-020.6: Active link state**
  - **Given** the navigation bar
  - **When** the user is on a route
  - **Then** the corresponding nav link has a visually distinct active state
    and `aria-current="page"`

## Definition of Done

- [ ] All ACs pass with automated test evidence
- [ ] All existing frontend tests still pass (zero regressions)
- [ ] INDEX.md updated

## Handoff Files

- [T-023](../subtasks/ep08/T-023-design-system-shell.md) --- Design system foundation + app shell
