---
id: DFS-028
title: Fix three CLI output bugs
status: in-progress
type: fix
scope: [cli]
archetype: none
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
priority: medium
depends_on: []
touches: [cli]
blocked_reason:
created: 2026-10-14
updated: 2026-10-14
completed:
---

# DFS-028: Fix three CLI output bugs

## Story

As a user running scans from the command line
I want a bad folder argument to get a usage message, and the report and progress log to show counts correctly
So that a typo never looks like a crash, and a long scan's output reads right

## Context

Found running the real Media scan (`C:/Media D:/Media E:/Media F:/Media`, 3:18:40) and DFS-026's synthetic 7,993-file scan, 2026-10-13 and 2026-10-14. Exit codes are 0 completed, 1 refused or failed scan, 2 usage error (DFS-020 decision 6).

1. **An unparseable path crashes.** `ScanCommand.run` calls `Path.of(arg)` for each folder argument (about line 100) without catching `InvalidPathException`. On Windows a path ending in a space (for example `E:/Media/iPhone/2018-03 Photoslibrary `) throws `InvalidPathException: Trailing char < >`, which escapes the `ApplicationRunner`: Spring prints "Application run failed" with a stack trace and the exit code is 1. It should be a usage error: one `error:` line naming the argument, the usage line, exit 2 (the same shape as `usage(err, ...)` for the other argument errors).
2. **No thousands separator in the set header.** `TextReport` writes each set's header as `#n  <size> reclaimable  (<copies> copies x <size>, ...)` with the copy count from `entry.copies().size()` printed raw (`TextReport.java` about line 45), so a 7,993-copy set reads `(7993 copies`. Every other count in the report and progress goes through `TextReport.count` (`%,d`, `Locale.ROOT`).
3. **The last `Hard links:` line appears twice with `--progress=log`.** Seen in the log of the DFS-026 synthetic scan: the phase's finished line was printed twice before `Done in`. Traced in refinement: in `LOG` mode `ProgressDisplay.onEvent` prints the current line whenever at least 5 seconds have passed since the last print (`LOG_INTERVAL`, about line 101), and `finishLine` (called from the next `PhaseStarted` and from `completed`) prints `line()` again (about line 202). The synthetic scan had one hard-link set, so its only `GroupDone` event arrived after about 20 minutes, printed `Hard links: 1 / 1 sets  20:55`, and `completed` printed the same line straight after. Any phase can do this when its last event comes 5 seconds or more after the previous print and the phase ends at once; it is not specific to the last phase. `LIVE` is not affected: there `finishLine` only ends the line drawn in place.

Decisions (refinement, 2026-10-14):

