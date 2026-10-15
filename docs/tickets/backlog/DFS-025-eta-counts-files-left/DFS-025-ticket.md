---
id: DFS-025
title: Make the hashing ETA and percentage account for files left, not only bytes
status: in-progress
type: fix
scope: [cli]
archetype: none
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
priority: low
depends_on: [DFS-024]
touches:
  - cli/src/main/java/us/pochaev/dfs/cli/ProgressDisplay.java
  - cli/src/test/java/us/pochaev/dfs/cli/ProgressDisplayTest.java
blocked_reason:
created: 2026-10-13
updated: 2026-10-15
completed:
---

# DFS-025: Make the hashing ETA and percentage account for files left, not only bytes

## Story

As a user scanning large media drives
I want the hashing ETA and percentage to stay realistic when many small files remain
So that I can trust it to plan around a multi-hour scan

## Context

DFS-024 decision 7 computes the `Hashing:` ETA as the bytes left divided by the average byte rate since FULL_HASH started. The largest size groups are hashed first, so the end of the phase is many tiny files whose cost is a disk seek each, not their bytes.

Observed on 2026-10-13 (`C:/Media D:/Media E:/Media F:/Media`, 7.3 TB, spinning disks): `Hashing:    323,498 / 495,829 files, 896.2 GB / 896.8 GB (99%)  ETA 0:05  2:43:12`. About 172,000 files (about 3.5 KB each) were left; the phase ended at `495,829 / 495,829 files … 3:00:29`, so that `ETA 0:05` was in fact about 17 minutes (about 166 files/s). The user reports the line had already sat above 90% for a long stretch while hundreds of thousands of small files were hashed, so the bytes-based percentage and ETA misreport the whole tail of the phase, not just its last seconds. Earlier in the same phase the byte-based ETA was reasonable (2:02:14 at 14%, falling to 1:33:09 at 29%). Sampling, which is mostly seeks, ran at about 235 files/s.

Observed again on 2026-10-14, on a smaller scan (`E:/Media/iPhone/2018-03 Photoslibrary E:/Media/iPhone/iPhoneOxana F:/Media/iPhone/oxana E:/Media/Video/Import E:/Media/iPhone/GMetrix`, 34,970 files, `--progress=log`): bytes reached `6.4 GB / 6.4 GB (99%)  ETA 0:01` at 3:15 with `214 / 3,700 files` hashed, and the line then read `ETA 0:00` for the rest of the phase while the files count rose `500`, `712`, `961`, `2,036`, `2,327`, `2,573`, `2,900`, `3,217`, `3,577` and finally `3,700 / 3,700 files … (100%)` at 4:02: 46 seconds at "ETA 0:00", about 75 files/s. The same tail, scaled down.

Observed a third time on 2026-10-15, after DFS-027 (the five folders above plus `F:/Media/Catalogs/ApplePhotos`: 81,694 files, 1.2 TB, `--progress=log`, done in 4:56; 25,296 metadata files excluded). Hashing covered 5,756 files and 22.4 GB. The byte-based ETA tracked well while the large files ran (`ETA 3:11` at 0:50, `ETA 1:30` at 1:45, `ETA 0:26` at 2:46), but files lagged far behind bytes: `360 / 5,756 files, 22.1 GB / 22.4 GB (98%)  ETA 0:02` at 3:11. The line then read `ETA 0:00` from `610 / 5,756 files … (99%)` at 3:16 to `5,756 / 5,756 files … (100%)` at 4:56: 1 minute 40 seconds, about a third of the whole run, with 5,146 files left when it first showed 0:00 (about 51 files/s over that tail). With DFS-027's excludes on, the tail is still thousands of small real files, so excluding metadata alone does not fix it.

The `HashProgress` event already carries `filesDone`, `filesTotal`, `bytesDone` and `bytesTotal`, so the fix is likely confined to `ProgressDisplay` in `cli`.

**Decisions (refinement, 2026-10-15):**

