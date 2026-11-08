# DFS-055 learnings

- **L-1 (architect): a ticket asked for a Spring bean of a secondary port.** Decision 3 and the Scope asked for the new `infra.port` lock to be "overridable as a `scanner-spring` bean like DFS-051's ports", but `infra.port` is not exported and `scanner-spring`'s `ArchitectureTest` forbids both the dependency and beans from internal packages; DFS-051's own secondary ports were never beans. Refinement could catch this with a check: a port meant to be overridable from an app must have its type in an exported package, or be overridden through the use-case bean. Deviation recorded in design.md.
- **L-2 (architect): adding an abstract method to a port breaks hand-written test fakes.** `JobStore` has two test implementations (`CountingStore`, `RecordingStore`); the new abstract `createUnless` stops them compiling until the test-author adds it. The use-case archetype could say: before adding a method to a port, grep the test tree for `implements <Port>` and list each fake in design.md for the test-author (or use a default method where the semantics allow).
- **L-3 (architect): a held OS resource and `@TempDir` on Windows.** Any existing test that ends with an unfinished execution now leaves a locked `execution.lock`, which blocks `@TempDir` deletion on Windows. A use-case checklist item on "resources held per request (locks, open files): released on every terminal path, and tests must end every request" would have surfaced this at refinement.
- **L-4 (architect): decision order vs. side-effect order.** Decision 5 fixed "plan checks, lock, claim" but did not place DFS-051's manifest creation; before the claim it leaves a stray manifest on every refusal, after it a manifest failure leaves a record. Refinement of an ordering decision should place every existing side effect of the operation, not only the new steps.
- **L-5 (test-author): a parameterized-looking ordering test passes against the stub.** "The plan is checked before the lock" is true before `launch` is reached, so a test using only a refused plan passes red. It must first make a sound plan reach the stubbed step (here, against a lock held elsewhere), then show the refused plan leaves the lock untouched. The guide's "reach the stub first" rule could name ordering tests explicitly.
- **L-6 (test-author): the existing DFS-051 execution tests fail red against the stub.** Changing `begin` to delegate to a stubbed `launch` makes every existing test that starts a run throw the stub exception. This is expected for this change, but the red gate counts them as regressions if it compares against a pre-design baseline; the archetype's "Expected breakage" section should say so when a stub sits on an existing happy path.

## Implement: test defect in the FAILED case of lockIsHeldUntilTheRunIsRecordedTerminal

In `PlanExecutionServiceTest.lockIsHeldUntilTheRunIsRecordedTerminal` (Ending.FAILED), `failOn = line instanceof ManifestLine.Moving` is set after the run is already blocked inside the comparator. `PlanExecutionService.execute` appends `Moving` before it compares, so that line is already written; nothing later fails and the run ends COMPLETED, not FAILED. The sibling `startAfterFailedSucceeds` sets `failOn` before the run starts and passes. Fix in the test: set `failOn` before `rig.start(...)`, or fail a line appended after the compare. Not changed by the implementer.

## Verify

- **L-7 (verifier): an OS file lock shared by shares needs care on POSIX.** POSIX `fcntl` locks are dropped when *any* channel of the file in the JVM is closed. `FileChannelExecutionLock` avoids opening a second channel when it already holds the lock, but its `OverlappingFileLockException` path (other in-JVM code holding the file) still opens and closes one. That is harmless today, since nothing else opens `execution.lock`. A use-case or adapter guide note could say: an adapter holding an OS file lock must be the only code in the JVM that opens that file, and must never open a second channel on it.
- **L-8 (verifier): a scenario's "separate threads" wording does not fit a refused start.** The AC-1 scenario asks for both executions on separate threads, but the second start is refused on the caller's thread and never gets one. Refinement could word race scenarios by observable outcome ("the second start is refused while the first is RUNNING") rather than by thread placement.

## Entries

### DFS-055.L-9: A failure injected after the run had already passed that step

- Status: applied
- Impact: local (`use-case` archetype)
- Target: `.claude/archetypes/use-case/test-guide.md`
- Change: a test that injects a failure mid-run arms it before the run reaches the injected step and names the line or step it fails on; where that depends on step order, `test-plan.md` states the order.
- Evidence: `plan.json` defect 1 (an implement-gate failure, routed to `test-author`): `lockIsHeldUntilTheRunIsRecordedTerminal` (FAILED) made the `Moving` line fail after the run was blocked in the comparator, but `Moving` is written before the compare (the "Implement: test defect" section above). It cost a failed gate, a tests rework and a reconcile; fixed in `2ccb7d3` by failing the `Moved` line.
- Recurrences: 1 (DFS-055)
- Recommendation: apply.
- Decision: applied 2026-11-07 by the user; commit `docs: apply DFS-055.L-9, DFS-055.L-10, DFS-055.L-11, DFS-055.L-12, DFS-055.L-14, DFS-034.L-1`

