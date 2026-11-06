# DFS-050 progress

## design (architect, 2026-11-05)

- Wrote `design.md` and the stubs. The new `OverlapsTab` container is in `server/frontend/src/app/results/overlaps-tab.{ts,html,css}`. Format helpers were added to `results/result-format.ts`. `results/folder-tree.*` gets a "Pin for comparison" button and the `folderPinned` output. `results/result-screen.*` gets `pinnedFolder`, `pinFolder` and `unpin`, and the tabs are now Overlaps then Sets.
- Every new method throws `new Error('not implemented: DFS-050')`.
- `./gradlew :server:ngBuild` is green with no budget warning. Initial total 697.22 kB → 701.30 kB; lazy `result-screen` chunk 205.81 kB → 258.65 kB.
- `./gradlew :server:npmTest`: 10 tests in `result-screen.spec.ts` fail, all with `not implemented: DFS-050`. The rest are green. Expected breakage: none.
- Expected commit: `feat(server): design the overlaps tab stubs [DFS-050]` (made by the orchestrator).

## Test phase (attempt 1)

Test-author wrote overlaps-tab.spec.ts (13 tests), one test each in folder-tree.spec.ts and three in result-screen.spec.ts (AC-5, AC-6, AC-8), builders in result-test-data.ts, and updated DFS-049 tests in result-screen.spec.ts (answer Overlaps requests, select Sets tab). 17 new tests plus 10 updated ones are red with `not implemented: DFS-050`. Expected commit: `test(server): add DFS-050 overlaps tab tests [DFS-050]`.

## Implement (attempt 1)

Implemented the six `result-format.ts` helpers, `OverlapsTab` (selectionChanged, loadSummaries, loadPairs, compare, paging, unpin, with request counters), `FolderTree.pin`, `ResultScreen.pinFolder`/`unpin` (and `pinnedFolder.set(null)` in `loadScan`); removed the stub notes; updated CLAUDE.md and docs/design/ui.md. Not green: 4 tests fail on test defects (single-backslash path literals in folder-tree.spec.ts and result-screen.spec.ts), reported to the orchestrator. Commit expected: `feat(server): add the Overlaps tab to the result screen [DFS-050]`.

Feedback at the done gate (2026-11-06, the user): docs only. In `docs/design/ui.md`, mark M-1.5 (DFS-050) and M-1 done in the Milestones table, and put the "As built (DFS-050, M-1.5)" bullet on its own line instead of the end of the DFS-049 bullet's line (verification.md, Known limits). Change no code or test.

## Tests: defect 1 fix

Doubled single-backslash Windows path literals (`D:\Backup`, `C:\Photos`, `.jpg`) in folder-tree.spec.ts (line 166) and result-screen.spec.ts (lines 361, 372, 378, 393-406). No assertion or test name changed. `./gradlew :server:npmTest`: 99 of 99 pass. No commit made.

Implement (attempt 1): ui.md Milestones marks M-1 and DFS-050 done; the DFS-050 "As built" bullet is on its own line.
