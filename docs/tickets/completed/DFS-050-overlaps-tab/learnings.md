# DFS-050 learnings

- **L-1 (design): the archetype contradicts the architect's definition about the spec skeleton.** `component/archetype.md`'s "Stubs" bullet says the architect copies `skeletons/feature.spec.ts.tmpl`, but the architect's definition says "Do not write tests". I wrote no spec. The bullet could say that the test-author copies the spec skeleton.
- **L-2 (design): decision 4 misreads how `mat-tab` renders.** It says Angular Material creates every tab's content on open. Without `preserveContent`, only the active tab's content is attached, and an inactive tab's component is not created until the tab is selected. DFS-049 AC-6 still holds, because `ResultScreen` requests sets itself. The real consequence is different: returning to Overlaps re-creates `OverlapsTab`, which reloads its pages. This is recorded in `design.md`'s open notes for the gate.
- **L-3 (design): `npx ng test` from `server/frontend` fails outside Gradle**, with `TypeError: Class extends value undefined is not a constructor or null` (it runs on the global Node, not the Node 24 the Gradle plugin downloads). Use `./gradlew :server:npmTest` for Vitest output.
- **L-4 (design): Vitest groups identical failures.** It prints the error once under a list of failed tests (here 10 tests, one `Error: not implemented: DFS-050`). This is enough to see that every failure is the stub's, but a per-test check needs the JSON reporter.

## Implement (attempt 1)

- Deviation from design (decision 4 note): `mat-tab` content projected as `<app-overlaps-tab>` is created once and never destroyed (only detached from the DOM), so the design's "Sets selected re-creates the tab" is wrong and an unselected Overlaps tab would keep requesting on every folder change, which result-screen.spec.ts forbids. Fixed with `<ng-template matTabContent>` around the Overlaps tab (created when shown, destroyed when left).
- Material tabs attach the newly selected tab's projected content only after a real timer in jsdom (about 500 ms), longer than `settle()` waits; `animationDuration="0ms"` on the `mat-tab-group` makes the switch immediate. Applies to any later spec that selects a tab through `MatTabGroupHarness` with `settle()`.
- Test defects: unescaped backslashes in string literals ('D:\Backup' is 'D:Backup'): folder-tree.spec.ts line 166; result-screen.spec.ts lines 361, 393-406 and the `Pinned: ` expectation in the DFS-050.AC-5 pin test.

## Verify (attempt 1)

- **L-5 (verify): a docs done-item with several parts was only half done.** The implementer updated U3 but not the Milestones table (M-1.5 and M-1 "done"), which Scope and the last done-item both name. It also appended the new "As built" bullet to the end of the previous bullet's line. The implementer could check each named part of a docs done-item against `git diff` before handing off, and check that a new Markdown bullet starts its own line.
- **L-6 (verify): turning off tab animation for the tests changed the product.** `animationDuration="0ms"` on the `mat-tab-group` was the implementer's only option once the specs were frozen. The component test-guide could tell the test-author that a spec selecting a tab through `MatTabGroupHarness` with `settle()` should provide `{provide: MAT_TABS_CONFIG, useValue: {animationDuration: '0ms'}}` in the spec itself, so the product keeps its animation.
- **L-7 (verify): content projected straight into a `mat-tab` is created once and only detached.** A container tab that loads on input changes keeps loading while hidden. Use `<ng-template matTabContent>` when a hidden tab must not request. This is worth a line in the component archetype's impl-guide; design L-2 assumed the opposite.
- **L-8 (verify): reading the live DOM with `textContent` on a whole section joins adjacent spans** (for example "585.9 KBall have a copy in the other"). The page renders them on separate lines (flex column). For the by-hand record, read per element (`.side-text`, `.side-coverage`) or take a `Page.captureScreenshot` to judge the layout.

## Entries

### DFS-050.L-1: The component archetype tells the architect to copy the spec skeleton

- Status: applied
- Impact: local
- Target: `.claude/archetypes/component/archetype.md`, the "Stubs" bullet
- Change: drop `skeletons/feature.spec.ts.tmpl` from the architect's copy list in the "Stubs" bullet; the test-author already starts from it (`test-guide.md`, "Start from `skeletons/feature.spec.ts.tmpl`").
- Evidence: DFS-050 note L-1: the "Stubs" bullet says "Copy `skeletons/feature.ts.tmpl`, `skeletons/feature.html.tmpl` and `skeletons/feature.spec.ts.tmpl`", while `.claude/agents/architect.md` says "Do not write tests"; the architect wrote no spec.
- Recurrences: 1 (DFS-050)
- Recommendation: apply; an explicit contradiction, fixed in one line.
- Decision: applied 2026-11-06 by the user; commit `docs: apply DFS-050.L-1, L-2, L-3, L-4 and L-5`