1. **The three bugs are one `fix` ticket; `--output=<file>` is DFS-029.** The bugs are understood (except one trace) and can reach `ready` quickly; the option has design questions of its own. Both touch `cli`, so they run one after the other, in either order. Rejected: one ticket for all four (the fixes would wait on the option's design, and the commit type mixes `feat` and `fix`) and one ticket per item (a ticket's process outweighs a one-line fix).
2. **A rejected path is a usage error for the first bad argument, quoted, with the JDK's reason.** For example `error: not a valid path: "E:/Media/iPhone/2018-03 Photoslibrary " (Trailing char < > at index 37)`, then the usage line, exit 2, before any scan starts. The same shape as every other usage error, which also stops at the first problem; the quotes make a trailing space visible, and the JDK's reason (`InvalidPathException.getReason()` and index) covers every rejected case without wording of our own. Rejected: one line per bad argument (the only usage error that would not stop at the first), and a hint written for the trailing-space case (string-matching a JDK message).
3. **Archetype `none`.** Implemented by hand, like DFS-020, DFS-023 and DFS-024, which built these classes. No archetype describes a presentation fix: `wiring` is for domain factories and the composition root, and stubbing methods that already work does not suit a fix. Rejected: `wiring` through the pipeline (its checklist would be mostly "not applicable").
4. **The finished line is skipped when it matches the last printed line, ignoring the elapsed clock.** In `--progress=log`, `finishLine` compares the line it would print with the last line printed, both without the trailing `  <clock>`, and prints nothing when they are equal. This covers the identical case seen in DFS-026 and the case where the finish lands a second later (`20:55`, then `20:56`). The last line then shows the clock of the final event, at most a few seconds before the phase ended; the `Done in` line still gives the total. Rejected: skipping only byte-identical lines (the next-second repeat would remain) and a "changed since last print" flag set by `onEvent` (a later event type that forgot to set it would silently lose a phase's final line).

## Acceptance criteria

The full ID of each criterion is `DFS-028.AC-<n>`.

- **AC-1** A folder argument that `Path.of` rejects gives exit 2 with stderr `error: not a valid path: "<argument>" (<reason>)`, where the reason is the `InvalidPathException`'s reason and index, followed by the usage line; nothing is written to stdout and no scan starts (decision 2).
- **AC-2** The text report's set header writes the copy count through `TextReport.count`, with a comma every three digits (`(7,993 copies x`); counts under 1,000 are unchanged (`(3 copies x`).
- **AC-3** In `--progress=log`, a phase's finished line is not printed when, without its trailing elapsed clock, it equals the last progress line printed; otherwise it is printed as today (decision 4).

## Scenarios

```gherkin
Feature: CLI output fixes

  @AC-1
  Scenario: A path with a NUL character is a usage error on every platform
    Given the arguments "C:/scan/a\u0000b" and "--format=text"
    When the scan command runs
    Then the exit code is 2
    And stderr's first line starts with: error: not a valid path: "C:/scan/a
    And that line ends with the InvalidPathException's reason and index in brackets
    And stderr's second line is the usage line
    And stdout is empty
    And no scan was started

  @AC-1
  Scenario: A folder name ending in a space is a usage error on Windows
    Given the platform is Windows (the test is skipped elsewhere, where Path.of accepts the name)
    And the argument "E:/Media/iPhone/2018-03 Photoslibrary "
    When the scan command runs
    Then the exit code is 2
    And stderr's first line is: error: not a valid path: "E:/Media/iPhone/2018-03 Photoslibrary " (Trailing char < > at index 37)
    And no scan was started

  @AC-1
  Scenario: Only the first rejected path is reported, and a valid folder before it is not scanned
    Given the arguments "C:/scan/ok", "C:/scan/a\u0000b" and "C:/scan/c\u0000d"
    When the scan command runs
    Then the exit code is 2
    And stderr has exactly one "error:" line, naming "C:/scan/a
    And no scan was started

  @AC-2
  Scenario: A set of 7,993 copies shows the separator in its header
    Given a completed scan with one duplicate set of 7,993 copies of 4,096 bytes
    When the text report is written
    Then the set's header contains "(7,993 copies x "

  @AC-2
  Scenario: A small set's header is unchanged
    Given a completed scan with one duplicate set of 3 copies of 15 bytes
    When the text report is written
    Then the set's header contains "(3 copies x 15 B"

  @AC-3
  Scenario: A final event and the finish in the same second print the line once
    Given progress mode log and the phase "Hard links" started at 0:00 with 1 set
    And the event "1 of 1 sets done" arrives at 20:55
    When the scan completes at 20:55
    Then stderr has exactly one line "Hard links: 1 / 1 sets  20:55"
    And it is followed by the "Done in 20:55" line

  @AC-3
  Scenario: The finish a second after the final event does not repeat the line
    Given progress mode log and the phase "Hard links" started at 0:00 with 1 set
    And the event "1 of 1 sets done" arrives at 20:55
    When the scan completes at 20:56
    Then stderr has exactly one line starting "Hard links: 1 / 1 sets", and it ends "20:55"
    And it is followed by the "Done in 20:56" line

  @AC-3
  Scenario: A finished line with new counts is still printed
    Given progress mode log and the phase "Hashing" started at 0:00 with 20 files
    And the event "10 of 20 files hashed" arrives at 0:05 and is printed
    And the event "20 of 20 files hashed" arrives at 0:07, too soon to be printed
    When the next phase starts at 0:07
    Then stderr has a "Hashing:" line with "10 / 20 files" ending "0:05"
    And then a "Hashing:" line with "20 / 20 files" ending "0:07"

```

## Scope

**Touches:**
- `cli`: `ScanCommand` (the path error), `TextReport` (header count), `ProgressDisplay` (duplicate line), and their tests

**Out of scope:**
- `--output=<file>` (DFS-029).
- Scanning a folder whose name Windows `Path.of` rejects (it stays a usage error).
- Changing the JSONL schema or the text report's layout beyond the copy count.

## Definition of done

- [x] Every scenario above is an automated test and passes: 8 tests named `DFS-028.AC-n`, in `ScanCommandTest` (3), `TextReportTest` (2) and `ProgressDisplayTest` (3); with the fixes stashed, the 6 that cover the bugs fail and the 2 guards (`A finished line with new counts is still printed`, `A small set's header is unchanged`) pass
- [x] `./gradlew build` is green (includes the ArchUnit and module-export tests): 262 tests, 0 failed, 2 skipped (2026-10-14)
- [x] No files changed outside the paths listed under Touches

## Open questions

None.
