---
id: DFS-053
title: Exercise the scan and results flow in a browser
status: ready
type: feature
scope: [build]
archetype: none
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
# gates: [done]        # pipeline tickets: decided in refine, required from ready on
# autorun: false       # pipeline tickets: true lets Claude start /story run itself
priority: medium
lane: frontend
depends_on: [DFS-050]
touches: [server/build.gradle.kts, server/src/test/java/us/pochaev/dfs/server, docs/design/ui.md, CLAUDE.md]
blocked_reason:
created: 2026-11-02
updated: 2026-11-07
completed:
---

# DFS-053: Exercise the scan and results flow in a browser

## Story

As a user browsing scan results
I want the scan, folder tree, Sets and Overlaps flow checked in a real browser
So that integration failures are caught before a release

## Context

Filed as required by DFS-049 decision 8 after its ready confirmation. The first Playwright flow follows DFS-050; framework setup and browser downloads remained outside DFS-049. See docs/design/ui.md D6. It does not block duplicate groups or Copy folder path.

Decisions (refinement, 2026-11-07):

1. **Archetype `none`.** The work is build and tooling (a test dependency, a browser download, a Gradle task) that the `component` archetype excludes, and its shape is a set of choices no archetype template covers. It is implemented by hand, with no phase gates and no `autorun`.
2. **Playwright for Java** (`com.microsoft.playwright`) as a test dependency of `server`, not the Node package. Spring Boot's test support and `@TempDir` give the server lifecycle and the fixture without scripting. The browser (Chromium) is downloaded into a project-local folder under `server/.gradle` by setting `PLAYWRIGHT_BROWSERS_PATH` on the task; nothing is installed globally.
3. **A separate `e2eTest` Gradle task, not part of `check`.** The tests are tagged `e2e`; `test` excludes the tag and `e2eTest` runs only it. `./gradlew build` stays offline and fast. Because nothing then runs the flows by itself, the Definition of done of every later ticket that changes the scan or result flow runs `./gradlew :server:e2eTest` (this ticket records that rule in ui.md D6).
4. **Fixture generated in code**, in a `@TempDir` by one helper: a handful of small files with fixed bytes in two overlapping folders, plus a hard link created with `Files.createLink`, skipped with `Assumptions` when the platform or privileges do not allow it. Nothing platform-specific is committed (CLAUDE.md, test fixtures). Each test states the counts and sizes it expects next to the data.
5. **The real server in the test JVM**, started with the existing `TestServer.start()` (random port, browser opener off); the test reads its `Open http://127.0.0.1:<port>/?token=<token>` line and has the browser navigate to that link, so the token exchange and the `DFS_SESSION` cookie are exercised. The frontend comes from the classpath (`ngBuild`, `frontendResources`), as for `bootRun`. The e2e classes sit in the package `us.pochaev.dfs.server` because `TestServer` is package-private.
6. **The real clipboard.** The browser context is granted `clipboard-read` and `clipboard-write`; the test clicks "Copy folder path" and reads the text back with `navigator.clipboard.readText()`.

## Acceptance criteria

The full ID of each criterion is `DFS-053.AC-<n>`.

- **AC-1** Navigating a real Chromium to the server's `Open ...?token=` link ends on the scan screen showing "Connected", with the session cookie set by the redirect.
- **AC-2** Adding the fixture's root folder as a chip and starting the scan runs it to COMPLETED, and the scan's row offers "Open results".
- **AC-3** "Open results" opens the result screen at `#/scans/<id>` on the Overlaps tab, whose Folders table lists the fixture's two folders.
- **AC-4** Expanding "All folders" in the folder tree loads the fixture's subfolders, and selecting one folder makes the Overlaps summary and the Sets tab show that folder.
- **AC-5** The Sets tab lists the fixture's duplicate sets with their copies, and a hard link is marked under its copy and is not counted as an extra copy.
- **AC-6** "Pin for comparison" on one folder and selecting the other shows the Comparison block with both folders (`Pinned: <path>`), and "Unpin" removes the pin and the block.
- **AC-7** "Copy folder path" on a set's link puts exactly that folder's absolute path on the browser's clipboard, read back with `navigator.clipboard.readText()`.
- **AC-8** Reloading the page at `#/scans/<id>` shows the same result screen without logging in again.

## Scenarios

