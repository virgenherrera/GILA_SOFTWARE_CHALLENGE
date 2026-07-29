# T-016 --- README & Decision Documentation

## Metadata

| Field | Value |
|-------|-------|
| Task ID | T-016 |
| Batch | 3 |
| Epic | EP06 --- Containerization & Docs |
| Story | [US-016](../../user-stories/US-016-readme-documentation.md) |
| Persona | Evaluator |
| Model Tier | standard |
| Priority | Must Have |
| Depends On | T-015 (Docker Compose finalization) |

## Objective

Write README.md documenting run instructions, prerequisites, architectural decisions with alternatives considered, architecture overview, testing strategy, and known limitations. This is the last task because it must reflect the final state of the entire application.

## Pre-conditions

- [ ] ALL other tasks (T-001 through T-015) are complete
- [ ] `docker compose up --build` starts the full application cleanly
- [ ] All features are implemented and tests pass
- [ ] No AI-generated comments exist in the codebase

## Context Bundle

| File | Lines | Why Needed |
|------|-------|------------|
| docs/architecture/tech-stack.md | Section 1 (Stack Overview), Section 12 (Alternatives Considered) | Technology choices and versions |
| docs/architecture/data-model.md | Section 1 (Schema Overview), Section 5 (Entity Relationship Diagram) | Database design for architecture overview |
| docs/architecture/api-contract.md | Section 3 (Products API), Section 4 (CSV Import API), Section 5 (Cart API), Section 6 (Checkout and Orders API) | API surface for architecture overview |
| docs/architecture/security-guidelines.md | Section 1 (Security Model Overview), Section 11 (Deferred Security Concerns) | Security approach for decisions section |
| docs/architecture/middleware-pipeline.md | Section 1 (Request Lifecycle Overview), Section 2 (Middleware Stack) | Request lifecycle and middleware ordering for architecture overview |
| docs/architecture/validation-pruning.md | Section 2 (Malli Schema Design --- Closed Maps), Section 6 (Validation Contract) | Validation strategy for decisions section |
| docs/architecture/error-handling.md | Section 1 (Design Principle), Section 2 (Exception → Error Code Mapping) | Error handling approach for architecture overview |
| docs/architecture/tdd-workflow.md | Section 1 (Red-Green-Refactor Cycle) | Testing strategy section |
| docs/architecture/api-docs-strategy.md | Section 1 (Approach), Section 6 (Benefits) | API documentation approach for architecture overview |
| docs/architecture/pnpm-config.md | Section 1 (Package Manager), Section 7 (Why pnpm) | Frontend tooling decisions section |
| docs/architecture/health-check-strategy.md | Decision: 503 + Stay Alive + HikariCP Reconnection | Health check behavior for architecture overview |
| docker-compose.yml | all | Actual run configuration to document |
| Dockerfile.backend | all | Build process for architecture overview |
| Dockerfile.frontend | all | Build process for architecture overview |
| docs/domain-glossary.md | Entities, Value Objects, Conventions | Domain terms for consistency |
| docs/user-stories/US-016-readme-documentation.md | all | Acceptance criteria for README documentation |
| docs/architecture/testing-strategy.md | Section 2 (Test Pyramid), Section 8 (Coverage Policy) | Test pyramid, documentation verification |

## Deliverables

### Files to Create

| File | Purpose |
|------|---------|
| README.md | Project documentation: run instructions, decisions, architecture, testing, limitations |

### Files to Modify

None.

## Quality Gates

