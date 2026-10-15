---
id: DFS-025
title: Make the hashing ETA and percentage account for files left, not only bytes
status: draft
type: fix
scope: [cli]
archetype:
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
priority: low
depends_on: [DFS-024]
touches: []
blocked_reason:
created: 2026-10-13
updated: 2026-10-14
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

The `HashProgress` event already carries `filesDone`, `filesTotal`, `bytesDone` and `bytesTotal`, so the fix is likely confined to `ProgressDisplay` in `cli`.

## Acceptance criteria

The full ID of each criterion is `DFS-025.AC-<n>`.

- **AC-1** <to be written during refinement>

## Scenarios

```gherkin
Feature: Hashing ETA

  @AC-1
  Scenario: <many small files left after the bytes are nearly done>
    Given <concrete state>
    When <action>
    Then <observable outcome>
```

## Scope

**Touches:**
- `us.pochaev.dfs.cli` (`ProgressDisplay`, its test): <to confirm>

**Out of scope:**
- Making the scan faster (the user has deferred speed work).
- The Sampling and Hard links lines, which show no ETA.

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] No files changed outside the paths listed under Touches
- [ ] <ticket-specific check, e.g. a real scan with many small files shows an ETA that does not sit at a few seconds for minutes>

## Open questions

1. The estimate model: (a) the larger of a bytes-based and a files-based estimate; (b) a per-file cost plus a per-byte cost fitted from progress so far; (c) a recent-window rate instead of the average since the phase started. Trade-offs to be weighed in refinement.
2. Whether the percentage should stay bytes-based (it read 99% with a third of the files left).
3. Archetype: `none` or a template set.
