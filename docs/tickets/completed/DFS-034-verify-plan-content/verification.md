# DFS-034: Verify a deletion plan's content against its keepers — verification

## Product owner review

### What you can do now

Before approving a deletion plan, a program using the scanner library can byte-compare every file the plan would quarantine with the copy it is kept against. It gets one verdict per entry (identical, differs and where, missing, now a link or folder, unreadable), progress events, and a cancel that takes effect within 1 MiB even in a huge file. Nothing on disk is touched. There is no CLI command or server screen for it yet: this is the library piece the UI's "double-check the plan" step (U7.3) will call.

### Acceptance criteria

- **DFS-034.AC-1** met: identical copies come back `IDENTICAL`, one per entry, in plan order, naming the planned file and its keeper.
- **DFS-034.AC-2** met: same-size difference reports the exact byte (3145727 in a 3 MiB file); size change reports `sizes differ: 6 vs 5` without opening the files.
- **DFS-034.AC-3** met: a missing candidate or keeper is reported as such and the remaining entries are still checked.
- **DFS-034.AC-4** met: a symlink, folder or (tested on this Windows machine) junction is `NOT_A_REGULAR_FILE`; the symlink pointed to identical bytes and was still refused, so nothing behind it was read.
- **DFS-034.AC-5** met: an access-denied entry is `UNREADABLE` with a message and the next entry is still checked.
- **DFS-034.AC-6** met: one "entry checked" event per entry with the right counts; the byte progress never goes back and ends exactly at the total; a throwing listener is dropped after one call and its message is kept.
- **DFS-034.AC-7** met: a cancel during the 3 MiB file stops after one chunk with only the earlier entry reported; a cancel set before the start returns nothing.
- **DFS-034.AC-8** met: an empty plan gives an empty, not-cancelled report; any null input throws `NullPointerException`.
- **DFS-034.AC-9** met: names, sizes and modified times are the same before and after a verify.
- **DFS-034.AC-10** met: `ScannerFactories.verifyPlanContent()` works, Spring provides exactly one bean, and an app's own bean replaces it.

### Try it

Nothing is user-visible yet (no CLI flag, no server endpoint). Run the story tests:

```
./gradlew :file-scanner:test --tests "*VerifyPlanContentServiceTest" --tests "*NioContentComparatorTest"
./gradlew :scanner-spring:test --tests "*ScannerAutoConfigurationTest"
```

Look for 14 + 6 tests passing with 0 skipped (on Windows with symlink rights the symlink and junction tests run rather than skip), and 14 in the Spring test class, including the two `DFS-034.AC-10` ones.

### Decided without you

- `Outcome` is nested as `OperationCheck.Outcome` instead of a top-level type (the bare name is too generic for an exported package).
- The `NOT_A_REGULAR_FILE` detail reads `candidate|keeper is a symlink|a junction|a directory|a special file`.
- `listenerFailure` holds the bare exception message (`"boom"`), not `ClassName: message` as the scan does; `toString()` when the message is blank.
- A file that disappears between the attributes read and the compare is reported as `UNREADABLE`, not as missing.
- The comparator returns a small sealed result (`Identical`, `Differs(offset)`, `Cancelled`); the chunk size can be set through a constructor (tests use 16 bytes); the factory uses 1 MiB.
- Files are opened with `NOFOLLOW_LINKS`, so a file that turns into a link between the attributes check and the open is refused (`UNREADABLE`) rather than followed.
- Only `RuntimeException`s from the listener detach it; an `Error` propagates. An exception thrown by the cancel signal itself propagates (a caller bug).
- The Spring bean test verifies a one-entry plan (DFS-033's helper) rather than the first scenario's two-entry plan; the factory test uses the scenario's plan.

### Known limits

- Synchronous only: the background job with start, status, cancel and result is DFS-042; the server endpoint is the frontend lane's.
- A candidate that has become a hard link of its keeper reports `IDENTICAL` (out of scope; moving it would be harmless).
- If a cancel arrives exactly in the progress callback of a file's last chunk and the file size is a multiple of 1 MiB, the comparator sees the cancel before noticing end-of-file, so that fully compared entry is dropped as "interrupted". Consistent with decision 6, harmless (the verify just reports one entry fewer), but worth knowing.
- Execution must still re-verify at delete time; this is a dry run of that proof, not a replacement.

### Evidence

- `file-scanner/.../application/VerifyPlanContentServiceTest`: 14 tests, 0 skipped, 0 failed (junction test ran on Windows).
- `file-scanner/.../infra/adapter/NioContentComparatorTest`: 6 tests, 0 skipped, 0 failed.
- `scanner-spring/.../ScannerAutoConfigurationTest`: 14 tests (2 new for DFS-034), 0 failed.
- Deterministic gate on f09068d: green (494 passed, 3 skipped, all pre-existing skips), no `@Disabled`, test freeze held since 949be3d, all 19 changed files inside touches, no stub markers, traceability 10 criteria / 12 scenarios, plan.json consistent.
- Test phase showed 22 story tests red on the stubs before implementation.
- `module-info.java` and `fixtures/` unchanged in the diff; no `Thread.sleep` in the new tests.

### How it went

- Design: 1 attempt (architect, opus, 28 turns), passed; design gate approved by you.
- Tests: 1 attempt (test-author, sonnet, 16 turns), 22 tests red on the stubs, traceability passed.
- Implement: 1 attempt (implementer, sonnet, 14 turns), all gate checks passed.
- Verify: 1 attempt. No defects found, no retries, no escalations.

Ready for your approval.

## Summary

- DFS-034.AC-1 met: two `IDENTICAL` checks in plan order, full `OperationCheck` equality asserted.
- DFS-034.AC-2 met: `first difference at byte 3145727` across chunk boundaries; `sizes differ: 6 vs 5` from attributes alone; adapter tests pin offsets and the shorter-file case.
- DFS-034.AC-3 met: `CANDIDATE_MISSING`, `KEEPER_MISSING`, `IDENTICAL` for x, y, z.
- DFS-034.AC-4 met: symlink to same-content file and folder both `NOT_A_REGULAR_FILE` with the right side; junction ran on Windows; adapter refuses to open a symlink.
- DFS-034.AC-5 met: fake comparator's `AccessDeniedException` gives `UNREADABLE` with a detail; y still `IDENTICAL`.
- DFS-034.AC-6 met: `EntryChecked` 1/2, 2/2; `BytesCompared` monotone, ends at 3145733/3145733; throwing listener called once, `listenerFailure` "boom".
- DFS-034.AC-7 met: cancel inside y returns `cancelled` true with x only and last bytesDone below the total; pre-cancelled returns no checks.
- DFS-034.AC-8 met: empty plan and the three null cases.
- DFS-034.AC-9 met: walk snapshot of sizes and mtimes unchanged.
- DFS-034.AC-10 met: factory, single bean, back-off.

Test quality: behavioural (real files, exact expected records and detail strings, event sequences); the AC-4 symlink points at identical bytes, so following it would turn the test red. Design conformance: the implementation follows design.md's behaviour steps, record checks and all 14 checklist dispositions. Safety invariants: read-only (READ-only channels, AC-9), no link or junction followed (NOFOLLOW attributes and NOFOLLOW open), "never delete the last copy", "re-verify at execution" and the hard-link rule are unaffected (nothing deleted). Empty files: a zero-byte pair is `Identical` with no progress; not reachable from a normal scan.

Minor observation, not a defect: a cancel in the last-chunk callback of a file whose size is an exact multiple of the chunk drops that fully compared entry.

Verdict: ready for the human gate (done).
