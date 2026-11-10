# DFS-064 verification (second run)

## Product owner review

### What you can do now

A scan's progress (`GET /api/scans/{id}`, `GET /api/scans` and the live event stream) now carries a `phases` list: one line of totals for each phase that has started (folders, files and bytes found; files and bytes compared; files and bytes hashed; hard-link groups checked), kept after the next phase starts. A scan entry also has `finishedAt`, set once when the scan ends. No screen shows them yet; DFS-063 will.

### Acceptance criteria

- **DFS-064.AC-1** met: once the last file is hashed, the progress shows every file and byte hashed, still in phase `FULL_HASH`, until hard-link checking starts.
- **DFS-064.AC-2** met (fixed since the first verification): each started phase is listed in order with its own totals, kept after the next phase starts; the two hashing phases now keep their files and bytes on a real scan.
- **DFS-064.AC-3** met: the progress fields that existed before keep their values and meaning.
- **DFS-064.AC-4** met: `finishedAt` is empty while the scan runs, then fixed at the time it ended (completed, cancelled or failed), never before the start, the same on every read.
- **DFS-064.AC-5** met as you approved at the design gate: `phases` is always present (empty before the first phase) in both GETs and the stream; `finishedAt` is always present on the two GETs but not in the stream, although the criterion's wording mentions the stream.

### Try it

`./gradlew :server:bootJar`, then `java --enable-native-access=ALL-UNNAMED -jar server/build/libs/dfs-server.jar`, open the logged `?token=` link (or `curl -c jar.txt` it), start a scan of a folder with a few large duplicate files, and repeat `curl -b jar.txt http://127.0.0.1:<port>/api/scans/<id>`. Look for `progress.phases` gaining entries while the scan runs, the `PARTIAL_HASH` and `FULL_HASH` entries keeping non-zero `files` and `bytes`, and `finishedAt` set (and unchanged on re-read) once the status is `COMPLETED`.

### Decided without you

- `finishedAt` is set by a check every 50 ms in the server, plus a check on every read, because the core sends no "scan ended" event; it is accurate to about 50 ms.
- The event stream carries `phases` but not `finishedAt` (raised at the design gate, approved without comment).
- A cancelled or failed scan leaves its last phase entry `finished: false`.
- Fields that do not apply to a phase are 0, never null. The HARD_LINKS `groups` is that phase's own total, not the number of duplicate sets.
- A hashing phase's `files` and `bytes` are the totals the core reports for that phase's input; in PARTIAL_HASH this includes small files that the core does not actually sample (by hand: 8 files, including two 6-byte ones).
- `startedAt` now also comes from the injectable clock.
- The `CLAUDE.md` edit also removed a duplicated sentence in the `server` entry.

### Known limits

- No per-phase start or finish times, no `finishing` field, flat fields not dropped (all out of scope by decision).
- Whether the old bar's long stay at 100% is fully explained by the hard-link phase and result assembly is still a hypothesis; the ticket's post-close check confirms it on a large drive after DFS-063.
- The registry unit tests start the real 50 ms sweeper on the fake clock; they are correct by their ordering, not by construction (see Test quality).

### Evidence

- `./gradlew build` green (all tasks up to date with the current tree). JUnit XML: server 193 tests, 0 failed, 0 skipped; file-scanner 518 (2 skipped), cli 135 (1 skipped), scanner-spring 23.
- `ScanRegistryPhasesTest` 11 of 11 (new: `groupDoneInHashPhasesKeepsFilesAndBytes`), `ScanPhasesApiTest` 3 of 3 (new: `aRealScansHashPhasesKeepTheirTotals`), `OpenApiSpecTest` 3 of 3, `ScanApiTest` 28 of 28.
- By-hand run on the built jar, fixture of 8 files in 3 folders (three identical 400 MB files, two identical 300 KB files and one other of that size, two identical 6-byte files): `phases` seen growing (`[]` at POST, then 3 entries with FULL_HASH `finished:false`, then 4 finished entries); final WALK 4 folders, 8 files, 1200900012 B; PARTIAL_HASH 8 files, 1200900012 B; FULL_HASH 7 files, 1200600012 B (the odd 300 KB file dropped); HARD_LINKS 3 groups; `finishedAt` 04:32:25.67Z after `startedAt` 04:32:21.01Z, identical on re-read and in the list; the stream's event carried the same `phases`; log with no WARN or ERROR.

