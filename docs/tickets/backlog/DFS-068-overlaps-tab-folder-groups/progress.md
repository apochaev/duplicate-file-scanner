# DFS-068 progress

## Design phase (architect)

- Wrote `design.md`; replaced `overlaps-tab.ts`, `.html`, `.css` with compilable stubs (groups list, expander, small-group button; Comparison kept real). `./gradlew :server:ngBuild` is green, no budget warning (Initial total 689.04 kB).
- Ran `:server:npmTest --continue`: failing are `overlaps-tab.spec.ts` (old tables) and three tests of `result-screen.spec.ts`; both declared under Expected breakage.
- Flagged for the gate: `result-screen.html` (bind `roots`, `density`) and `result-screen.spec.ts` are outside `touches`.
- Expected commit: `docs: design DFS-068 [DFS-068]` (design.md, progress.md, learnings.md, the three overlaps-tab stub files).

## Gate feedback (design, user, 2026-11-12)

"add result-screen.html and result-screen.spec.ts to touches" — done in the ticket's `touches` and Scope. Rework the design: bind `[roots]="rootPaths()" [density]="density()"` in `result-screen.html` (a real change, not a stub), list `result-screen.spec.ts`'s three Overlaps tests (`DFS-060.AC-1`, `DFS-050.AC-5`, `DFS-050.AC-6`) as the test-author's to rewrite, and update design.md's Summary and file list. Do not touch other files.

## Design rework (architect, 2026-11-12)

- Applied the gate feedback: bound `[roots]="rootPaths()" [density]="density()"` on `<app-overlaps-tab>` in `result-screen.html` (real change); `:server:ngBuild` green.
- Updated design.md: Summary, file list (added `result-screen.html`, `result-screen.spec.ts`), Expected breakage (both specs now in touches, test-author rewrites), checklist and open-notes wording. `result-screen.spec.ts` not edited.
- Expected commit: `docs: rework design DFS-068 [DFS-068]` (design.md, progress.md, result-screen.html).

## Test phase (test-author)

- Rewrote `overlaps-tab.spec.ts` (26 tests, AC-1..AC-9 plus the kept DFS-050 comparison tests), the three Overlaps tests of `result-screen.spec.ts` (`/groups` instead of `/summaries` and `/pairs`), and `result-test-data.ts` (group builders, `expectGroups`; summaries/pairs builders removed). Wrote `test-plan.md`.
- `./gradlew :server:npmTest`: compiles; 29 tests fail, all with `not implemented: DFS-068` (plus its "Uncaught Exception" echoes); no new test passes.
- `BrowserFlowTest` not touched (implementer rewrites the two flows and adds the layout flow; listed in the plan).
- Expected commit: `test: add DFS-068 tests [DFS-068]`.

## Implement phase (implementer)

- Implemented every stub in `overlaps-tab.ts` (groups loading with superseded-answer guard, small-group toggle, paging, text helpers, `isAppManaged`); template and CSS unchanged from the design. Rewrote BrowserFlowTest flows `resultScreenOpensOnSets` and `folderBrowserFiltersTheViews` to assert groups (fixture groups are under 1 MiB, so they click "Show N small groups"), added `groupLayoutIsVisible`. Docs: CLAUDE.md, ui.md, folder-groups.md.
- `./gradlew build` and `:server:e2eTest` green. Expected commit: `feat(server): overlaps tab shows folder groups [DFS-068]`.

## Gate feedback (done, user, 2026-11-12)

"tests: fix the "in all" test data so sharedBytes follows /groups' real rules, expect the files' own size one copy each; implement: groupSizeText uses reclaimableBytes × files / (copies − files)"

Rework: tests phase first (fix the "in all" Vitest data and expected value; do not touch src/main, the new expectation fails against the current implementation, which is intended), then implement (change `groupSizeText` in overlaps-tab.ts; design.md's "sum of sharedBytes" is superseded), then verify again.

## Tests rework (in all)

Test-author: the "in all" test now uses /groups-consistent data (25 files in 2 folders with 20 listed; 3 MB + 2 MB in two folders; 1 MB + 2 MB with three copies per file) and expects the files own size (25.0 / 5.0 / 3.0 MB). Against the current implementation it fails with 50.0 / 10.0 / 9.0 MB; the other 298 tests pass. Expected commit: test(server): fix in-all expectation [DFS-068].
