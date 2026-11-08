---
id: DFS-059
title: Compact read-only results screen
status: draft
type: feature
scope: []
archetype:
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
# gates: [done]        # pipeline tickets: decided in refine, required from ready on
# autorun: false       # pipeline tickets: true lets Claude start /story run itself
priority: high
lane: frontend
depends_on: []
touches: []
blocked_reason:
created: 2026-11-07
updated: 2026-11-07
completed:
---

# DFS-059: Compact read-only results screen

## Story

As a user looking at a finished scan
I want the results screen to show sizes as bars and numbers in columns, with short paths, instead of sentences and full paths
So that I can see at a glance where my duplicate space is and which copies sit where, without reading long lines of text

## Context

The product owner tried the result screen (DFS-049 Sets, DFS-050 Overlaps) on 2026-11-07: it works, but it is "a lot of text for relatively small space". Today every set is a Material card listing full absolute paths, and every table cell is a phrase such as "1204 files · 3.1 GB".

A prototype was made in Claude Design from a brief (similar apps: Gemini 2, dupeGuru and Czkawka for grouped duplicate lists; DaisyDisk and WizTree for size shown visually; Carbon and Material compact density for dense tables). Screenshots, the reference for this ticket:

- `docs/design/mockups/results-screen-prototype-2026-11-07.png`: the whole results screen.
- `docs/design/mockups/path-display-rule-2026-11-07.png`: how a path is shortened.

This ticket takes the **read-only** part of the prototype: what you see, not what you select. From the prototype:

- **Header:** the scan's root, when it was scanned and how long it took; headline numbers (reclaimable, duplicate sets, duplicate files, scanned bytes and files) over one stacked bar.
- **Folder list** on the left: folders sorted by duplicate space, each with a size bar and a "Dupes" column, expandable as today.
- **Sets as one compact grouped table** instead of cards: a header row per set (file name, copies, size each, reclaimable with a small bar), then one row per physical copy (path, modified date, copy-path button). Hard links are a "2 links" badge on their copy's row, not extra rows; a copy under the selected folder carries an "in selected" badge. "All sets" / "Copies under <folder>" as a filter toggle.
- **Path display rule:** paths relative to the scan root (the root is shown once, in the header); a path too long for its column loses folders from the middle, keeping the first folder under the root and the file name; folders in grey, the file name in normal ink, monospace 12 px; the full absolute path on hover and in the copy button.
- **Compact / Comfortable** density switch.

Left for later tickets: everything about building a plan (checkboxes, Keep/Quarantine labels, auto-select buttons, folder "move dupes"/"keep" tags, the "In current plan" number and bar segment, the sticky footer, "Review plan", "Quarantine folder", "New scan"), which needs DFS-054; and the Folder pairs view, not yet seen in the prototype.

Existing API (DFS-047, DFS-048), unchanged so far: `FolderNode` (path, name, files, bytes, directFiles, directBytes, hasChildren) has **no duplicate bytes**, so the folder list's bar and "Dupes" column need either a new field or the overlaps API's `FolderSummaryView` (bytesWithCopyElsewhere, internalBytes). `SetPage` gives `totalItems` and `totalReclaimableBytes`; there is no count of duplicate files and no "last copies kept" or "unique" total for the header's bar. `DuplicateSetView` gives digest, size, reclaimableBytes and copies with their links.

## Acceptance criteria

The full ID of each criterion is `DFS-059.AC-<n>`.

- **AC-1** <to be written in refinement>

## Scenarios

```gherkin
Feature: Compact results screen

  @AC-1
  Scenario: <to be written in refinement>
    Given <concrete state>
    When <action>
    Then <observable outcome>
```

## Scope

**Touches:**
- `server/frontend/src/app/results`: <to be decided>
- `CLAUDE.md`: the result screen's description in the `server` entry
- `docs/design/ui.md`: the result screen's design

**Out of scope:**
- Building a deletion plan on the screen (checkboxes, Keep/Quarantine, auto-select, footer, Review plan): a later ticket on DFS-054.
- The Folder pairs view's redesign (a later ticket, once its board is designed).
- Quarantine folder and New scan buttons.

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit tests and the Vitest tests)
- [ ] No files changed outside the paths listed under Touches
- [ ] Started by hand once on Windows on a real scan; compared side by side with the prototype screenshot

## Open questions

1. **Folder duplicate bytes:** add duplicate bytes to `FolderNode` (a server change in `us.pochaev.dfs.server.result`, `updateOpenApi`), or read them per folder from the overlaps API, or drop the "Dupes" column for now?
2. **Header totals:** which headline numbers and bar segments to show with today's API (reclaimable and sets exist; duplicate files, last copies kept and unique do not), and whether to add a scan-summary endpoint.
3. **Overlaps tab:** keep it as it is, restyle its tables to the same compact rules, or move it behind Sets (Sets first)?
4. **Folder list order:** by duplicate space (the prototype) or by name; does sorting by duplicates need the server to sort children?
5. **Density switch:** remembered between visits (local storage) or reset on every load?
6. **Paging:** keep the paginator (25/50/100) under the table, or load more on scroll?
7. **Archetype and gates:** `component`, or `endpoint` if question 1 adds a field; `[done]` or `[design, done]`.
8. **Live check:** whether DFS-053's browser flows need updating (`:server:e2eTest`), since the Sets view's markup changes.