| # | Gate | Command/Check | Type | Pass Criteria |
|---|------|---------------|------|---------------|
| 1 | README exists | `test -f README.md` | EXE | exit 0 |
| 2 | Run instructions work | Follow README to start app from scratch | MANUAL | App starts on first attempt |
| 3 | Prerequisites listed | README states Docker + Docker Compose only | REVIEW | No other prerequisites mentioned |
| 4 | Decision 1 documented | Backend: Clojure vs Java/Python/Go/PHP | REVIEW | Choice + alternatives + rationale |
| 5 | Decision 2 documented | Frontend: Angular 22 vs React/ClojureScript/Vanilla | REVIEW | Choice + alternatives + rationale |
| 6 | Decision 3 documented | Database: PostgreSQL vs SQLite/MongoDB/MySQL | REVIEW | Choice + alternatives + rationale |
| 7 | Decision 4 documented | Validation: Malli + Zod vs NestJS gateway | REVIEW | Choice + alternatives + rationale |
| 8 | Decision 5 documented | CSV processing: core.async vs distributed step functions | REVIEW | Choice + alternatives + rationale |
| 9 | Decision 6 documented | Search: PostgreSQL tsvector vs Elasticsearch | REVIEW | Choice + alternatives + rationale |
| 10 | Decision 7 documented | Duplicate SKU: upsert for catalog, reject for in-file | REVIEW | Choice + alternatives + rationale |
| 11 | Decision 8 documented | Delete: hard delete with FK protection | REVIEW | Choice + alternatives + rationale |
| 12 | Decision 9 documented | Cart identity: signed cookie | REVIEW | Choice + alternatives + rationale |
| 13 | Decision 10 documented | Checkout concurrency: SELECT FOR UPDATE | REVIEW | Choice + alternatives + rationale |
| 14 | Architecture overview | How components interact (backend, frontend, DB, Docker) | REVIEW | Clear system description |
| 15 | Testing strategy | TDD, test pyramid, security tests documented | REVIEW | Strategy summarized |
| 16 | Known limitations | What would change with more time | REVIEW | Honest assessment present |
| 17 | No AI comments | `rg "AI-generated|Generated by AI|Co-authored-by.*AI|GitHub Copilot|ChatGPT|Claude" --include="*.clj" --include="*.ts" src/ frontend/src/` | EXE | No AI attribution in code |
| 18 | Docker start works | `docker compose down -v && docker compose up --build -d` | EXE | App starts per README instructions |
| 19 | CSV download date documented | `rg "2026-07-27" README.md` | EXE | Date found in CSV download context |
| 20 | Configuration documented | README has a Configuration section listing every env var: name, required/optional, default, description | REVIEW | All vars from tech-stack.md §6 present |

## Boundaries

- NOT in scope: Auto-generated API documentation (Swagger/OpenAPI) --- already generated and served at `/api-docs/` by the backend itself (api-docs-strategy.md); duplicating it in the README would drift out of sync
- NOT in scope: Architecture diagrams in README (reference docs/ directory) --- the architecture docs already contain the authoritative Mermaid diagrams; the README references them rather than duplicating them (see Anti-patterns: "Copy-paste architecture docs verbatim")
- NOT in scope: Contribution guidelines --- this is a single-evaluator technical challenge submission, not an open-source project accepting external contributions
- NOT in scope: License file --- not requested by any acceptance criterion and irrelevant to a take-home evaluation submission
- NOT in scope: Changelog --- a single-delivery challenge has no release history to log; the git commit history already documents the change sequence

## Anti-patterns

| What | Why It Fails | Do Instead |
|------|-------------|------------|
| Document planned features as if implemented | Misleads evaluator, damages credibility | Only document what actually exists and works |
| Write a tutorial | Evaluator wants a reference, not a walkthrough | Write concise reference documentation |
| Skip "alternatives considered" for decisions | Evaluator wants to see reasoning, not just choices | Each decision MUST list alternatives and why they were not chosen |
| Copy-paste architecture docs verbatim | Redundant, shows no synthesis | Summarize and reference docs/ for details |
| Include AI-generated comments in code | Challenge requirement violation | Remove any AI attribution from source files |

## Rollback Guidance

```bash
git checkout -- README.md
```

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
- Pipeline: install -> build -> lint -> test:unit -> test:integration -> test:e2e
- Failing stage STOPS the pipeline

## Status Protocol

```
Status: [IN_PROGRESS | BLOCKED | DONE | FAILED]
Progress: X/Y items
Blocker: (if applicable)
```

## Progress Tracker

### Deliverables
- [ ] README.md created

### Quality Gates
- [ ] Gate 1: README.md exists
- [ ] Gate 2: Run instructions work on first attempt
- [ ] Gate 3: Prerequisites are Docker-only
- [ ] Gate 4: Decision 1 --- Backend language
- [ ] Gate 5: Decision 2 --- Frontend framework
- [ ] Gate 6: Decision 3 --- Database
- [ ] Gate 7: Decision 4 --- Validation strategy
- [ ] Gate 8: Decision 5 --- CSV processing
- [ ] Gate 9: Decision 6 --- Search implementation
- [ ] Gate 10: Decision 7 --- Duplicate SKU handling
- [ ] Gate 11: Decision 8 --- Delete strategy
- [ ] Gate 12: Decision 9 --- Cart identity
- [ ] Gate 13: Decision 10 --- Checkout concurrency
- [ ] Gate 14: Architecture overview present
- [ ] Gate 15: Testing strategy documented
- [ ] Gate 16: Known limitations listed
- [ ] Gate 17: No AI comments in code
- [ ] Gate 18: Docker start works per README
- [ ] Gate 19: CSV download date documented
- [ ] Gate 20: Configuration section documented
