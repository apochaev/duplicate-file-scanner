# DFS-064 verification

## Product owner review

### What you can do now

Once the defect below is fixed, a scan's progress (`GET /api/scans/{id}`, `GET /api/scans` and the event stream) will carry a `phases` list: one line of totals for each phase that has started, kept after the next phase starts. A scan entry also gets a `finishedAt` time once the scan ends. No screen shows either yet; DFS-063 will use them.

### Acceptance criteria

- **DFS-064.AC-1** met: at the end of full hashing, the progress shows every file and byte hashed, still in phase `FULL_HASH`, until hard-link checking starts.
- **DFS-064.AC-2** **not met on a real scan**: the walk and hard-link entries are right, but the two hashing phases always end up with `files` 0 and `bytes` 0. The core sends a "group done" event in the hashing phases, and the server's handler for that event clears the totals. The tests pass only because their fake core never sends that event in a hashing phase.
- **DFS-064.AC-3** met: the existing flat fields keep their values and meaning.
- **DFS-064.AC-4** met: `finishedAt` is null while the scan runs and is fixed when it ends (completed, cancelled or failed). It is never before `startedAt`, and it is the same on every later read.
- **DFS-064.AC-5** met as you approved at the design gate: `phases` appears everywhere and `finishedAt` appears on the two GETs. The event stream does not carry `finishedAt`, although the criterion's wording says it does.

### Try it

`./gradlew :server:bootRun`, open the logged `?token=` link, start a scan through the page or `curl`, then `curl` `GET /api/scans/<id>` with the session cookie. Check that `progress.phases` lists WALK, PARTIAL_HASH, FULL_HASH and HARD_LINKS, and that `finishedAt` is set. Today the PARTIAL_HASH and FULL_HASH entries show `"files":0,"bytes":0,"groups":<n>`, which is the defect.

### Decided without you

- `finishedAt` is set by a check every 50 ms in the server, plus a check on every read, because the core sends no "scan ended" event. The time is accurate to about 50 ms.
- The event stream carries `phases` but not `finishedAt` (raised at the design gate and approved without comment).
- A cancelled or failed scan leaves its last phase entry `finished: false`.
- Fields that do not apply to a phase are 0, never null. The HARD_LINKS `groups` is that phase's own total, not the number of duplicate sets.
- `startedAt` now also comes from the injectable clock.
- The `CLAUDE.md` edit also removed a duplicated sentence that was already in the `server` entry.

### Known limits

- The hashing-phase totals are lost (the defect above). This blocks DFS-063's "29594 compared → 13845 candidates" lines.
- There are no per-phase start or finish times, and no `finishing` field (both out of scope by decision).
- The by-hand scan finished before the first stream event, so the run showed the end state but not `phases` growing step by step.

### Evidence

- `./gradlew build`: green, re-run by the verifier. JUnit XML: server 191 tests, 0 failed, 0 skipped; file-scanner 518 (2 skipped), cli 135 (1 skipped), scanner-spring 23.
- `ScanRegistryPhasesTest`: 10 of 10 pass. `ScanPhasesApiTest`: 2 of 2. `OpenApiSpecTest`: 3 of 3. `ScanApiTest`: 28 of 28.
- By-hand run (the built `dfs-server.jar`, 5 files in 3 folders, two duplicate pairs). It showed `phases` with four finished entries and `finishedAt` `04:19:03.643Z` after `startedAt` `04:19:03.521Z`, the same on a second read and on the list; the server log had no WARN or ERROR. The hash entries showed `{"phase":"PARTIAL_HASH","finished":true,"directories":0,"files":0,"bytes":0,"groups":2}`, which is wrong: PARTIAL_HASH had 2 input files of 300000 bytes.

### How it went

Design: 1 attempt, passed; gate approved without feedback. Tests: 1 attempt, passed (12 tests red on the stubs). Implement: 1 attempt, passed (green, 1064 passed, 3 skipped). No retries or escalations so far. The verifier found one defect in the by-hand run.

