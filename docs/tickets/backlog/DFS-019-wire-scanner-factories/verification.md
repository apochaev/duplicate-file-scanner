# DFS-019: Wire ScannerFactories — verification

## Summary

Verified at HEAD `1c6ba85` (the commit the deterministic gate passed on; no later code commit). Diff reviewed: `git diff main...HEAD`.

- **DFS-019.AC-1** met: `scanUseCases` returns `new ScanUseCases(service, service, service, service)`; the record's compact constructor rejects nulls; the AC-1 test asserts all four non-null.
- **DFS-019.AC-2** met: one `ScanService` and one `InMemoryScanRepository` per call; the test starts a real scan, then reads COMPLETED from `status`, the one {a.txt, b.txt} set from `result`, and COMPLETED from `cancel`.
- **DFS-019.AC-3** met: each call builds a new repository, pipeline and service, and `ScannerFactories` has no fields; the test shows the second set's `status` returns `Failure(UNKNOWN_SCAN)` for the first set's ID.
- **DFS-019.AC-4** met: wired to `NioLocationResolver`, `NioFileTreeWalker`, `NioContentHasher` and `NioFileIdentity`. The disk test would fail if empty files or hard links were counted, because either would add a second set. The missing-path test gets `LOCATION_NOT_FOUND` back from the real resolver.
- **DFS-019.AC-5** met: `requireNonNull(executor)` runs before anything is built; the test uses try/fail/catch (a red-gate-safe form).
- **DFS-019.AC-6** met: the executor is passed to `ScanService` unwrapped; with a holding executor the scan is QUEUED with exactly one held task, then COMPLETED once that task runs.

Definition of done: all six items verified and ticked. Remaining issues are documentation only: two stale lines in `design.md` (see Design conformance). No invariant violated.

**Verdict: ready for the human gate.** No phase needs to be rerun.

## Per-criterion detail

| Criterion | Test | Verdict | Notes |
|---|---|---|---|
| AC-1 | `DFS-019.AC-1 The factory returns all four use cases` | met | Also guarded structurally by the record's `requireNonNull` checks. |
| AC-2 | `DFS-019.AC-2 The four ports share one scan state` | met | Uses all four ports on one ID through the public factory only. |
| AC-3 | `DFS-019.AC-3 Separate builds do not share state` | met | A behavioral test: it observes the outcome instead of checking instance identity. |
| AC-4 | `... The default adapters read the real disk`, `... A missing location is rejected by the real resolver` | met | The hard link comes from `Files.createLink` at test time, and the test is skipped via `assumeTrue(false, ...)` on `UnsupportedOperationException`/`FileSystemException` (CLAUDE.md fixtures rule). |
| AC-5 | `DFS-019.AC-5 A null executor is a caller bug` | met | |
| AC-6 | `DFS-019.AC-6 Scans run on the caller's executor` | met | No sleeps or timing; it is deterministic. |

## Test quality

- The tests check observable behavior through the public factory and the ports. None of them restates the wiring, for example by checking instance identity or using reflection on fields.
- AC-4's single assertion ("exactly one set, {a.txt, b.txt}") carries real weight. If empty files were not excluded, `empty/x.txt` and `empty/y.txt` would form a second set. If hard links were counted, `linked/l1.bin` and `linked/l2.bin` would form one too. `unique/c.txt` has the same size as the dup files, so this also proves that hashing tells same-size files apart.
- `names()` compares file names only, not relative paths. That is enough here because the fixture names are unique across folders. The test-plan explains why: the resolver may return real paths.
- Minor point: AC-3 creates only `a.txt`, so the first scan finds no duplicates. That is fine, because the criterion is about the ID, not about the result.

## Design conformance

- The implementation follows `design.md`'s Public API and implementation sketch exactly: null check first, one shared resolver, a fresh pipeline, repository and service, and the same service passed four times.
- Checklist dispositions hold. Item 2 (NPE for a null executor): AC-5. Item 5 (validation before construction): `requireNonNull` is the first statement. Item 6 (no fields): confirmed; the class has only a private constructor and the static method. Item 11 (executor passed through unwrapped, no executor created): confirmed in the code and by AC-6. Item 13 (testable without timing): confirmed. Item 14 (no `module-info` change): confirmed; `module-info.java` is not in the diff.
- `ArchitectureTest`: `allowEmptyShould(true)` was removed from the layer rule and the no-Spring rule. A search finds it only once, on the top-level `util` rule, with the comment "top-level util holds only package-info.java, so this rule matches no class yet". The class Javadoc was reworded. All three rules pass in the gate's green run.
- Documentation defects in `design.md` (non-blocking):
  1. Under "Open design notes", it says `ScanUseCases`'s compact constructor "is left without checks". This contradicts the same file's Summary and Public API sections, which tell the implementer to add `requireNonNull` per component, and the code does add them. That line describes the stub phase and is stale now.
  2. Under "Stubs", it still says "Commit: to be filled in after the design commit."

## Safety invariants (CLAUDE.md)

- Never delete the last copy / re-verify at execution time / preview is a pure query: not applicable, because this ticket adds no deletion path.
- Hard links are not duplicates: holds through the wired `NioFileIdentity`, and AC-4 pins it end to end on the real disk.
- No symlink or junction following: holds through the wired `NioFileTreeWalker` (unchanged). This ticket adds no test for it, and none is needed: the walker's own tests cover it.
- Empty files excluded: holds with `ScanConfig.defaults()`, and AC-4 pins it end to end.
- Spring-free core and layering: `domain.factory` depends on `application`, `infra.adapter` and `infra.port`, all of which the layer rule permits. `ArchitectureTest` is green with the stricter rules.
- Deferred rule (ticket decision 3): the factory takes no overrides for secondary ports. This is a deliberate, documented deferral of the Spring-friendliness rule, not a defect.

## Scope

The diff touches only `domain.factory` (`ScannerFactories`, `ScanUseCases`, `ScannerFactoriesTest`), `ArchitectureTest.java` and the story folder. That is within `touches`.