### How it went

Design: 1 attempt, passed; gate approved without feedback. Tests: 1 attempt, then reopened once by the verify defect (2 new tests, confirmed failing with real assertion failures on the old code). Implement: 2 attempts, both green (the second the one-branch fix). Verify: attempt 1 failed (AC-2 defect found by the by-hand run, routed to the test-author then the implementer); this is attempt 2. No escalations.

**Ready for your approval.**

## Summary

- **DFS-064.AC-1** met: `fullHashEndsAtItsTotalsBeforeHardLinks` pins FULL_HASH at 3/3 files and 300/300 bytes until HARD_LINKS starts.
- **DFS-064.AC-2** met: the `GroupDone` branch now updates only an open HARD_LINKS entry and keeps `directories`/`files`/`bytes`; `groupDoneInHashPhasesKeepsFilesAndBytes` replays the core's order (GroupDone after HashProgress in both hash stages) and the real-scan HTTP test asserts the hash entries' files, bytes and groups 0; reproduced by hand with correct numbers.
- **DFS-064.AC-3** met: flat fields and their reset at `PhaseStarted` unchanged; existing API test classes green.
- **DFS-064.AC-4** met: compare-and-set stamp from the injectable clock via sweep or read; null while RUNNING/CANCELLING; fixed; never before `startedAt`.
- **DFS-064.AC-5** met as read at the design gate: `phases` in both GETs and the event, `finishedAt` (required, nullable) in both GETs, not in the event.
- Verdict: **ready for the human gate**.

## Criteria

### DFS-064.AC-1: met

`fullHashEndsAtItsTotalsBeforeHardLinks` feeds HashProgress 0..3 of 3 and checks the flat counters and the FULL_HASH entry at 3/300, then the phase change to HARD_LINKS. The flat counters are copied from `HashProgress` unchanged; "never more than total" is the core's guarantee (`ScanEvent.HashProgress`). After the fix, a following `GroupDone(FULL_HASH, ...)` no longer touches the entry.

### DFS-064.AC-2: met

- Fix (ee82386, `ScanRegistry.Counters.onEvent`): `GroupDone` updates the last entry only when it and the event are both HARD_LINKS, and copies `directories`, `files` and `bytes` from the entry, setting only `groups`. This is what `design.md` (State and threads) committed to.
- Tests: `groupDoneInHashPhasesKeepsFilesAndBytes` interleaves `GroupDone` with `HashProgress` in PARTIAL_HASH and FULL_HASH as `ScanPipeline` does and asserts whole `PhaseTotals` records (files, bytes, groups 0, `finished`), then HARD_LINKS with groups 1 while FULL_HASH keeps 2/200. `aRealScansHashPhasesKeepTheirTotals` runs a real scan over HTTP and asserts both hash entries have at least 3 files and 3 x 300 KB and groups 0. plan.json records both failing on the old code with real assertion failures.
- The other AC-2 scenarios (order, only started phases, totals 0 before the first HashProgress, COMPLETED finishes every entry) are unchanged and green.
- By hand: correct hash totals on a real scan (see By-hand run).

### DFS-064.AC-3: met

Flat fields and their reset at `PhaseStarted` unchanged; `phases` is the last record component. `flatFieldsAreUnchanged` and the flat part of `finishedPhasesKeepTheirTotals` cover it; `ScanApiTest` (28), `ResultApiTest`, `PlanApiTest`, `OverlapApiTest` green. By hand the flat fields after COMPLETED read as before (hash counters reset by the HARD_LINKS `PhaseStarted`, `groupsDone`/`groupsTotal` 3/3).

