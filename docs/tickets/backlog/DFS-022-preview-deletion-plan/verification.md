# DFS-022: Preview a deletion plan — verification

## Product owner review

### What you can do now

The scanner library can now turn a finished scan plus a set of rules ("keep C:/A, quarantine its copies in C:/B"), exceptions and per-set picks into a deletion plan. You can review that plan before anything is touched: which files would go, which copy each one is kept against, the totals, what stays behind in each quarantine folder, and the sets where your rules conflict. Nothing is deleted yet; there is no CLI command for it yet (that is DFS-021), and the server can now inject it as a Spring bean.

### Acceptance criteria

- **DFS-022.AC-1** met: each plan has a 64-character hex ID; the same request gives the same ID, a different plan gives a different one.
- **DFS-022.AC-2** met: preview works on paths that do not exist and changes no files.
- **DFS-022.AC-3** met: a rule quarantines the copies under B that have a copy under A; `C:/A` does not cover `C:/AB`.
- **DFS-022.AC-4** met: an exception (a file or a folder above it) keeps that file out of every rule.
- **DFS-022.AC-5** met: a pick chooses the keeper for its set and overrides rules and exceptions there.
- **DFS-022.AC-6** met: when rules collide (one rule's keeper is another rule's victim), that set is left alone and listed as a conflict; other sets still plan.
- **DFS-022.AC-7** met: a file targeted by two rules appears once, credited to the earlier rule.
- **DFS-022.AC-8** met: hard links of one file move together and count their size once; a file only partly inside the quarantine folder is left alone.
- **DFS-022.AC-9** met: the keeper is the qualifying copy whose path sorts first.
- **DFS-022.AC-10** met: operations are ordered by rule, then picks, then path; totals (files, bytes, sets) add up.
- **DFS-022.AC-11** met: each rule reports how many scanned files and bytes stay in its quarantine folder.
- **DFS-022.AC-12** met: an empty request gives an empty plan with a fixed ID; null inputs throw.
- **DFS-022.AC-13** met: invalid rules and picks are refused with the right error code for the first problem.
- **DFS-022.AC-14** met: available from `ScannerFactories.previewDeletionPlan()` and as a Spring bean an app can replace.

### Try it

Nothing is user-visible yet: no CLI command or server endpoint calls it (DFS-021 and the frontend lane will). To see it working:

```
./gradlew :file-scanner:test --tests "*PreviewDeletionPlan*"
./gradlew :scanner-spring:test --tests "*ScannerAutoConfigurationTest"
```

Look for 26 + 1 passing tests in `file-scanner` and 8 (2 new) in `scanner-spring`, all named `DFS-022.AC-n ...`.

### Decided without you

- A conflict lists every rule that had a would-be entry in that set, not only the two that collide.
- A rule never quarantines a copy that has any link under its own `keep` folder.
- For a pick on a hard-linked copy, the recorded keeper is that copy's first link by path, which may differ from the path you picked (same physical file).
- Left-behind counts are clamped at 0 (only matters for hand-built scans whose folder totals miss files).
- The plan ID covers only the operations and conflicts (not totals or left-behind), uses a length-prefixed encoding versioned `dfs-deletion-plan/1`, and uses the platform's path separator, so an ID is stable on one OS but not comparable across OSes.
- For a pick, the checks run in the order unknown set, path not in set, duplicate pick.
- The factory method and the Spring bean were written for real at design time instead of as stubs, so existing Spring contexts kept starting.

### Known limits

- No CLI command, server endpoint, execution, quarantine or undo yet (DFS-021, later tickets).
- No test pins the exact plan-ID encoding from `design.md`; I checked it by hand (empty plan and one-operation plan both match the documented encoding), but a future change to the encoding would not fail a test. DFS-033 (drift check) relies on it.
- "Every set keeps a copy" is proven by the scenario tests and by the code structure, not by a property test over random plans.
- The plan is computed per request with no storage; the caller keeps the plan it previewed (as decided).

### Evidence

