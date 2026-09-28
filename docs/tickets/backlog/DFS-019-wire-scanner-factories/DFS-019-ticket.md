---
id: DFS-019
title: Wire ScannerFactories
status: draft
type: feature
scope: [file-scanner]
archetype:
priority: medium
depends_on: [DFS-018]
touches: [us.pochaev.dfs.scanner.domain.factory]
blocked_reason:
created: 2026-09-27
updated: 2026-09-27
completed:
---

# DFS-019: Wire ScannerFactories

## Story

As a caller of the scanner library (the CLI's Spring configuration, later a server)
I want static factories that hand me the scan use cases as port interfaces
So that I can use the scanner without depending on its `application` or `infra` classes, and swap a secondary port for tests or a server

## Context

Split on 2026-09-27 from the original DFS-005. CLAUDE.md design decision 5: plain static factories in `domain.factory` instantiate application and adapter classes directly (compile-time checked, works on the classpath and the module path); no `ServiceLoader`. Spring-friendliness rules: factories return port interfaces and accept optional overrides for secondary ports. `ScannerFactories` exists today as an empty class. `domain.factory` is the one domain package allowed to depend on `application` and `infra.adapter`.

The scanner ArchUnit rules use `allowEmptyShould(true)` so they pass while layers are empty; now that `application` and `infra` have classes, CLAUDE.md says to consider removing it so a mistyped package pattern fails loudly.

## Acceptance criteria

<!-- Filled during refinement. -->

The full ID of each criterion is `DFS-019.AC-<n>`.

## Scenarios

```gherkin
Feature: Wiring the scanner

  Scenario: The factories return the use cases as port interfaces
    Given no overrides
    When the factories are asked for the scan use cases
    Then each is returned as its port interface, backed by the default adapters
```

## Scope

**Touches:**
- `us.pochaev.dfs.scanner.domain.factory`: `ScannerFactories`

**Out of scope:**
- Any CLI or Spring wiring in the `cli` subproject
- The `scanner-spring` autoconfigure module (CLAUDE.md decision 6, later)

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] No files changed outside the paths listed under Touches
- [ ] `allowEmptyShould(true)` in the scanner ArchUnit rules is reconsidered now that `application` and `infra` have classes

## Open questions

- Archetype.
- Scenarios are a first draft; review and complete them.
- Factory API: one factory method per use case, or one method returning a scanner facade? Which secondary ports can be overridden, and how (an overrides record, a builder)?
- Reconsidering `allowEmptyShould(true)` edits `ArchitectureTest` in the test tree: add its path to `touches` once decided.
- If the factories need a `module-info` change, list `file-scanner/src/main/java/module-info.java` in `touches` explicitly (CCW-003).