### DFS-064.AC-4: met

`finishedAt(Tracked, status)` stamps by compare-and-set with `max(clock, startedAt)`, only for a terminal status; `sweep()` every 50 ms on a daemon thread, stopped by `@PreDestroy`. Fake-clock tests: stamped 10:41, read at 11:30, still 10:41 on `find` and `list`; RUNNING and CANCELLING null; CANCELLED and FAILED 10:05; COMPLETED entry keeps `phases`. By hand: set, after `startedAt`, stable across reads and the list.

### DFS-064.AC-5: met as read at the design gate

`aQueuedScanHasAnEmptyPhasesListAndNoFinishedAt` checks `finishedAt` present and null on GET and list, `phases` `[]` there and in the first stream event. By hand the POST answer (QUEUED) had `"finishedAt":null` and `"phases":[]`. The literal AC wording also puts `finishedAt` in the events; the design raised it, the gate approved without feedback, progress.md records the reading.

## Test quality

- The registry tests assert behaviour (whole `PhaseTotals` records, flat values, stamped instants), and the new test now follows the core's real event order, closing the gap that hid the first defect. The new HTTP test asserts the hash entries with lower bounds (`>=`), which is enough to catch the zeroing defect but would not catch a wrong-but-non-zero total; the registry test pins exact values, so together they are adequate.
- Unchanged from the first run: every registry test builds a real `ScanRegistry` that starts its 50 ms sweeper on the fake clock; tests are correct because they set the clock before the terminal status. Fragile, not failing.
- `ScanPhasesApiTest` sleeps 150 ms before re-reading `finishedAt`; it asserts nothing about timing.

## Design and invariants

- Checklist disposition matches the diff (no new path; spec regenerated with `PhaseTotals`; `phases` required; `finishedAt` required and nullable; two frontend literals; one daemon thread with `@PreDestroy`). The `GroupDone` handling now matches State and threads.
- Every changed file is inside `touches`; no `file-scanner` change.
- Safety invariants: none apply directly (no deletion, preview, walking or hashing change). The listener only writes memory on the scanning thread and does not block it.
- `CLAUDE.md`'s `server` entry names `phases`, `PhaseTotals` and `finishedAt`, and its description of the hash entries ("the `files` and `bytes` of their input") is now true.

## By-hand run

Built `server/build/libs/dfs-server.jar` (up to date with HEAD e19203c) and ran it with `java --enable-native-access=ALL-UNNAMED -jar ... --dfs.server.open-browser=false` from a scratch folder outside the worktree; logged in with `curl` through the token link; started a scan (minSize 1) of a scratch fixture: `a/big.bin`, `b/big-copy.bin`, `c/big-copy2.bin` (identical, 400000000 B each), `a/one.bin` and `b/one-copy.bin` (identical, 300000 B), `c/other.bin` (300000 B, different), `a/s.txt` and `b/s.txt` (6 B each). Polled `GET /api/scans/{id}` in a loop.

- POST: QUEUED, `finishedAt` null, `phases` `[]`.
- During FULL_HASH: 3 entries, WALK and PARTIAL_HASH finished, FULL_HASH `finished:false` with 7 files and 1200600012 B. So `phases` was seen growing.
- COMPLETED: WALK 4/8/1200900012; PARTIAL_HASH 8 files, 1200900012 B, groups 0; FULL_HASH 7 files, 1200600012 B, groups 0; HARD_LINKS groups 3; all finished. `finishedAt` 2026-11-10T04:32:25.671893600Z after `startedAt` 04:32:21.011853200Z; identical on a second GET and in `GET /api/scans`. The stream's one event (COMPLETED) carried the same `phases`.
- Server log: no WARN or ERROR lines. Stopped the server by its PID and deleted the scratch files.

Done-item ticked: "Started by hand once" (run on the built jar rather than `bootRun`, same application).
