# DFS-069: Mark folder groups as dealt with — design

## Summary

Client-side only: no API call, no OpenAPI change, nothing deleted. The Overlaps tab (`overlaps-tab.ts`, lazy with the result screen) gets a "Mark as dealt with" checkbox in every `div.group-header`; a ticked group leaves `div.open-groups` and appears, muted, in a collapsed `mat-expansion-panel.done-groups` below the paginator.

Acceptance criteria and the stub each reaches:
- AC-1 (checkbox, move to Dealt with, no request): `OverlapsTab.toggleDone`, `isDone`, then `groupKey` and `writeDoneMarks`.
- AC-2 (expander "Dealt with on this page (K)", collapsed, absent at K 0, untick returns the group in server order): `doneTitle`, `isDone` (the `openGroups`/`doneGroups` computeds filter the page in the server's order through it), `toggleDone`.
- AC-3 (counts line): `countsText`, then `countMarksUnder`.
- AC-4 (key `dfs.results.done.<scanId>`, folder order irrelevant, per scan): `groupKey`, `doneStorageKey`, `readDoneMarks`, `writeDoneMarks`, `loadMarks`.
- AC-5 (marks survive paging, selection, small-groups toggle; server figures untouched): `marks` lives in the component and is reset only when `scanId` changes; `isDone` and `countsText`; the paginator keeps `result.totalItems`.
- AC-6 (null, throwing or corrupt storage): `readDoneMarks`, `writeDoneMarks`; the in-memory `marks` signal holds the marks for the visit.
- AC-7 (all marked): `isDone` through `openGroups`; the template shows `p.all-done-note` and keeps the paginator.

Placement: `server/frontend/src/app/results/` only. New file `result-done-groups.ts` (token `DONE_STORAGE`, pure helpers); `overlaps-tab.ts`/`.html`/`.css` changed. The group card moved into an `ng-template #groupCard` rendered by `ngTemplateOutlet` in both lists. New Material module: `MatCheckboxModule` (lazy chunk only; initial total after the stubs 689.74 kB, no budget warning).

Checklist items marked not applicable: 5 (no new screen or route), 6 (a checkbox only), 8 (no stream), 12 (no style iteration). Item 11 (live check) is the verifier's.

Expected breakage (user approves this list): `overlaps-tab.spec.ts`, `result-screen.spec.ts` (both fail with the stub error), `app.routes.spec.ts`, `session-ended.spec.ts` (collateral timeouts, see below), `BrowserFlowTest` (e2e only).

Existing tests read: none.

## Components and files

| File | Kind | Inputs / outputs | Injects | Purpose |
|---|---|---|---|---|
| `results/result-done-groups.ts` (new) | helper + token | none | none (`DONE_STORAGE` token, default `browserStorage()` from `result-density.ts`) | `DoneStorage`, `DONE_STORAGE`, `doneStorageKey`, `groupKey`, `readDoneMarks`, `writeDoneMarks`, `countMarksUnder` |
| `results/overlaps-tab.ts` | component (changed) | unchanged | adds `DONE_STORAGE` | adds `marks` (a `linkedSignal` over `scanId`), `openGroups`, `doneGroups`, `loadMarks`, `isDone`, `toggleDone`, `countsText`, `doneTitle`, `ALL_DONE_NOTE` |
| `results/overlaps-tab.html` | template (changed) | | | counts line, `open-groups` list, paginator, `done-groups` expander, `#groupCard` template |
| `results/overlaps-tab.css` | style (changed) | | | `.group-done` (opacity 0.6), `.done-groups`, `.groups-counts`, `.all-done-note`, `.group-done-check` |
| `results/result-test-data.ts` | test data | | | test-author adds what its groups need (nothing stubbed here) |
| `CLAUDE.md`, `docs/design/ui.md` | docs | | | implementer: the Overlaps sentence, the checkbox, expander, counts line and storage key |

Fixed decisions the tests rely on:
- **Group key**: folder paths sorted in plain code-unit order, joined with `\n`; `['/b/x','/a/x']` gives `'/a/x\n/b/x'`. The stored value under `dfs.results.done.<scanId>` is `JSON.stringify` of an array of such keys.
- **Under a folder** (`countMarksUnder`): a key counts when any of its paths equals the folder or continues with `/` or `\` after it (trailing separators of the folder ignored, case-sensitive); null folder counts every key.
- **Texts**: `N groups left · M dealt with` (always "groups", even for 1; the dot is U+00B7), `Dealt with on this page (K)`, `All groups on this page are dealt with.`, checkbox label `Mark as dealt with`.
- **Selectors**: `p.groups-counts`, `div.open-groups > div.group`, `p.all-done-note`, `mat-expansion-panel.done-groups` (collapsed; its content is not lazy, so a muted `div.group.group-done` with a ticked `mat-checkbox.group-done-check` is in the DOM before it is opened), `div.done-list`. Main list has no `group-done`. The counts line and note show only when `totalItems > 0`.
- The expander is placed after the paginator; the paginator stays rendered when the main list is empty.

## Page states

- Not connected: not applicable: the tab sits inside the result screen, shown only when connected.
- Loading: unchanged (progress bar "Loading folder groups").
- Empty: unchanged ("No folders share files", no counts line, no expander).
- Loaded: counts line, unmarked groups, paginator, expander when K > 0.
- Everything on the page marked: `All groups on this page are dealt with.` in place of the main list; paginator enabled.
- Input refused by the form, server refusal, server busy: not applicable: no input and no new request.
- Load failed: unchanged (`p.groups-error`).
- Live updates: not applicable.

## API calls

None new. Marking sends no request (AC-1); `listGroups` and `getPair` are as DFS-068 left them. `server/frontend/openapi.json` unchanged.

## Browser APIs behind wrappers

`localStorage` behind `DONE_STORAGE` (`InjectionToken<DoneStorage | null>`, providedIn root, factory `browserStorage` from `result-density.ts`; the same shape as `DENSITY_STORAGE`). A spec provides a fake with `getItem`/`setItem`, `null`, or one whose calls throw.

## Material components

`MatCheckboxModule` (new, per component, harness `MatCheckboxHarness` from `@angular/material/checkbox/testing`), `MatExpansionModule` (already used; the new `mat-expansion-panel.done-groups`, harness `MatExpansionPanelHarness`). `NgTemplateOutlet` from `@angular/common`. `./gradlew :server:ngBuild` after the stubs: Initial total 689.74 kB, lazy `result-screen` 221.68 kB, no budget warning.

## Checklist disposition

1. The generated client: no new call; `src/app/api/` untouched.
2. Promise timing in tests: specs use `settle(fixture)` after every step (the existing `step()` helper).
3. Stubs reachable and surfaced: through template-evaluated methods (`isDone` in a computed read by the template, `countsText`, `doneTitle`, `toggleDone` from the checkbox's `(change)`), not from an RxJS callback or interceptor. The helper functions are reached directly by `result-done-groups.spec.ts`. The `marks` linkedSignal is read from `isDone`.
4. Browser APIs jsdom lacks: `localStorage` exists in jsdom, but the tests need to pre-fill, break and disable it, so it sits behind `DONE_STORAGE` (see above).
5. Where the screen sits: not applicable: inside the existing Overlaps tab, no route.
6. Input and validation: not applicable: a checkbox only.
7. Loading, empty and error states: see Page states; unchanged except the all-marked note.
8. Live updates: not applicable: no stream.
9. Material components and their harnesses: `MatCheckboxHarness` (label "Mark as dealt with"), `MatExpansionPanelHarness`.
10. Bundle budget and system font: initial total 689.74 kB, no warning; no font set.
11. The live check reads the DOM: done by the verifier on the built jar (tick a group, reload, check the stored key).
12. The dev server and its proxy: not applicable: no style iteration needed beyond `.group-done`.
13. Docs: `CLAUDE.md` Overlaps sentence and `docs/design/ui.md` U3, by the implementer (both in `touches`).

## Expected breakage

- `overlaps-tab.spec.ts`: 22 existing tests render groups, whose template now reaches `countsText`/`isDone` (all fail with `not implemented: DFS-069`).
- `result-screen.spec.ts`: one test selects the Overlaps tab with groups and reaches `countsText` (same stub error).
- `app.routes.spec.ts`: collateral only; passes alone and when `overlaps-tab.spec.ts` is excluded, but times out (5000 ms) in the full run after the stub failures leave the shared test environment in a bad state.
- `session-ended.spec.ts`: same collateral timeouts (3 tests) in the full run.
- `BrowserFlowTest`: its Overlaps flows (tab "Overlaps", "Show N small groups") render groups and would hit the stub; it runs only in `:server:e2eTest`, not in `check`. No selector it uses is replaced.

## Open design notes (as of the design phase)

- The ticket leaves the key separator open; fixed here as `\n` (cannot appear in a Windows path).
- The "Dealt with" list renders its content eagerly (not `matExpansionPanelContent`), so AC-2's "collapsed, and G2 inside it is muted with a ticked checkbox" is observable without opening it.
- The counts line and the all-done note show only when the page's `totalItems > 0`.
- The stub on `loadMarks` etc. is in the component; once implemented the component calls the `result-done-groups.ts` helpers.

## Stubs (as of the design phase)

- Files stubbed: `server/frontend/src/app/results/result-done-groups.ts` (`doneStorageKey`, `groupKey`, `readDoneMarks`, `writeDoneMarks`, `countMarksUnder`), `server/frontend/src/app/results/overlaps-tab.ts` (`loadMarks`, `isDone`, `toggleDone`, `countsText`, `doneTitle`); template and CSS are final.
- Each stub throws `new Error('not implemented: DFS-069')`. Which stub each criterion reaches: AC-1 `toggleDone`/`isDone`; AC-2 `isDone`/`doneTitle`; AC-3 `countsText`; AC-4 `groupKey`/`doneStorageKey`/`readDoneMarks`/`writeDoneMarks`/`loadMarks`; AC-5 `isDone`/`countsText`; AC-6 `readDoneMarks`/`writeDoneMarks`; AC-7 `isDone`.
- The template's structure and CSS classes are final; tests find elements by them and by Material harnesses.
- Compiled with `./gradlew :server:ngBuild`.
- Commit: filled in after the design commit.