- `file-scanner/src/test/java/us/pochaev/dfs/scanner/application/PreviewDeletionPlanServiceTest.java`: 26 tests, 0 failures (every scenario of AC-1 to AC-13, null cases as 4 tests).
- `file-scanner/src/test/java/us/pochaev/dfs/scanner/domain/factory/PreviewDeletionPlanFactoryTest.java`: 1 test (AC-14 factory).
- `scanner-spring/src/test/java/us/pochaev/dfs/scannerspring/ScannerAutoConfigurationTest.java`: 2 new tests (AC-14 bean and back-off), 8 total, 0 failures.
- `./gradlew build` re-run by the verifier on 395fd1b: green; `ArchitectureTest` and `ModuleExportsTest` green; `module-info.java` unchanged since refinement.
- Deterministic gate on 395fd1b: green 428 passed / 3 skipped, disabled, freeze, touches (23 files, all in touches), stubs, traceability (14 criteria, 24 scenarios), consistency.
- Verifier probe (scratch file outside the worktree, deleted): empty-plan ID `2595215d...bd9740` equals SHA-256 of `19:dfs-deletion-plan/11:01:0`; a one-operation plan's ID equals a hand-built encoding per `design.md`; a mixed hard-link set with two opposite rules keeps a copy.

### How it went

- Design: 1 attempt (opus, 28 turns), design gate approved.
- Tests: 1 attempt (sonnet, 16 turns), 29 tests red against the stubs.
- Implement: 1 attempt (sonnet, 15 turns), gate green on the first try.
- No defects, retries or escalations recorded in `plan.json`.

Ready for the user's approval.

## Summary

All 14 criteria met; ready for the human done gate.

- DFS-022.AC-1 met: 64-hex ID, stable for the same request, different for different entries; encoding matches design.md (verifier probe).
- DFS-022.AC-2 met: works on non-existent paths, `@TempDir` listing unchanged; service has no I/O.
- DFS-022.AC-3 met: exact operation list asserted; whole-component matching via `Path.startsWith`.
- DFS-022.AC-4 met: folder and file exceptions both tested.
- DFS-022.AC-5 met: pick overrides rule and exception; source is `FromPick`.
- DFS-022.AC-6 met: both conflict scenarios; conflicted set contributes nothing, other sets unaffected.
- DFS-022.AC-7 met: one entry, earliest rule (`putIfAbsent` in rule order).
- DFS-022.AC-8 met: links together, size once, partial copy left alone.
- DFS-022.AC-9 met: keeper is smallest first link among qualifying copies.
- DFS-022.AC-10 met: order and totals asserted with full equality.
- DFS-022.AC-11 met: 4 files / 250 bytes and the empty-folder case.
- DFS-022.AC-12 met: empty plan, stable ID, four NPE cases.
- DFS-022.AC-13 met: all four codes, first-problem order, keep-under-quarantine valid.
- DFS-022.AC-14 met: factory works; bean present once and backs off.

Test quality: strong (full-equality assertions on operation lists, conflicts, totals and left-behind). Gap: no test pins the canonical ID encoding (verified by hand). Invariants: never-last-copy holds structurally (rule keepers are never in the quarantined set, pick keepers never quarantined); preview is pure (no fields, no I/O); hard links handled per physical copy. Symlink, empty-file and execution-time re-verify invariants do not apply.

## Details

### Design conformance

- Every algorithm step in `design.md` (validate, picks, rules, conflict check, order, totals, left-behind, ID) matches `PreviewDeletionPlanService`. All 14 checklist dispositions hold: items 1, 3, 4, 6–9, 11, 12 are genuinely not applicable (no ID input, no state, no callbacks, no executor); item 2 (NPE vs `Failure`) and item 5 (validate before planning) are implemented as stated; item 10 (whole plan, conflicts reported) holds; item 14 (exports) holds, `module-info.java` unchanged.
- Record checks listed in design.md are all present (`List.copyOf`, non-null, non-negative, non-empty, ID pattern).
- `PreviewDeletionPlanService` has no fields (two private nested records only, used as locals).

### Safety invariants

- Never delete the last copy: in rule sets, the conflict check refuses the set whenever any candidate's keeper is itself a candidate, so every keeper survives; picked sets keep the picked copy and ignore rules. Validated as a whole per set, across rules, as CLAUDE.md requires.
- Preview is a pure query: no disk access in the service; AC-2 test plus code inspection.
- Hard links are not duplicates: planning is per `PhysicalCopy`; a copy is quarantined only when all links are under `quarantineIn`, counted once in bytes.
- Re-verify at execution time, symlinks, empty files: not applicable (no execution, no walking).

### Test quality notes

- Tests compare entire `operations`, `conflicts`, `leftBehind` and `totals` values, so wrong keepers, sources, order or extra entries fail.
- AC-1's "different ID" uses only one variant; the exact encoding is not pinned (see Known limits).
- The `Thread.sleep(10)` in `ScannerAutoConfigurationTest.await` predates this ticket (DFS-031, ddf443f); the DFS-022 tests use none.