### DFS-050.L-2: Spec literals holding Windows paths lost their backslashes and failed the green gate

- Status: applied
- Impact: local
- Target: `.claude/archetypes/component/test-guide.md`
- Change: add a note: a string literal holding a Windows path doubles its backslashes (`'D:\\Backup'`) or uses `String.raw`, since `'D:\Backup'` is `'D:Backup'` in JavaScript; the red run cannot catch it, because such a test fails on the stub either way.
- Evidence: DFS-050 `plan.json` defect 1 (implement `green` failed on four story tests in `folder-tree.spec.ts` and `result-screen.spec.ts`, routed to test-author, fixed in 50c4e2b); implement attempt 1 recorded as `failed-gate`.
- Recurrences: 1 (DFS-050)
- Recommendation: apply; it cost a failed implement attempt and a reopened tests phase.
- Decision: applied 2026-11-06 by the user; commit `docs: apply DFS-050.L-1, L-2, L-3, L-4 and L-5`

### DFS-050.L-3: Turning tab animation off for the tests changed the product

- Status: applied
- Impact: local
- Target: `.claude/archetypes/component/test-guide.md`
- Change: add a note: a spec that selects a tab through `MatTabGroupHarness` and `settle()` provides `{provide: MAT_TABS_CONFIG, useValue: {animationDuration: '0ms'}}` itself, since in jsdom the new tab's content attaches only after about 500 ms of real timers; the product keeps its animation.
- Evidence: DFS-050 note L-6 and the implement note: with the specs frozen, the implementer set `animationDuration="0ms"` on the `mat-tab-group` in `result-screen.html`, which also removed the animation in the app (verification.md, "Decided without you").
- Recurrences: 1 (DFS-050)
- Recommendation: apply.
- Decision: applied 2026-11-06 by the user; commit `docs: apply DFS-050.L-1, L-2, L-3, L-4 and L-5`

### DFS-050.L-4: Content projected into a `mat-tab` is created once and only detached

- Status: applied
- Impact: local
- Target: `.claude/archetypes/component/impl-guide.md` (and the archetype's design checklist)
- Change: add a line: content placed directly inside a `mat-tab` is created once and only detached when another tab is selected, so a tab whose component loads on input changes keeps requesting while hidden; wrap it in `<ng-template matTabContent>` to create it when shown and destroy it when left.
- Evidence: DFS-050 notes L-2 and L-7 and the implement deviation: design decision 4 assumed the tab is re-created on each switch; the implementer added `matTabContent` in `result-screen.html` (8182892) because `result-screen.spec.ts` forbids requests from a hidden Overlaps tab.
- Recurrences: 1 (DFS-050)
- Recommendation: apply; the design phase got it wrong and the implementer had to deviate.
- Decision: applied 2026-11-06 by the user; commit `docs: apply DFS-050.L-1, L-2, L-3, L-4 and L-5`

### DFS-050.L-5: A multi-part docs done-item was half done, and a new bullet landed on the previous line

- Status: applied
- Impact: shared (an agent prompt)
- Target: `.claude/agents/implementer.md`, its checklist
- Change: add a pre-handoff check: compare each part a docs done-item names (sections, tables, rows) against `git diff`, and confirm a new Markdown bullet starts its own line.
- Evidence: DFS-050 note L-5: `docs/design/ui.md` got the U3 text but not the Milestones table (M-1.5 and M-1 done), and the "As built (DFS-050, M-1.5)" bullet was appended to the DFS-049 bullet's line; the user sent implement back at the done gate (fix 68559a6, verify rerun 1a0be2d).
- Recurrences: 1 (DFS-050)
- Recommendation: apply; it cost a send-back plus an implement and a verify rerun.
- Decision: applied 2026-11-06 by the user; commit `docs: apply DFS-050.L-1, L-2, L-3, L-4 and L-5`

### DFS-050.L-6: Reading a whole section's `textContent` in the by-hand check joins adjacent spans

- Status: parked
- Impact: local
- Target: `.claude/archetypes/component/test-guide.md`, "The by-hand check without a person (DFS-049)"
- Change: add: read the live DOM per element (for example `.side-text`, `.side-coverage`) or take a `Page.captureScreenshot` to judge layout, since `textContent` on a whole section joins spans ("585.9 KBall have a copy in the other").
- Evidence: DFS-050 note L-8 (verify).
- Recurrences: 1 (DFS-050)
- Recommendation: park; minor, and it caused no failure.
- Decision: parked 2026-11-06 by the user
