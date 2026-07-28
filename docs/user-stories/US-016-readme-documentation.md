> [INDEX](../INDEX.md) / [User Stories](./) / US-016 --- README & Decision Documentation

# US-016 --- README & Decision Documentation

## 1. Metadata

| Field | Value |
| ----- | ----- |
| Epic | [EP06 --- Containerization & Documentation](../epics/EP06-containerization-docs.md) |
| Priority | Must Have |
| Status | Ready |

## 2. Story

**As an** Evaluator,
**I want** a README documenting the decisions made, alternatives considered, approach taken, and local run instructions,
**so that** I understand the engineering reasoning behind the solution.

## 3. Definition of Ready

- [x] Domain entity contract frozen
- [x] Interface or API contract frozen
- [x] Input validation rules enumerated with exact boundaries
- [x] Edge cases identified with boundary behavior defined
- [x] Dependencies identified and resolved or deferred
- [x] Test plan exists with test names mapped to ACs
- [x] Out-of-scope items listed
- [x] API contract endpoints touched by this story are defined

## 4. Acceptance Criteria

- [ ] **AC-016.1: README.md exists at repository root**
  - **Given** the repository is cloned
  - **When** the Evaluator looks at the repository root
  - **Then** a `README.md` file exists at the top level
  - **And** it is the primary entry point for understanding the project

- [ ] **AC-016.2: Run instructions are clear and complete**
  - **Given** the README contains a "Run Instructions" (or equivalent) section
  - **When** the Evaluator reads it
  - **Then** it documents the exact command: `docker compose up --build`
  - **And** it states the URL to access the application: `http://localhost:8080`
  - **And** no additional manual steps are required between the command and accessing the application

- [ ] **AC-016.3: Prerequisites list only Docker**
  - **Given** the README contains a "Prerequisites" (or equivalent) section
  - **When** the Evaluator reads it
  - **Then** the only required software listed is Docker and Docker Compose
  - **And** it explicitly states that no JDK, Node.js, npm, or PostgreSQL installation is needed on the host

- [ ] **AC-016.4: CSV download date documented**
  - **Given** the README references the sample CSV data
  - **When** the Evaluator reads the relevant section
  - **Then** the document states that the CSV was downloaded on 2026-07-27
  - **And** this date is presented as a fixed fact, not a dynamic value

- [ ] **AC-016.5: Decisions documented with alternatives considered**
  - **Given** the README contains a "Decisions" (or equivalent) section
  - **When** the Evaluator reads it
  - **Then** each major technical decision is documented with:
    - The choice made
    - At least one alternative that was considered
    - The reasoning for the choice over the alternative(s)
  - **And** the following decisions are covered:
    - Backend language: Clojure (vs Java, Python, Go, PHP)
    - Frontend framework: Angular 22 (vs React, ClojureScript, Vanilla JS)
    - Database: PostgreSQL (vs SQLite, MongoDB, MySQL)
    - Validation strategy: Malli + Zod parallel (vs NestJS gateway, single-layer validation)
    - CSV processing: core.async (vs distributed step functions, thread pools)
    - Product search: PostgreSQL tsvector (vs Elasticsearch, application-level filtering)
    - Duplicate SKU strategy: upsert for catalog, reject for in-file (vs reject-all, overwrite-all)
    - Delete behavior: hard delete with FK protection (vs soft delete, cascade)
    - Cart identity: signed cookie (vs JWT, database session, localStorage)
    - Checkout concurrency: SELECT FOR UPDATE (vs optimistic locking, queue-based)

- [ ] **AC-016.6: Approach section describes architecture**
  - **Given** the README contains an "Approach" or "Architecture" section
  - **When** the Evaluator reads it
  - **Then** it provides an overview of how the components interact: frontend (Angular) -> nginx proxy -> backend (Clojure/Ring) -> PostgreSQL
  - **And** it describes the key architectural patterns used (container-presentational, domain-driven handlers, etc.)
  - **And** it is concise enough to read in under 5 minutes

