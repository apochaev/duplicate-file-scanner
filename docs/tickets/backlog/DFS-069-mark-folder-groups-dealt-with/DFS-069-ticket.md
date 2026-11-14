---
id: DFS-069
title: Mark folder groups as dealt with
status: in-review
type: feature
scope: []
archetype: component
gates: [done]
priority: medium
lane: frontend
depends_on: [DFS-068]
touches:
  - server/frontend/src/app/results/overlaps-tab.ts
  - server/frontend/src/app/results/overlaps-tab.html
  - server/frontend/src/app/results/overlaps-tab.css
  - server/frontend/src/app/results/overlaps-tab.spec.ts
  - server/frontend/src/app/results/result-done-groups.ts
  - server/frontend/src/app/results/result-done-groups.spec.ts
  - server/frontend/src/app/results/result-test-data.ts
  - CLAUDE.md
  - docs/design/ui.md
blocked_reason:
created: 2026-11-13
updated: 2026-11-13
completed:
---

# DFS-069: Mark folder groups as dealt with

## Story

As a user working through the Overlaps tab's folder groups
I want to tick a group as dealt with and have it move out of the way
So that the list shrinks as I progress and I always see which groups are still open

## Context

DFS-068 lists folder groups from `/groups` (paged 10/25/50) in `overlaps-tab.ts`. A long list gives no way to remember which groups were already handled. This ticket is client-side only: no API, no OpenAPI change, nothing is deleted, so no deletion invariant of `CLAUDE.md` applies.

Design:

- Each group header (`div.group-header`) gets a checkbox "Mark as dealt with". One click; it does not open the group's sets panel.
- A ticked group leaves the main list and appears in a "Dealt with on this page (K)" expander below the paginated list, collapsed by default, its groups muted (dimmed, checkbox ticked). Unticking returns the group to the main list.
- Above the list: "N groups left · M dealt with".
- **Identity.** A group has no server ID. Its key is its folders' exact paths, sorted and joined, so the order the API lists folders in does not matter.
- **Storage.** `localStorage` behind an injection token with the same shape as `DENSITY_STORAGE` (`getItem`/`setItem`, `null` when the browser offers none), in `result-done-groups.ts`. Key `dfs.results.done.<scanId>` holds a JSON array of group keys. Every read and write is in try/catch; a null, throwing or corrupt storage leaves the tab working, and the marks then last only for the visit.
- **Counts (settled with the user).** Paginator, `totalItems` and `hiddenSmall` stay as the server reports them. M is the number of stored marks of this scan whose group lies under the selected folder (any folder of the key equal to or below it; every mark when "All folders"). N is `totalItems` minus M, never below 0. Both are approximations: a mark made on a small group that is hidden again by "Hide small groups" is still subtracted. No "reclaimable still open" figure is shown, because only the loaded page can be summed.
- **Page effects.** Marked groups are hidden client-side after a page loads, so a page can show fewer groups than its size. K (the "Dealt with on this page" count) covers only the loaded page. A page whose groups are all marked shows a note and keeps the paginator.
- Marks survive paging, selection changes and the small-groups toggle; they are per scan and per browser.
- Not included: an inline "show dealt with in the list" toggle, a server-side `done` filter on `/groups`, sharing marks between browsers.

## Acceptance criteria

The full ID of each criterion is `DFS-069.AC-<n>`.

- **AC-1** Every group header shows a checkbox labelled "Mark as dealt with"; ticking it removes the group from the main list, adds it to the "Dealt with" expander and sends no request to the server.
- **AC-2** The expander is titled "Dealt with on this page (K)", is collapsed by default, is absent when K is 0, and lists its groups muted with the checkbox ticked; unticking one returns it to the main list in the server's order.
- **AC-3** Above the list the tab shows "N groups left · M dealt with", with N and M computed as in Context (M from the stored marks under the selected folder, N = `totalItems` − M, at least 0).
- **AC-4** A mark is stored under `dfs.results.done.<scanId>` as the group's key (its folders' paths, sorted and joined), so the same group is dealt with after a reload, when the folders come in another order, and not under another scan's ID.
- **AC-5** Marks survive paging, a folder selection change and the small-groups toggle; `totalItems`, the paginator and `hiddenSmall` stay as the server reports them.
- **AC-6** With a null storage, a storage that throws, or a stored value that is not a JSON array of strings, the tab still loads and marks groups; the marks then last only until the tab is left.
- **AC-7** When every group of the loaded page is marked, the main list shows "All groups on this page are dealt with." and the paginator stays usable.

## Scenarios

