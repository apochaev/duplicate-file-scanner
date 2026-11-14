# DFS-069: Mark folder groups as dealt with — test plan

## Criterion → scenario → test

| Criterion | Scenario | Test (`it` name, inside its `describe`) | Spec file | Stub it reaches | Level |
|---|---|---|---|---|---|
| `DFS-069.AC-1` | Ticking a group moves it to Dealt with without a request | `DFS-069.AC-1 shows a "Mark as dealt with" checkbox in every header, ticking moves the group to Dealt with without a request` | `src/app/results/overlaps-tab.spec.ts` (`OverlapsTab dealt with`) | `OverlapsTab.isDone`, `toggleDone` | component (TestBed) |
| `DFS-069.AC-2` | The Dealt with expander is collapsed, muted, and unticking returns the group | `DFS-069.AC-2 keeps the Dealt with expander collapsed with the group muted and ticked, and unticking returns it in the server order` | `overlaps-tab.spec.ts` | `isDone`, `doneTitle`, `toggleDone` | component (TestBed) |
| `DFS-069.AC-2` | No marks, no expander | `DFS-069.AC-2 shows no Dealt with expander when no group is marked` | `overlaps-tab.spec.ts` | `isDone` | component (TestBed) |
| `DFS-069.AC-3` | The header line counts groups left and dealt with | `DFS-069.AC-3 counts the groups left and dealt with` | `overlaps-tab.spec.ts` | `countsText` | component (TestBed) |
| `DFS-069.AC-3` | The counts follow the selected folder | `DFS-069.AC-3 counts only the marks under the selected folder` | `overlaps-tab.spec.ts` | `countsText` | component (TestBed) |
| `DFS-069.AC-3` | Groups left never goes below 0 | `DFS-069.AC-3 never shows a negative number of groups left` | `overlaps-tab.spec.ts` | `countsText` | component (TestBed) |
| `DFS-069.AC-3` | (helper) marks under a folder | `DFS-069.AC-3 counts every key for "All folders"`, `... counts a key when any of its folders equals or lies below the folder`, `... does not count a sibling that only shares ...` | `result-done-groups.spec.ts` | `countMarksUnder` | pure function |
| `DFS-069.AC-4` | Marks are stored per scan and survive a reload | `DFS-069.AC-4 stores a mark per scan under dfs.results.done.<scanId> and finds it again after a reload` | `overlaps-tab.spec.ts` | `loadMarks`, `toggleDone` | component (TestBed) |
| `DFS-069.AC-4` | The folder order in the answer does not matter | `DFS-069.AC-4 recognises a group whatever order the answer lists its folders in` | `overlaps-tab.spec.ts` | `isDone` | component (TestBed) |
| `DFS-069.AC-4` | (helper) key and storage format | `DFS-069.AC-4 builds the storage key ...`, `... builds the group key ...`, `... reads back what it wrote ...` | `result-done-groups.spec.ts` | `doneStorageKey`, `groupKey`, `readDoneMarks`, `writeDoneMarks` | pure function |
| `DFS-069.AC-5` | Marks survive paging, selection and the small-groups toggle | `DFS-069.AC-5 keeps marks across paging, a folder selection and the small-groups toggle, with the server figures` | `overlaps-tab.spec.ts` | `isDone`, `countsText` | component (TestBed) |
| `DFS-069.AC-6` | No storage | `DFS-069.AC-6 marks groups for the visit when the browser has no storage` | `overlaps-tab.spec.ts` | `loadMarks`, `toggleDone` | component (TestBed) |
| `DFS-069.AC-6` | Storage throws | `DFS-069.AC-6 loads and marks groups when the storage throws` | `overlaps-tab.spec.ts` | `loadMarks`, `toggleDone` | component (TestBed) |
| `DFS-069.AC-6` | Corrupt stored value | `DFS-069.AC-6 treats a stored value that is not a JSON array of strings as no marks` | `overlaps-tab.spec.ts` | `loadMarks`, `isDone` | component (TestBed) |
| `DFS-069.AC-6` | (helper) null, throwing, corrupt | `DFS-069.AC-6 reads no marks from ...` (4 tests), `... writes nothing to a null storage and ignores a setItem that throws` | `result-done-groups.spec.ts` | `readDoneMarks`, `writeDoneMarks` | pure function |
| `DFS-069.AC-7` | Every group on the page is marked | `DFS-069.AC-7 says all groups on the page are dealt with and keeps the paginator enabled` | `overlaps-tab.spec.ts` | `isDone`, `doneTitle` | component (TestBed) |

The grey look of a muted group (opacity) is not asserted (jsdom computes no styles); the test checks the `group-done` class and the ticked checkbox. Checklist item 11 (live check) is the verifier's.

## Fixtures and fakes

- HTTP: `provideHttpClient()`, `provideHttpClientTesting()`; `/groups` answered with `expectGroups(...).flush(...)`; `http.verify()` after each test; `http.expectNone` on `/groups` after a tick.
- Stub surfacing: `provideStubSurfacing()` and `await settle(fixture)` after each step.
- Fakes behind tokens: `DONE_STORAGE` with a `FakeStorage` (a Map, pre-filled by the test), `null`, or a storage whose calls throw.
- Data builders: `folderGroup`, `groupFolder`, `groupSet`, `groupPage` (existing, `result-test-data.ts`, unchanged); in the spec `pairOf`/`threeGroups` build G1 (/a/x, /b/x), G2 (/a/y, /b/y), G3 (/a/z, /c/z). Groups are told apart by the first folder line's tooltip.

## Notes for the implementer

- Group key `'/a/x\n/b/x'` (sorted, joined by a line feed); storage key `dfs.results.done.<scanId>`.
- Counts text `N groups left · M dealt with` with U+00B7; expander title `Dealt with on this page (K)`.
- Selectors used: `p.groups-counts`, `.open-groups > div.group`, `.done-list > div.group.group-done`, `.group-done-check`, `mat-expansion-panel.done-groups`, `p.all-done-note`.
- The AC-6 write test calls `writeDoneMarks` with a null and a throwing storage bare, then asserts afterwards.
- `overlaps-tab.spec.ts` keeps its 22 existing tests (the `OverlapsTab` describe); the new tests are the `OverlapsTab dealt with` describe.