- [ ] **AC-016.7: Testing strategy summarized**
  - **Given** the README contains a "Testing" (or equivalent) section
  - **When** the Evaluator reads it
  - **Then** it summarizes the testing approach: TDD, test pyramid levels (unit, integration, smoke)
  - **And** it mentions the security testing approach (no stack traces, SQL, or paths leaked in errors)
  - **And** it references the `docs/architecture/testing-strategy.md` for full details

- [ ] **AC-016.8: Known limitations documented**
  - **Given** the README contains a "Known Limitations" or "What Would Change With More Time" section
  - **When** the Evaluator reads it
  - **Then** it lists at least 3 known limitations or areas for improvement
  - **And** the items are honest assessments, not aspirational features

- [ ] **AC-016.9: No AI-generated comments in source code**
  - **Given** the challenge constraint prohibits AI-generated comments in code
  - **When** the README references this constraint
  - **Then** it acknowledges the constraint and confirms compliance
  - **And** the actual source code files contain no AI-attribution comments (verified separately)

- [ ] **AC-016.10: Instructions work on first attempt**
  - **Given** a machine with Docker and Docker Compose installed
  - **When** the Evaluator follows the README instructions exactly as written
  - **Then** the application starts and is accessible at the documented URL
  - **And** no undocumented manual steps, environment variables, or configuration are required

## 5. Definition of Done

- [ ] All ACs pass with automated test evidence (where applicable)
- [ ] README content reflects the actual implementation, not planned implementation
- [ ] All 10 decisions documented with alternatives
- [ ] Run instructions verified on a clean environment
- [ ] No regressions in existing test suite
- [ ] Code reviewed
- [ ] INDEX.md updated

## 6. Deliverables

### Files to Create

| File Path | Contents |
| --------- | -------- |
| `README.md` | Main documentation file at repository root; contains: run instructions, prerequisites, CSV download date, decisions with alternatives, architecture overview, testing strategy summary, known limitations, AI-comment compliance note |

### Files to Modify

| File Path | Change |
| --------- | ------ |
| N/A | This story creates the README and references existing docs/ files but does not modify them |

**Note:** This story references `docs/architecture/` files for detailed architecture documentation but does NOT modify them. The README provides a summary and links to the detailed documents.

## 7. Test Plan

| Test Name | AC | Assertion |
| --------- | -- | --------- |
| `readme-exists-at-root` | AC-016.1 | `README.md` file exists at the repository root |
| `readme-contains-docker-compose-command` | AC-016.2 | README contains the text `docker compose up --build` |
| `readme-contains-access-url` | AC-016.2 | README contains the text `http://localhost:8080` |
| `readme-prerequisites-mention-docker` | AC-016.3 | Prerequisites section lists Docker and Docker Compose |
| `readme-prerequisites-no-jdk` | AC-016.3 | Prerequisites section does NOT list JDK or Java as a requirement |
| `readme-prerequisites-no-node` | AC-016.3 | Prerequisites section does NOT list Node.js or npm as a requirement |
| `readme-csv-date-documented` | AC-016.4 | README contains the date `2026-07-27` in the context of CSV download |
| `readme-decision-backend-language` | AC-016.5 | Decisions section mentions Clojure and at least one alternative (Java, Python, Go, or PHP) |
| `readme-decision-frontend-framework` | AC-016.5 | Decisions section mentions Angular and at least one alternative (React, ClojureScript, or Vanilla) |
| `readme-decision-database` | AC-016.5 | Decisions section mentions PostgreSQL and at least one alternative (SQLite, MongoDB, or MySQL) |
| `readme-decision-validation` | AC-016.5 | Decisions section mentions Malli + Zod and at least one alternative |
| `readme-decision-csv-processing` | AC-016.5 | Decisions section mentions core.async and at least one alternative |
| `readme-decision-search` | AC-016.5 | Decisions section mentions tsvector and at least one alternative (Elasticsearch) |
| `readme-decision-duplicate-sku` | AC-016.5 | Decisions section documents the upsert/reject strategy with alternatives |
| `readme-decision-delete-behavior` | AC-016.5 | Decisions section documents hard delete with FK protection and at least one alternative |
| `readme-decision-cart-identity` | AC-016.5 | Decisions section documents signed cookie with alternatives |
| `readme-decision-checkout-concurrency` | AC-016.5 | Decisions section documents SELECT FOR UPDATE with alternatives |
| `readme-architecture-overview-exists` | AC-016.6 | README contains an architecture/approach section describing component interaction |
| `readme-testing-strategy-summary` | AC-016.7 | README contains a testing section mentioning TDD and test pyramid |
| `readme-testing-references-detailed-doc` | AC-016.7 | README links to `docs/architecture/testing-strategy.md` |
| `readme-known-limitations-exist` | AC-016.8 | README has a limitations section with at least 3 items |
| `readme-no-ai-comments-acknowledged` | AC-016.9 | README acknowledges the no-AI-comments constraint |
| `source-code-no-ai-comments` | AC-016.9 | `grep -r "AI-generated\|Generated by AI\|Co-authored-by.*AI\|GitHub Copilot\|ChatGPT\|Claude" src/ frontend/src/` finds no matches in source code |
| `readme-instructions-first-attempt` | AC-016.10 | Following README instructions on a clean Docker environment produces a working application |

