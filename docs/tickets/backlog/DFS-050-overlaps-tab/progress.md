# DFS-050 progress

## design (architect, 2026-11-05)

- Wrote `design.md` and the stubs. The new `OverlapsTab` container is in `server/frontend/src/app/results/overlaps-tab.{ts,html,css}`. Format helpers were added to `results/result-format.ts`. `results/folder-tree.*` gets a "Pin for comparison" button and the `folderPinned` output. `results/result-screen.*` gets `pinnedFolder`, `pinFolder` and `unpin`, and the tabs are now Overlaps then Sets.
- Every new method throws `new Error('not implemented: DFS-050')`.
- `./gradlew :server:ngBuild` is green with no budget warning. Initial total 697.22 kB → 701.30 kB; lazy `result-screen` chunk 205.81 kB → 258.65 kB.
- `./gradlew :server:npmTest`: 10 tests in `result-screen.spec.ts` fail, all with `not implemented: DFS-050`. The rest are green. Expected breakage: none.
- Expected commit: `feat(server): design the overlaps tab stubs [DFS-050]` (made by the orchestrator).

## Test phase (attempt 1)

Test-author wrote overlaps-tab.spec.ts (13 tests), one test each in folder-tree.spec.ts and three in result-screen.spec.ts (AC-5, AC-6, AC-8), builders in result-test-data.ts, and updated DFS-049 tests in result-screen.spec.ts (answer Overlaps requests, select Sets tab). 17 new tests plus 10 updated ones are red with `not implemented: DFS-050`. Expected commit: `test(server): add DFS-050 overlaps tab tests [DFS-050]`.