1. **Two rules: byte time plus a per-file cost, then a recent files rate near the end.** Until bytes reach 99%, the ETA is `bytes left / average byte rate since FULL_HASH started + files left x per-file cost`, where the per-file cost is the Sampling phase's elapsed time divided by the files it sampled (2026-10-15: 7,754 files in 45 s, about 5.8 ms). From 99% of bytes on, the ETA is `files left / files hashed per second since the newest progress report at least 5 seconds old` (amended from 10 seconds after the first real run, see below). Replayed against the 2026-10-15 log, this gives 3:44 at 0:50 (real 4:06), 0:59 at 2:46 (real 2:10) and 1:43 at 3:16 (real 1:40), against today's 3:11, 0:26 and 0:00. The existing guards stay: no ETA before `ETA_MIN_TIME` or before 1% of bytes. Rejected: the larger of a bytes-based and a files-based estimate (reads hours for most of the phase, since the largest files go first: `18 / 5,756 files` at 2:00 gives about 6.6 h); switching to files near the end alone (ignores the small-file tail until 99%: 0:26 at 2:46 against 2:10 real); the per-file cost alone (0:30 at 3:16 against 1:40 real, since full hashing ran 51 files/s against Sampling's 172).
   Amended 2026-10-15 after the first real run with a 10-second window (same six folders, hashing 1:10 to 4:51): no false `ETA 0:00` any more (it appeared only on the final line), but the first line at or above 99% read `ETA 3:10` with 1:10 left (2.7x), because files get smaller and hashing speeds up through the tail, so a window looking back over larger files overestimates. Replayed from that log, a 5-second window (the log interval) gives about 2:03 there (1.76x) and 1.4x to 2x on later lines. The window became 5 seconds; the ticket's scenarios keep their values, since their reports are 10 seconds apart.
2. **Missing inputs fall back to what this ticket already defines.** (a) When the Sampling phase sampled no files (every candidate under the 128 KB partial-hash threshold), the per-file cost is 0, so rule 1 is today's byte-based ETA. (b) After 99% of bytes, when no file finished since the newest progress report at least 5 seconds old (one large file still hashing), the ETA is rule 1's value instead of a division by zero. Rejected: a fixed default per-file cost with `--:--` for (b) (a constant tuned to one disk, and a gap exactly when the user waits for the end); learning the per-file cost from hashing (the fitted model decision 1 did not choose, noisy while large files run).
3. **The percentage stays a byte percentage, unlabelled.** It sits right after the byte counts, so it reads as one, and decision 1's ETA is the figure meant for planning. Rejected: a percentage of estimated time (moves backwards as the estimate grows: 67% at 2:46, 59% at 3:16 in the replay), a files percentage (near 0% for most of the phase, 2% at 2:46 with 75% of bytes done), and the label `(99% of bytes)` (a longer line for little gain).
4. **Archetype `none`.** One `cli` class and its test, no domain types or ports, every design choice already made here; DFS-023, DFS-024 and DFS-028 (the same display code) were `none`. Rejected: `pipeline-stage` (its checklist is written for `file-scanner` stages and mostly does not apply).

How the inputs reach `ProgressDisplay` today (read 2026-10-15): `PhaseStarted(PARTIAL_HASH)` and `PhaseStarted(FULL_HASH)` give the Sampling phase's start and end on the injected `nanos` clock; the last `HashProgress(PARTIAL_HASH, filesDone, …)` gives the files sampled; `HashProgress(FULL_HASH, filesDone, filesTotal, bytesDone, bytesTotal)` drives the ETA. The 10-second window needs the `(time, filesDone)` of recent `HashProgress` events, kept inside `ProgressDisplay`; no event or `file-scanner` change is needed.

## Acceptance criteria

The full ID of each criterion is `DFS-025.AC-<n>`.

- **AC-1** Before bytes reach 99%, the `Hashing:` ETA is the bytes left divided by the average byte rate since hashing started, plus the files left times the Sampling phase's average time per file; from 99% of bytes on, it is the files left divided by the files hashed per second since the newest progress report at least 5 seconds old. The existing guards stay: `--:--` before 3 seconds of hashing and before 1% of bytes (decision 1).
- **AC-2** When the Sampling phase sampled no files, the per-file cost is 0 and the ETA before 99% of bytes equals the byte-based estimate; after 99%, when no file finished since the newest progress report at least 5 seconds old, the ETA is the before-99% estimate (decision 2).
- **AC-3** The `Hashing:` percentage stays `bytes done x 100 / bytes total`, unchanged by the files left (decision 3).

## Scenarios

```gherkin
Feature: Hashing ETA

  Background:
    Given a ProgressDisplay in log mode on a fake clock
    And the Sampling phase starts at 0 s, reports 100 files sampled, and hashing starts at 10 s
    # per-file cost: 10 s / 100 files = 0.1 s

  @AC-1
  Scenario: Before 99% of bytes, the ETA adds a per-file cost for the files left
    When at 20 s hashing reports 10 of 110 files and 500,000,000 of 1,000,000,000 bytes
    Then the Hashing line shows "ETA 0:20"
    # bytes: 500,000,000 left at 50,000,000 per second = 10 s; files: 100 left x 0.1 s = 10 s
    # the old byte-only estimate would show 0:10

  @AC-1
  Scenario: From 99% of bytes on, the ETA uses the recent files rate
    When at 60 s hashing reports 50 of 110 files and 990,000,000 of 1,000,000,000 bytes
    And at 70 s hashing reports 70 of 110 files and 995,000,000 of 1,000,000,000 bytes
    Then the Hashing line at 70 s shows "ETA 0:20"
    # base: the report at 60 s, the newest at least 5 s old; 20 files in 10 s = 2 per second; 40 left / 2 = 20 s

  @AC-1
  Scenario: The existing guards still show no estimate too early
    When at 12 s hashing reports 2 of 110 files and 100,000,000 of 1,000,000,000 bytes
    Then the Hashing line shows "ETA --:--"
    When at 20 s hashing reports 3 of 110 files and 5,000,000 of 1,000,000,000 bytes
    Then the Hashing line shows "ETA --:--"
    # under 3 s of hashing in the first case; under 1% of bytes in the second

  @AC-2
  Scenario: With no files sampled, the per-file cost is 0
    Given instead the Sampling phase starts at 0 s, reports no files, and hashing starts at 10 s
    When at 20 s hashing reports 10 of 110 files and 500,000,000 of 1,000,000,000 bytes
    Then the Hashing line shows "ETA 0:10"

  @AC-2
  Scenario: From 99% of bytes on, with no file finished since the recent base, the ETA falls back to the first rule
    When at 60 s hashing reports 50 of 110 files and 990,000,000 of 1,000,000,000 bytes
    And at 70 s hashing reports 50 of 110 files and 995,000,000 of 1,000,000,000 bytes
    Then the Hashing line at 70 s shows "ETA 0:06"
    # bytes: 5,000,000 left at 995,000,000 / 60 s, about 0.3 s; files: 60 left x 0.1 s = 6 s

  @AC-3
  Scenario: The percentage stays a byte percentage
    When at 70 s hashing reports 50 of 110 files and 995,000,000 of 1,000,000,000 bytes
    Then the Hashing line shows "(99%)"
```

## Scope

**Touches:**
- `cli/src/main/java/us/pochaev/dfs/cli/ProgressDisplay.java`: the ETA rules (decisions 1, 2) and the per-file cost and files-rate window they need.
- `cli/src/test/java/us/pochaev/dfs/cli/ProgressDisplayTest.java`: the scenarios above; existing ETA assertions change only where the per-file cost now adds to them.

**Out of scope:**
- Making the scan faster (the user has deferred speed work).
- The Sampling and Hard links lines, which show no ETA.

## Definition of done

- [x] Every scenario above is an automated test and passes: six `DFS-025.AC-*` tests in `ProgressDisplayTest`, 19 of 19 passing
- [x] `./gradlew build` is green (includes the ArchUnit and module-export tests): 315 tests, 0 failed, 2 skipped
- [x] No files changed outside the paths listed under Touches: `ProgressDisplay.java`, `ProgressDisplayTest.java` and this ticket
- [x] A real scan of `'E:/Media/iPhone/2018-03 Photoslibrary' E:/Media/iPhone/iPhoneOxana F:/Media/iPhone/oxana E:/Media/Video/Import E:/Media/iPhone/GMetrix F:/Media/Catalogs/ApplePhotos --format=text --progress=log` (2026-10-15 baseline: hashing 0:45 to 4:56, `ETA 0:00` from 3:16 to 4:56) shows no `ETA 0:00` on any `Hashing:` log line earlier than 10 seconds before the phase's final line, and the ETA on the first line at or above 99% of bytes is within half to double of the time actually left
  - Run 2026-10-15 with the 5-second base: hashing 0:59 to 4:20. `ETA 0:00` appears only on the final line at 4:20. The first line at or above 99%, at 3:29 (`618 / 5,756 files`), read `ETA 1:28` with 51 s left (1.7x); later lines read 1:10 (46 s left), 0:43 (31 s), 0:28 (21 s), 0:11 (5 s). Before 99% the ETA read 3:08 at 1:04 against 3:16 real. Summary unchanged: `573 sets, 1,391 files, 11.2 GB reclaimable, 0 skipped, 25,296 excluded`.

## Open questions

None.