### DFS-055.L-10: An ordering test must first make a sound input reach the stub

- Status: applied
- Impact: local (`use-case` archetype)
- Target: `.claude/archetypes/use-case/test-guide.md`
- Change: for "X is checked before Y", the test first makes a sound input reach the stubbed step, then shows that a refused input leaves Y untouched; otherwise it passes against the stub.
- Evidence: L-5 above (test-author): "the plan is checked before the lock" held before `launch` was reached, so a test using only a refused plan passed red.
- Recurrences: 1 (DFS-055)
- Recommendation: apply.
- Decision: applied 2026-11-07 by the user; commit `docs: apply DFS-055.L-9, DFS-055.L-10, DFS-055.L-11, DFS-055.L-12, DFS-055.L-14, DFS-034.L-1`

### DFS-055.L-11: "Expected breakage" said none, but existing tests went red

- Status: applied
- Impact: local (`use-case` archetype)
- Target: `.claude/archetypes/use-case/design.md.tmpl`, section "Expected breakage"
- Change: when a stub sits on an existing happy path, every existing test that runs it fails with the stub's exception; the architect lists those tests there as expected red.
- Evidence: L-6 above; `design.md` said "none", yet 32 existing DFS-051 tests failed on the stub (the tests-phase note in `plan.json`).
- Recurrences: 1 (DFS-055)
- Recommendation: apply.
- Decision: applied 2026-11-07 by the user; commit `docs: apply DFS-055.L-9, DFS-055.L-10, DFS-055.L-11, DFS-055.L-12, DFS-055.L-14, DFS-034.L-1`

### DFS-055.L-12: Three design checks: port fakes, held resources, side-effect order

- Status: applied
- Impact: local (`use-case` archetype)
- Target: `.claude/archetypes/use-case/archetype.md`, the edge-case checklist
- Change: three items. (a) Before adding an abstract method to a port, search the test tree for `implements <Port>` and list each fake in `design.md`. (b) For a resource held per request (a lock, an open file), `design.md` lists its release on every way a run can end, and tests end every request, since Windows cannot delete a locked file in a `@TempDir`. (c) An ordering decision places every existing side effect of the operation, not only the new steps.
- Evidence: L-2, L-3 and L-4 above (architect): `CountingStore`/`RecordingStore` stopped compiling on `createUnless`; unfinished executions left a locked `execution.lock`; decision 5 did not place DFS-051's manifest creation.
- Recurrences: 1 (DFS-055)
- Recommendation: apply.
- Decision: applied 2026-11-07 by the user; commit `docs: apply DFS-055.L-9, DFS-055.L-10, DFS-055.L-11, DFS-055.L-12, DFS-055.L-14, DFS-034.L-1`

### DFS-055.L-13: Port exposure, POSIX file locks and race-scenario wording

- Status: parked
- Impact: shared (the `tickets` skill's `refine`, `use-case` archetype)
- Target: `.claude/skills/tickets/SKILL.md`, `refine`; `.claude/archetypes/use-case/impl-guide.md`
- Change: three items. (a) Refinement checks that a port meant to be replaceable from an app has its type in an exported package (or is replaced through the use-case bean). (b) An adapter holding an OS file lock is the only code in the JVM that opens that file and never opens a second channel on it. (c) Race scenarios are worded by observable outcome, not by which thread runs what.
- Evidence: L-1 (architect), L-7 and L-8 (verifier) above.
- Recurrences: 1 (DFS-055)
- Recommendation: park, since each happened once; apply item (a) if a ticket asks for a port bean again.
- Decision: parked 2026-11-07 by the user

### DFS-055.L-14: A test defect sent implement back before it had a commit

- Status: applied
- Impact: shared (`.claude/skills/story/SKILL.md`)
- Target: `.claude/skills/story/SKILL.md`, resume step 9 (the test-defect route) and step 5 (reconcile)
- Change: the route sets `implement` to `in-progress` "so step 5 reconciles it against its existing commit", but an implement attempt that failed its gate has no commit. Say so: the implementation stays uncommitted in the worktree and the test-author must not touch `src/main`; once the tests rework is committed and recorded, run the full implement battery against the working tree, then commit `implement`.
- Evidence: `plan.json` implement runs: attempt 1 `failed-gate` with no commit, then the reconcile note; the test fix was committed first (`2ccb7d3`), then the implementation (`e22db17`). Related: DFS-005.L-6 (applied) added the route but assumed a commit existed; this corrects it, so it is not counted as a recurrence.
- Recurrences: 1 (DFS-055)
- Recommendation: apply.
- Decision: applied 2026-11-07 by the user; commit `docs: apply DFS-055.L-9, DFS-055.L-10, DFS-055.L-11, DFS-055.L-12, DFS-055.L-14, DFS-034.L-1`
