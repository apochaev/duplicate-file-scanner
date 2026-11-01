# DFS-033: Check a deletion plan for drift since the scan — verification

## Product owner review

### What you can do now

Once you have previewed a deletion plan, you can ask the scanner whether any of its files changed, moved or disappeared since the scan. The check covers both the files to be quarantined and the keepers. It reads only each file's size and modification time: no content, no writes, no following of links. It lists each file that drifted and says how. This is the library side only. The server endpoint and the UI warning are not built yet (frontend lane).

### Acceptance criteria

- **AC-1** met: an unchanged plan comes back clean, with the plan's id and the right count of distinct files checked (4).
- **AC-2** met: a deleted candidate and a deleted keeper are each reported as MISSING, with their role and set.
- **AC-3** met: a size change, a time-only change and a change to both are told apart. The last one counts as a single size change.
- **AC-4** met: a file replaced by a symlink, a folder or (on Windows) a junction is NOT_A_REGULAR_FILE. The symlink test shows the link is not followed.
- **AC-5** met: one unreadable file is reported with a reason, and the other three files are still checked.
- **AC-6** met: drifts come in plan order. A keeper shared by two operations is checked, counted and reported once (3 checked, not 4).
- **AC-7** met: a plan from another scan, or one with an unknown set, is refused with PLAN_NOT_FROM_SCAN before any disk read. An empty plan gives 0 checked. Null inputs throw.
- **AC-8** met: names, sizes and modification times on disk are identical before and after a check.
- **AC-9** met: `ScannerFactories.checkPlanDrift()` works, and the Spring autoconfiguration provides one bean that an app can replace with its own.

### Try it

Nothing is user-visible yet: there is no CLI command or server endpoint for it. To see it work:

```
./gradlew :file-scanner:test --tests "*CheckPlanDriftTest"
./gradlew :scanner-spring:test --tests "*ScannerAutoConfigurationTest"
```

Look for 14 passing tests in `CheckPlanDriftTest` with none skipped on Windows (the symlink and junction tests both ran), and 12 in `ScannerAutoConfigurationTest`.

### Decided without you

- `Role` is nested as `PathDrift.Role`, because `Role` alone is too generic for the package. `DriftKind` is a top-level enum.
- The whole plan is validated against the scan, operation by operation, before the first read. The failure messages are fixed: `operation <i>: no set with digest <d> in the scan` and `operation <i>: <path> is not a copy of set <d> in the scan`.
- A path counts as a regular file only if it is regular, not "other" (junction or reparse point) and not a symlink. Anything else is NOT_A_REGULAR_FILE.
- Only an I/O error while reading one path becomes an UNREADABLE drift. Any other exception is treated as a bug and stops the check.
- A path's recorded size and time come from the matching `FileEntry` in the operation's set. Paths are compared exactly as stored, with nothing resolved or normalized.
- Tests build "a scan of T" by hand from real files' attributes instead of running a scan.

### Known limits

- No content check: an edit that keeps the same size and restores the original modification time is not caught. Execution's byte compare (CLAUDE.md decision 4) and DFS-034 cover that.
- It runs synchronously, with no progress and no cancel (decision 5). That is fine for thousands of paths but untested at large scale.
- No server endpoint and no CLI exposure (out of scope; frontend lane).
- On non-Windows platforms the junction test skips by design, and the symlink test skips where symlinks need privileges.

### Evidence

- `file-scanner/src/test/java/us/pochaev/dfs/scanner/infra/adapter/CheckPlanDriftTest.java`: 14 tests, 14 passed, 0 skipped. I re-ran it on this machine (Windows).
- `scanner-spring/src/test/java/us/pochaev/dfs/scannerspring/ScannerAutoConfigurationTest.java`: 12 tests, all passed. 2 of them are new for AC-9.
- Deterministic gate on commit b42d1f0: green (472 passed, 3 skipped repo-wide), plus disabled, freeze, touches (18 files, all inside touches), stubs, traceability (9 criteria, 11 scenarios) and consistency. The only change since b42d1f0 is the orchestrator's `plan.json`.
- `module-info.java` and `fixtures/` are unchanged, and no added code uses `Thread.sleep`.

### How it went

- design (opus): 1 attempt, passed.
- tests (sonnet): 1 attempt. Red with 16 failing story tests, no collateral failures.
- implement (sonnet): 1 attempt, green on the first try.
- No defects, retries or escalations recorded in `plan.json`.

Ready for the user's approval.

## Summary

- **DFS-033.AC-1** met: clean plan, plan id, checked 4, no drifts (`unchangedPlanHasNoDrift`).
- **DFS-033.AC-2** met: candidate and keeper MISSING with role and digest, in order.
- **DFS-033.AC-3** met: SIZE_CHANGED, MODIFIED_CHANGED, and size+time as one SIZE_CHANGED. Times are set explicitly, no sleeping.
- **DFS-033.AC-4** met: symlink (no-follow proven by a same-size, same-mtime target), folder, and Windows junction (ran here). Three separate tests, so one skip does not hide the others.
- **DFS-033.AC-5** met: a fake reader throws AccessDeniedException. The test asserts UNREADABLE, a non-blank detail, checked 4 and the three other reads.
- **DFS-033.AC-6** met: the preview emits one operation per physical copy, so B and C are separate operations sharing keeper K. checked 3 proves the de-duplication. A separate test covers plan order.
- **DFS-033.AC-7** met: wrong paths and an unknown digest each give PLAN_NOT_FROM_SCAN, with a recording reader that saw zero calls. Empty plan gives checked 0. NPEs are tested with try/catch.
- **DFS-033.AC-8** met: a name, size and mtime snapshot of T before and after is identical.
- **DFS-033.AC-9** met: factory test, one-bean test (calls `check` on the bean) and back-off test (factory exercised first).

Test quality: the tests assert on behaviour through real files and the public port, not on internals. Minor deviation: the AC-9 Spring bean test uses a one-set plan (checked 2) instead of the first scenario's plan (checked 4). It still proves the bean works. The junction test cannot by itself tell "followed" from "not followed", because a followed junction is a directory. The symlink test carries that proof.

Design conformance: the code matches the design's Behavior table, its precedence, decisions (a) to (f) and every checklist disposition (items 1, 3, 4, 6, 7, 8, 9, 11 and 12 are marked not applicable with reasons). The service's only field is its port, and the adapter uses `NOFOLLOW_LINKS`.

Invariants: read-only (attributes only, no content, no writes, preview untouched). Links are not followed. Every link of a physical copy (hard links) is resolvable and checked individually. No deletion, so the last-copy invariant is not touched. The empty-file rule does not apply.

Verdict: ready for the human gate. No phase needs to be redone.