```gherkin
Feature: Mark folder groups as dealt with

  Background:
    Given a COMPLETED scan "s1" whose /groups answers 3 groups: G1 (folders /a/x and /b/x), G2 (/a/y and /b/y), G3 (/a/z and /c/z), totalItems 3
    And the browser storage is empty

  @AC-1
  Scenario: Ticking a group moves it to Dealt with without a request
    Given the Overlaps tab shows G1, G2 and G3, each header with a checkbox "Mark as dealt with"
    When the user ticks the checkbox of G2
    Then the main list shows G1 and G3
    And an expander "Dealt with on this page (1)" exists and holds G2
    And no further request to /groups was made

  @AC-2
  Scenario: The Dealt with expander is collapsed, muted, and unticking returns the group
    Given G2 is marked
    Then the expander "Dealt with on this page (1)" is collapsed and G2 inside it is muted with a ticked checkbox
    When the user opens the expander and unticks G2
    Then the main list shows G1, G2 and G3 in that order
    And no expander is shown

  @AC-2
  Scenario: No marks, no expander
    Given no group is marked
    Then there is no "Dealt with" expander

  @AC-3
  Scenario: The header line counts groups left and dealt with
    Given G1 and G2 are marked
    Then the tab shows "1 groups left · 2 dealt with"

  @AC-3
  Scenario: The counts follow the selected folder
    Given G1 (/a/x, /b/x) and G3 (/a/z, /c/z) are marked and folder /b is selected
    And the answer for /b has totalItems 2
    Then the tab shows "1 groups left · 1 dealt with"

  @AC-3
  Scenario: Groups left never goes below 0
    Given the storage holds 5 marks of scan "s1" and the answer has totalItems 3
    Then the tab shows "0 groups left · 5 dealt with"

  @AC-4
  Scenario: Marks are stored per scan and survive a reload
    When the user ticks G1
    Then the storage key "dfs.results.done.s1" holds a JSON array with one key built from "/a/x" and "/b/x" sorted
    When the tab is created again over the same storage
    Then G1 is in the "Dealt with" expander
    When the tab is created for scan "s2" over the same storage
    Then G1 is in the main list

  @AC-4
  Scenario: The folder order in the answer does not matter
    Given G1 is marked
    When /groups answers G1 with its folders as /b/x then /a/x
    Then G1 is in the "Dealt with" expander

  @AC-5
  Scenario: Marks survive paging, selection and the small-groups toggle
    Given G1 is marked
    When the user goes to page 2 and back to page 1
    And selects folder /a and then "All folders"
    And clicks "Show 4 small groups" and then "Hide small groups"
    Then G1 is still in the "Dealt with" expander each time it is on the loaded page
    And totalItems, the paginator length and the "Show N small groups" count are the server's values

  @AC-6
  Scenario: No storage
    Given the storage token is null
    When the user ticks G1
    Then G1 is in the "Dealt with" expander
    And nothing was written anywhere

  @AC-6
  Scenario: Storage throws
    Given getItem and setItem both throw
    When the tab loads and the user ticks G1
    Then the groups load and G1 is in the "Dealt with" expander

  @AC-6
  Scenario: Corrupt stored value
    Given the storage holds "{not json" under "dfs.results.done.s1"
    Then the tab loads with all three groups in the main list and "3 groups left · 0 dealt with"
    And a value like {"a":1} or [1,2] is treated the same

  @AC-7
  Scenario: Every group on the page is marked
    Given G1, G2 and G3 are marked
    Then the main list shows "All groups on this page are dealt with."
    And the paginator is shown and enabled
    And the expander "Dealt with on this page (3)" holds all three
```

## Scope

**Touches:**
- `server/frontend/src/app/results/overlaps-tab.ts`, `.html`, `.css`, `.spec.ts`: checkbox, main list vs expander, header line, note.
- `server/frontend/src/app/results/result-done-groups.ts` (new) and its spec: the storage token (same shape as `DENSITY_STORAGE`), the group key, read and write of the marks.
- `server/frontend/src/app/results/result-test-data.ts`: groups used by the specs.
- `CLAUDE.md` (the Overlaps sentence) and `docs/design/ui.md`: describe the checkbox, the expander and the storage.

**Out of scope:**
- Server-side marks, a `done` filter on `/groups`, sharing marks between browsers or devices.
- An inline "show dealt with in the list" toggle.
- Any API or OpenAPI change; the Playwright browser flow (unit tests cover this).
- A "reclaimable still open" figure.

## Definition of done

- [x] Every scenario above is an automated test and passes (Vitest, `:server:npmTest`)
- [x] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [x] No source files changed outside the paths listed under Touches (the ticket folder's own files excepted)
- [x] `server/frontend/openapi.json` is unchanged
- [x] `CLAUDE.md` and `docs/design/ui.md` describe the checkbox, the "Dealt with on this page" expander, the counts and the storage key

## Open questions

None.