## 8. Validation Rules

| Field | Required | Type | Constraint | Reject Examples |
| ----- | -------- | ---- | ---------- | --------------- |
| N/A | --- | --- | No user input in this story | --- |

## 9. Risks

| Severity | Risk | Mitigation |
| -------- | ---- | ---------- |
| MEDIUM | README must be written last to reflect actual implementation, not planned implementation | Schedule this story as the final deliverable; verify all claims against the actual codebase before marking complete |
| MEDIUM | Over-documenting leads to a README that is too long to read in a reasonable time | Focus on decisions + alternatives + run instructions; reference `docs/` for full architectural detail rather than duplicating it |
| LOW | Run instructions may fail on certain Docker versions or OS configurations | Document the minimum Docker and Docker Compose versions tested; keep instructions to the simplest possible form |
| LOW | Known limitations section may be perceived as weakness rather than self-awareness | Frame limitations as engineering trade-offs with clear reasoning for what was prioritized instead |

## 10. Out of Scope

- API documentation auto-generation (Swagger/OpenAPI)
- Architecture diagrams embedded in README (reference `docs/` instead)
- Contribution guidelines (CONTRIBUTING.md)
- License file (LICENSE)
- Changelog (CHANGELOG.md)
- Deployment guides for cloud platforms
- Performance benchmarks or load test results

## 11. Notes

- This story should be the LAST story implemented, because the README must accurately describe what was actually built, not what was planned. Writing the README before the implementation is complete risks documenting features that do not exist or documenting them incorrectly.
- The decisions section is the most critical part of the README for evaluation purposes. The challenge explicitly evaluates engineering judgment, and documented decisions with alternatives are the primary artifact through which judgment is assessed.
- The README should NOT duplicate the full content of `docs/architecture/*.md` files. It should provide a concise summary and link to the detailed documents for readers who want more depth.
- The CSV download date (2026-07-27) is a fixed fact per the project brief. If the CSV file is re-downloaded in the future and the data differs, this date provides the reference point for any discrepancies.

## 12. Related Documents

- [EP06 --- Containerization & Documentation](../epics/EP06-containerization-docs.md) --- parent epic
- [Project Brief](../project-brief.md) --- challenge requirements and evaluation criteria
- [Tech Stack](../architecture/tech-stack.md) --- technology choices with rationale
- [Data Model](../architecture/data-model.md) --- database schema decisions
- [API Contract](../architecture/api-contract.md) --- endpoint design decisions
- [Testing Strategy](../architecture/testing-strategy.md) --- testing approach referenced from README
- [Domain Glossary](../domain-glossary.md) --- terminology used in documentation
- [US-015 --- Docker Compose Multi-Stage Setup](./US-015-docker-compose-setup.md) --- run instructions depend on Docker setup being complete

## 13. Handoff Files

TBD --- populated during Plan phase.

## 14. Change Log

| Date | Change | Reason |
| ---- | ------ | ------ |
| 2026-07-27 | Initial creation | Refine phase |