```gherkin
Feature: Scan and results flow in a real browser

  Background:
    Given a real server on a random port with the built frontend
    And a temp fixture folder "root" holding folders "keep" and "extra"
    And "keep" and "extra" share two files of identical content, "keep" holds one more file and "extra" holds one more file
    And "keep" holds a hard link to one of its own files
    And Chromium is open with clipboard-read and clipboard-write granted

  @AC-1
  Scenario: The token link connects the page
    When the browser opens the server's "Open ...?token=" link
    Then the address ends on the scan screen
    And the page shows "Connected"
    And the cookie "DFS_SESSION" is set

  @AC-2
  Scenario: A scan of the fixture completes
    Given the scan screen is open
    When the user adds the absolute path of "root" as a chip and presses Start
    Then the scan's row reaches COMPLETED
    And the row offers "Open results"

  @AC-3
  Scenario: The result screen opens on Overlaps
    Given the scan is COMPLETED
    When the user clicks "Open results"
    Then the address is "#/scans/<id>"
    And the Overlaps tab is selected
    And the Folders table lists "keep" and "extra"

  @AC-4
  Scenario: The folder tree filters the views
    Given the result screen is open
    When the user expands "All folders" and selects "extra"
    Then the tree lists "keep" and "extra" under the root
    And the Overlaps summary and the Sets tab show "extra"

  @AC-5
  Scenario: Sets show copies and a marked hard link
    Given the result screen is open
    When the user opens the Sets tab
    Then it lists a card for each of the two duplicate sets
    And each card lists its copies
    And the hard link is marked under its copy
    And the hard link adds nothing to its set's reclaimable size

  @AC-6
  Scenario: Pin one folder and compare it with another
    Given the result screen is open with "keep" in the tree
    When the user pins "keep" and then selects "extra"
    Then the page shows "Pinned: <path of keep>"
    And the Comparison block shows "keep" against "extra"

  @AC-6
  Scenario: Unpin removes the comparison
    Given "keep" is pinned and "extra" is selected
    When the user clicks "Unpin"
    Then "Pinned:" is no longer shown
    And the Comparison block is gone

  @AC-7
  Scenario: Copy folder path reaches the real clipboard
    Given the Sets tab shows a set with a copy in "extra"
    When the user clicks "Copy folder path" on that copy
    Then reading the browser clipboard returns the absolute path of "extra"

  @AC-8
  Scenario: A reload keeps the result screen
    Given the result screen is open at "#/scans/<id>"
    When the page is reloaded
    Then the same result screen opens on the Overlaps tab
    And no login is asked for

  @AC-5
  Scenario: Without hard links the rest still runs
    Given the platform or privileges do not allow creating a hard link
    When the fixture is built
    Then the hard-link check is skipped by an assumption
    And the other scenarios run on a fixture without the link
```

## Scope

**Touches:**
- `server/build.gradle.kts`: the Playwright test dependency, the `e2e` tag excluded from `test`, the `e2eTest` task (own browser folder via `PLAYWRIGHT_BROWSERS_PATH`, depends on the frontend resources), not wired into `check`.
- `server/src/test/java/us/pochaev/dfs/server`: the tagged browser test class and the fixture helper.
- `docs/design/ui.md`: D6 records the `e2eTest` task and the rule that tickets changing the flow run it.
- `CLAUDE.md`: `./gradlew :server:e2eTest` in the Commands block and one sentence in the server description.

**Out of scope:**
- Wiring `e2eTest` into `check` or into a CI service (decision 3).
- Deleting, quarantining or previewing a deletion plan; those screens do not exist yet.
- Tests of the scan API, security headers or the generated client; their own tests cover them.
- Browsers other than Chromium, and visual or screenshot comparison.
- Any change to production code; a defect a flow finds is filed as its own ticket.

## Definition of done

- [ ] Every scenario above is an automated test in `./gradlew :server:e2eTest` and passes (the hard-link scenario may skip on a platform without links)
- [ ] `./gradlew build` is green and does not run the browser tests or download a browser (includes the ArchUnit and module-export tests)
- [ ] `./gradlew :server:test` does not run the `e2e` tests; `:server:e2eTest` runs only them
- [ ] The Chromium download lands in a folder under `server/.gradle`, and nothing is installed globally
- [ ] No files changed outside the paths listed under Touches
- [ ] ui.md D6 and the CLAUDE.md Commands block name `e2eTest` and the rule that tickets changing the scan or result flow run it
- [ ] A product-owner review is written by the implementing session: what was run, the real output of `e2eTest`, and anything decided without the user

## Open questions

None.
