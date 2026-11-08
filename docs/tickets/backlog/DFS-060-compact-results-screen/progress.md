# DFS-060 progress

## design (architect, 2026-11-07)

- Wrote `design.md` and the compilable stubs (`./gradlew :server:ngBuild` green: initial 705.73 kB, lazy result-screen chunk 261.93 kB, no budget warning).
- Files: `server/frontend/src/app/results/` `result-density.ts` (new), `result-path.ts`, `result-format.ts`, `result-screen.ts`/`.html`/`.css`, `sets-tab.ts`/`.html`/`.css`, `folder-tree.ts`/`.html`/`.css`, `path-copy.ts`/`.html`.
- Ran `:server:npmTest` once: 47 failures in `app.routes.spec.ts`, `folder-tree.spec.ts`, `path-copy.spec.ts`, `result-screen.spec.ts` and `sets-tab.spec.ts`. These five spec files, plus `BrowserFlowTest` (e2e), are listed under Expected breakage.
- Expected commit: `feat(server): design compact results screen stubs [DFS-060]` (the orchestrator commits).

## tests (test-author, 2026-11-07)

- Wrote Vitest specs `result-density.spec.ts`, `result-format.spec.ts` (new) and rewrote/extended `result-path.spec.ts`, `folder-tree.spec.ts`, `path-copy.spec.ts`, `sets-tab.spec.ts`, `result-screen.spec.ts`; `app.routes.spec.ts` gets a `DENSITY_STORAGE` null provider. Updated `BrowserFlowTest` flows (AC-3, 4, 5, 7, 8) to the new markup; it compiles, e2e not run (needs the implementation).
- `:server:npmTest`: 118 failed, 82 passed (the old DFS-049/050 tests and untouched specs). Every DFS-060 test fails with `not implemented: DFS-060`; the only other failures are the two DFS-049.AC-1 tests in `app.routes.spec.ts` (timeout / TestBed reuse), the expected breakage in design.md.
- Expected commit: `test(server): add compact results screen tests [DFS-060]` (the orchestrator commits).

## tests, attempt 2 (test-author, 2026-11-07)

- Reverted `app.routes.spec.ts` (outside `touches`); its two DFS-049.AC-1 tests stay expected breakage.
- `result-density.spec.ts` "DFS-060.AC-9 ignores a missing storage and a write that throws" now calls `writeDensity` directly and asserts on the fake storage afterwards, so the stub's error propagates instead of becoming an AssertionError.
- Gates: `touches` PASS (28 files); `test-phase` PASS (106 story tests red on the stub, traceability 10 criteria / 14 scenarios).

## implement (implementer, 2026-11-07)

- Replaced every stub: `result-density.ts`, `result-path.ts` (`displayPath`, `fileName`), `result-format.ts`, `folder-tree.ts`, `path-copy.ts`, `sets-tab.ts`, `result-screen.ts` (`totals`, `filterBySelection`, `setFilter`, `chooseDensity`); removed the "Stubs (design phase)" class comments. Templates were already final.
- `CLAUDE.md` server entry and `docs/design/ui.md` U3 describe the compact screen (new DFS-060 bullet; Overlaps no longer "landing tab").
- `./gradlew build` green, no budget warning. `:server:e2eTest`: 7 of 8 pass; DFS-053.AC-6 fails (see learnings).
- Expected commit: `feat(server): compact read-only results screen [DFS-060]`.
- Miss: the design's expected-breakage list omitted DFS-053.AC-6 (`pinCompareAndUnpin`); the pin label and Comparison live in the Overlaps tab, so the flow now clicks `tab("Overlaps")` after pinning; all 8 flows pass.

## implement, second reopen (implementer, 2026-11-07)

- Folder tree: two-line rows (name across the row, then bar, size and duplicates in fixed 3.5rem columns at the right edge; the bar shrinks first), 24 px toggle, 12 px indent, "Pin for comparison" at the top right of the name line on hover or focus; one line and the pin in the row's flow below a 960 px window.
- Sets table: `table-layout: fixed` (Modified 6rem, Reclaimable 4.5rem, its header cell spanning Copy), paths at 0.75rem monospace in a flex line where the folders ellipsize before the file name, `PATH_BUDGET` 62 → 56 (every test green), compact Copy button 24 px high. Result layout stacks below 960 px. CLAUDE.md and ui.md say 56.
- Checked on the built jar with headless Chrome over CDP: at 1440 and 1000 px `.folder-name` is 124-220 px wide, none clipped; the tab body is 688 px with scrollWidth 688; every `.path-name` is inside its cell; Reclaimable is in view; rows are 28 px; no console messages.
- `./gradlew build` green; `:server:e2eTest` 8/8.
- Expected commit: `fix(server): fit the folder names and the sets table in the results screen [DFS-060]`.
