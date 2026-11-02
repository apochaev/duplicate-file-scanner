---
id: DFS-044
title: Fit the frontend bundle budget and use the system font
status: done
type: build
scope: [build]
archetype: none
priority: medium
lane: frontend
depends_on: [DFS-041]
touches:
  - server/frontend/angular.json
  - server/frontend/src/styles.css
blocked_reason:
created: 2026-11-01
updated: 2026-11-01
completed: 2026-11-01
---

# DFS-044: Fit the frontend bundle budget and use the system font

## Story

As the user of the local DFS server
I want the frontend build to pass without a size warning and the page to use my system's font
So that a warning in the build means something again, and the page looks finished

## Context

DFS-041's known limits (its `verification.md`). The production build's initial bundle is 560 kB, over the 500 kB warning budget in `server/frontend/angular.json` (`budgets`, type `initial`, `maximumWarning: 500kB`, `maximumError: 1MB`), which the Angular CLI's default assumes a page sent over the internet. This page is served only from `127.0.0.1` (U10), so its size costs nothing on the network; Angular Material accounts for most of it. Every build now prints the warning.

The Material theme (`@angular/material/prebuilt-themes/azure-blue.css`) sets its fifteen typography tokens (`--mat-sys-body-large-font` through `--mat-sys-title-small-font`) to `Roboto`, a web font the app never loads (it loads nothing from the internet), so Material's text falls back to the browser's serif. `src/styles.css` already sets `body` to `system-ui, sans-serif`, which Material's components override through those tokens. CCW-020 decision 2 put this fix in a product ticket, ahead of the Angular component archetype.

## Decisions

1. **The budget becomes 1 MB warning, 2 MB error** (2026-11-01). Double the current values: the bundle has room to grow by about 80% before a warning, so the warning flags a real jump (a large new dependency) rather than every Material component. The `anyComponentStyle` budget (4 kB / 8 kB) stays.
2. **The fifteen Material font tokens are set to `system-ui, sans-serif` in `src/styles.css`**, on `html`, after the prebuilt theme (which `angular.json` lists first). No web font is added: the app stays offline.
3. **`archetype: none`, checked by the build and by hand.** Neither change has behavior a Vitest test can observe (jsdom does not compute fonts from stylesheets); AC-1 is read from the build output, AC-2 from the computed style in a browser.

## Acceptance criteria

The full ID of each criterion is `DFS-044.AC-<n>`.

- **AC-1** `./gradlew :server:ngBuild` (and so `./gradlew build`) prints no budget warning; `angular.json`'s `initial` budget reads `maximumWarning: 1MB` and `maximumError: 2MB`.
- **AC-2** On the served page, the computed `font-family` of a Material component's text (the Start button's label, the Settings panel title, the folder field's label) starts with `system-ui`, and `--mat-sys-body-medium-font` reads `system-ui, sans-serif`.
- **AC-3** The existing Vitest tests still pass.

## Scenarios

How each scenario is checked: AC-1 mechanically (the build output searched for "budget"), AC-2 by hand in a browser on the built jar (computed styles read through the page's own DOM, not from a screenshot), AC-3 by the build.

```gherkin
Feature: Frontend build budget and font

  @AC-1
  Scenario: The build passes without a budget warning
    When ./gradlew :server:ngBuild runs
    Then its output contains no line mentioning "budget"
    And angular.json's initial budget is 1MB warning and 2MB error

  @AC-2
  Scenario: Material text uses the system font
    Given the built server is running and the page is open with a session
    When the computed font-family of the Start button, the Settings panel title and the folder field's label is read
    Then each starts with "system-ui"
    And the value of --mat-sys-body-medium-font on html is "system-ui, sans-serif"

  @AC-3
  Scenario: The unit tests still pass
    When ./gradlew :server:npmTest runs
    Then every test passes
```

## Scope

**Touches:**
- `server/frontend/angular.json`: the `initial` budget (decision 1).
- `server/frontend/src/styles.css`: the font tokens (decision 2).

**Out of scope:**
- Shrinking the bundle (lazy loading, dropping Material modules).
- Loading a web font.
- The Vitest JUnit reporter in `angular.json` (CCW-020 decision 1).

## Definition of done

- [x] Each scenario checked as the note above the scenarios says, recorded in `verification.md` as mechanical or by hand
- [x] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [x] No files changed outside the paths listed under Touches
- [x] `verification.md` opens with the `## Product owner review` section

## Open questions

None.