**Not ready for approval. Send it back to the tests phase, then implement:** first a test that fails on the defect, then the fix.

## Summary

- **DFS-064.AC-1** met: `ScanRegistryPhasesTest` pins FULL_HASH at 3/3 files and 300/300 bytes until HARD_LINKS starts.
- **DFS-064.AC-2** not met: `Counters.onEvent`'s `GroupDone` branch rebuilds the open entry as `(phase, false, 0, 0, 0, total)` for every phase. The core sends `GroupDone` in PARTIAL_HASH and FULL_HASH after their `HashProgress` (`ScanPipeline.java:196`), so on a real scan both hash entries end with `files` and `bytes` 0 and a non-zero `groups`. Reproduced by hand. The tests miss it: the fake core sends `GroupDone` only in HARD_LINKS, and the HTTP real-scan test asserts only the WALK numbers.
- **DFS-064.AC-3** met: flat fields untouched; the existing API test classes stay green.
- **DFS-064.AC-4** met: compare-and-set stamp from the injectable clock, through the sweep or a read; null while running or cancelling; fixed; never before `startedAt`; the fake-clock tests are deterministic.
- **DFS-064.AC-5** met as read at the design gate: `phases` in both GETs and the event, `finishedAt` in both GETs; `finishedAt` is not in the event.
- Verdict: **back to the tests phase**, for a test that feeds `GroupDone` in a hash phase (or asserts the real scan's hash totals), then to **implement**: `GroupDone` must leave `files` and `bytes` alone and set `groups` only for HARD_LINKS, as `design.md` says.

## Criteria

### DFS-064.AC-1: met

`fullHashEndsAtItsTotalsBeforeHardLinks` feeds HashProgress 0..3 of 3 and checks the flat counters and the FULL_HASH entry at 3/300, then the phase change to HARD_LINKS. The flat counters are copied from `HashProgress` unchanged. The "never more than total" part is the core's guarantee (`ScanEvent.HashProgress` javadoc); the test checks it only on one middle sample, which is enough. On a real scan the FULL_HASH *entry* would be overwritten by a following `GroupDone` (see AC-2), but AC-1 is about the flat fields and `phase`, which are correct.

### DFS-064.AC-2: not met

- Order, the `finished` flags and the WALK totals: correct, also on the real scan (by hand and in `ScanPhasesApiTest`).
- Hash-phase input totals: lost on a real scan. `ScanRegistry.java` lines 376-379:
  ```java
  case ScanEvent.GroupDone e -> {
      if (lastIs(e.phase().name())) {
          updateLast(p -> new PhaseTotals(p.phase(), false, 0, 0, 0, e.total()));
  ```
  `design.md` (State and threads) says only `GroupDone(HARD_LINKS, ...)` sets `groups`, and that components that do not apply stay 0. The handler applies to every phase and zeroes `files` and `bytes`. The core sends `GroupDone(phase, i+1, total)` after every group of every stage (`ScanPipeline.java:196`), so the PARTIAL_HASH and FULL_HASH entries show `files` 0, `bytes` 0 and `groups` n. By hand: `{"phase":"PARTIAL_HASH","finished":true,"directories":0,"files":0,"bytes":0,"groups":2}` and the same for FULL_HASH.
- Why the gate passed: `ScanRegistryPhasesTest.throughHardLinks` never sends `GroupDone` in a hash phase, so it does not follow the core's real event order. `ScanPhasesApiTest.aRealScanListsItsPhasesAndFinishes` asserts the WALK numbers only (test-plan.md: "nothing about the other phases' numbers").

### DFS-064.AC-3: met

The flat fields and their reset at `PhaseStarted` are unchanged; `phases` is appended as the last component. `flatFieldsAreUnchanged` and the flat part of `finishedPhasesKeepTheirTotals` cover it. `ScanApiTest` (28), `ResultApiTest`, `PlanApiTest` and `OverlapApiTest` stay green.

### DFS-064.AC-4: met

`finishedAt(Tracked, status)` stamps by compare-and-set with `max(clock, startedAt)` and only for a terminal status. `sweep()` runs every 50 ms on a daemon thread and `@PreDestroy` stops it. The tests use the package-private clock constructor and call `sweep()` directly. Stamped 10:41 and read at 11:30, the entry still says 10:41, on `find` and on `list`. Running and CANCELLING give null. CANCELLED and FAILED give 10:05. A COMPLETED entry keeps `phases`. By hand: `finishedAt` is set, after `startedAt`, and stable.

### DFS-064.AC-5: met as read at the design gate

`aQueuedScanHasAnEmptyPhasesListAndNoFinishedAt` checks that `finishedAt` is present and null on GET and on the list, that `phases` is `[]`, and that the first stream event has `phases` `[]`. The literal AC text also wants `finishedAt` in the progress events. The design raised this as an open note, the gate approved it without feedback, and progress.md records that reading.

## Test quality

- The registry tests check behaviour (whole `PhaseTotals` records, flat values, stamped instants), not the implementation, and the fake clock makes them deterministic. One weakness: the fake core does not send the real core's event order (`GroupDone` after each group in every stage). That gap hid the AC-2 defect. A test should either replay a real-shaped sequence or assert the hash totals of a real scan over HTTP.
- Timing: every registry test builds a real `ScanRegistry`, which starts its 50 ms background sweeper on the fake clock. The tests are correct only because they set the clock before the terminal status, or do not assert `finishedAt` (`completedScanFinishesEveryEntry` sets the status first and asserts no instant). The sweeper threads are never shut down in these tests (daemon threads, so harmless). This is fragile; it is not a failure.
- `ScanPhasesApiTest` sleeps 150 ms before re-reading `finishedAt`. That is acceptable: it asserts nothing about timing.

## Design and invariants

- Checklist disposition: matches the diff (no new path, spec regenerated with `PhaseTotals`, `phases` required, `finishedAt` required and nullable, two frontend literals, one new daemon thread with `@PreDestroy`). Exception: the `GroupDone` handling in State and threads is not what was implemented (AC-2 above).
- Every changed file is inside `touches`. No `file-scanner` change.
- Safety invariants: none apply directly (no deletion, preview, walking or hashing change). The listener only writes memory and cannot throw from the event path. Each `updateLast` copies the list per event, which is O(phases) and fine. `sweep` calls `getScanStatus` for each unfinished scan every 50 ms, which is in memory and cheap.
- `CLAUDE.md`'s `server` entry names `phases`, `PhaseTotals` and `finishedAt`. Its wording ("`PARTIAL_HASH` and `FULL_HASH` with the `files` and `bytes` of their input") describes the intended behaviour, which the code does not deliver yet.

## By-hand run

Built `server/build/libs/dfs-server.jar` (HEAD 930cfa6, build up to date) and ran it with `java --enable-native-access=ALL-UNNAMED -jar ... --dfs.server.open-browser=false` from a scratch folder. I logged in through the token link with `curl`, then started a scan of a scratch fixture (a/one.bin 300000 B, b/one-copy.bin the same content, b/other.bin 200000 B, a/s.txt and b/s.txt 6 B each; minSize 1).

- POST answered `finishedAt: null`, `phases: []` (QUEUED).
- The stream's only event was already COMPLETED, so `phases` growing was not observable on a scan this small.
- GET twice and the list: `finishedAt` was stable and after `startedAt`. The WALK entry had 3 folders, 5 files and 800012 bytes. PARTIAL_HASH and FULL_HASH had `files` 0, `bytes` 0 and `groups` 2: the defect.
- The log had no WARN or ERROR. I stopped the server by its PID and deleted the scratch files.

Done-item left unticked: the run reproduced the AC-2 defect and could not show `phases` growing.
