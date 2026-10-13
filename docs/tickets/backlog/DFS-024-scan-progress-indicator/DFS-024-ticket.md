---
id: DFS-024
title: Show scan progress with folders, files, bytes and ETA
status: ready
type: feature
scope: [file-scanner, cli, docs]
archetype: none
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
priority: high
depends_on: [DFS-023]
touches: [CLAUDE.md, us.pochaev.dfs.scanner.domain.model, us.pochaev.dfs.scanner.application, us.pochaev.dfs.scanner.infra.port, us.pochaev.dfs.scanner.infra.adapter, us.pochaev.dfs.cli]
blocked_reason:
created: 2026-10-13
updated: 2026-10-13
completed:
---

# DFS-024: Show scan progress with folders, files, bytes and ETA

## Story

As a user of the duplicate file scanner
I want to see while a scan runs how many folders and files it has found, how much of the candidate data it has hashed, and how long is left
So that I know a long scan is making progress and can judge whether to wait for it

## Context

Progress today (DFS-020 decision 4): `ScanCommand.Progress`, a `ScanListener`, writes plain lines to stderr: `scan: <phase> started` per phase, then `scan: walk <n> files` and `scan: <phase> <done>/<total> groups` at most once a second. On a real 4.5-minute scan (`scan-performance` note, 2026-10-08) the full-hash phase took about 240 of 271 seconds, and its progress counted groups: a group may be two 1 KB files or two 4 GB videos, so `812/4676 groups` predicts nothing. The lines scroll, and folders are not counted.

What the scanner sends today (`ScanEvent`, `domain.model`, sealed): `PhaseStarted(phase)`, `FileWalked(filesSoFar)`, `GroupDone(phase, done, total)` and `Skipped(skipped)`. They are emitted in `ScanPipeline`: the walk (from `WalkEvent.FileFound` and `WalkEvent.Skipped` of `FileTreeWalker`; there is no directory event) and `runGroupPhase`, which runs a phase's groups one at a time on the scan's thread. Hashing happens in `PartialHashFilter` (`ContentHasher.sampleHash`) and `FullHashConfirmer` (`ContentHasher.fullHash`, returns a digest only, no progress within a file). `ScanService` passes every event to the caller's listener and applies it to the `ScanSnapshot` (`filesWalked`, `groupsDone`, `groupsTotal`). Exhaustive switches over `ScanEvent` exist in `ScanService`, `ScanCommand` and `ScanPipelineTest`.

Most files never reach hashing: after the walk, files of a unique size are dropped (`SizeIndex`), so "processed" (hashed) is far fewer than "found" (walked).

**Decisions (2026-10-13):**

1. **One ticket, both halves, `archetype: none`.** New progress events in `file-scanner` and a new progress display in `cli`, built by hand like DFS-020 and DFS-023, for speed. Chosen over a CLI-only line on today's events (no folder count, a misleading group percentage, no honest ETA) and over a split into a pipeline ticket plus a CLI ticket (slower). Tests carry `DFS-024.AC-n` in their `@DisplayName`; the implementing session ticks each done-item with its evidence.

2. **One line per phase, each with totals fixed for that phase; bytes and ETA only in the full-hash phase**, where the time goes (about 240 of 271 s on the real scan). The full-hash candidates' bytes are known only once the partial phase has dropped its non-matches, so a combined line would have a moving total. Each line is updated in place while its phase runs and stays as a finished line when the next phase begins. Target display (wording and layout to be settled below):

```
Walking:    1,203 folders, 33,984 files, 41.2 GB found               0:02
Sampling:   4,120 / 9,812 candidate files                            0:03
Hashing:    1,840 / 6,377 files, 2.1 / 9.8 GB (21%)  ETA 3:10        0:58
Hard links: 1,466 / 2,680 sets                                       4:20
Done in 4:31: 33,984 files found, 9,812 compared, 3,330 duplicate sets
```

3. **Small, specific events, each sent where its fact arises** (chosen over one wide cumulative `Progress` event, and over counting folders from the found files' parents, which misses folders without files):
   - `WalkEvent.DirectoryEntered(path)`: `FileTreeWalker` reports every directory it descends into, the start location included (`NioFileTreeWalker`'s `preVisitDirectory`); a skipped junction or linked directory is a `Skipped`, not entered. `ScanPipeline` turns it into `ScanEvent.DirectoryWalked(directoriesSoFar)`, counted from 1 across all locations.
   - `ScanEvent.FileWalked(filesSoFar, bytesSoFar)`: the existing event gains the running sum of the found files' sizes (all files walked, before the minimum-size filter).
   - `ScanEvent.HashProgress(phase, filesDone, filesTotal, bytesDone, bytesTotal)` (named `FileHashed` until decision 4): sent in `PARTIAL_HASH` and `FULL_HASH` after each file of the phase's input, including one dropped because it could not be read, so `filesDone` reaches `filesTotal` exactly. The totals are the file count and the sum of sizes of the phase's input, computed before the phase's first group and fixed for the phase. The stages report each file through an internal per-file hook from `ScanPipeline`.
   - `GroupDone` is unchanged and drives the hard-link line (sets done / total).
   - Every exhaustive switch over `ScanEvent` or `WalkEvent` gains the new cases; `ScanService` does not change `ScanSnapshot` for them (see open questions).
4. **Progress within a large file in the full-hash phase.** Without it, bytes and ETA freeze for the minutes a 40 GB file takes. `ContentHasher` gains `fullHash(Path file, LongConsumer bytesRead)`, which reports the bytes read so far in that file, at least once per 8 MiB read; a `default` implementation calls `fullHash(Path)` and reports nothing, so the existing fake hashers in tests keep working, and `NioContentHasher` overrides it from its read loop. `FullHashConfirmer` calls it, and `ScanPipeline` sends a `HashProgress` on each report, with `bytesDone` = the sizes of the files finished in the phase plus the bytes read so far in the current one, capped at the current file's scan-time size (a file that grew since the walk cannot push `bytesDone` past `bytesTotal`); `filesDone` changes only when a file finishes. When a file finishes, `bytesDone` steps to include exactly its scan-time size. Sampling (`PARTIAL_HASH`) reads at most about 128 KiB per file and reports per file only. Chosen over per-file only (stalls on the files that take time) and over showing the current file's name (the ETA would still freeze).
5. **`--progress=live|log|off`, default `live`; no auto-detection.** Java cannot portably tell whether stderr is a terminal: `Console.isTerminal()` is believed to need stdin and stdout to be one, so it would say "no" for the usual `> report.txt` (not verified: the session's shell has no terminal at all). `live` redraws the current phase's line in place with `\r` from a timer, about 5 times a second, so the elapsed clock ticks even while one file is read; when a phase ends its line is completed with a newline and stays. `log` writes a plain line every 5 seconds and one when each phase ends, for stderr redirected to a file. `off` writes no progress, only errors. A bad, empty, wrongly cased or repeated `--progress` is a usage error like `--format` (DFS-023 decision 8). Whether Gradle's `:cli:run` relays a `\r` line without a newline is not verified; the real-run done-item checks it, and if it does not, CLAUDE.md documents running the installed launcher (`./gradlew :cli:installDist`, then `cli/build/install/cli/bin/cli.bat ...`) for the live line.
6. **`ScanSnapshot` is unchanged.** `ScanService` passes the new events to the caller's listener and leaves the snapshot as it is for them; `GetScanStatus` keeps `phase, filesWalked, groupsDone, groupsTotal`. Progress reaches the CLI through the listener (CLAUDE.md, "Progress is reported through a listener"); a poller such as a future server's status page gains the new counters when it exists and its needs are known.
7. **Line formats, ETA and the final line, as proposed.** Counts and sizes use DFS-023's formatters (`3,330`, `9.8 GB`). The percentage is `bytesDone * 100 / bytesTotal` rounded down. The elapsed clock is the time since the scan started, `m:ss`, or `h:mm:ss` from one hour on. The ETA is `(bytesTotal - bytesDone) / (bytesDone / seconds since FULL_HASH started)`, formatted like the clock, and reads `--:--` until 3 seconds and 1% of the phase's bytes have passed. A phase with no input still writes its finished line (`Sampling:   0 / 0 files`). The final line, `Done in <elapsed>: <files walked> files found, <files entering PARTIAL_HASH> compared, <sets> duplicate sets`, is written in `live` and `log` modes, not in `off`. On a failed scan the current line is completed with a newline before the `error:` line, and no `Done` line is written; on Ctrl+C the JVM stops (DFS-020 decision 7) and the line stays as it was.
8. **Layout and testing details (proposed during refinement, 2026-10-13).** A line is the label padded to 12 characters (`Walking:`, `Sampling:`, `Hashing:`, `Hard links:`), the counts, two spaces, and the elapsed clock; no right alignment. The display is a plain class in `cli` fed the events and a clock (`LongSupplier` of nanoseconds), with an explicit redraw method; only `live` mode adds a daemon timer that calls it about every 200 ms, stopped when the scan ends. Tests drive the class directly with a fake clock and explicit redraws; the scanner events with fake walkers and hashers in `application` tests; `NioFileTreeWalker` and `NioContentHasher` on `@TempDir` files; and the command through `ScanCommand` as in DFS-020 and DFS-023. Hand-written fakes, no Mockito.

## Acceptance criteria

<!-- Filled during refinement. State the full ID once, then one testable statement per line, numbered from 1. Never renumber or delete: retire with `- **AC-<n> (retired)** <statement>. Retired <date>: <reason>`. -->

The full ID of each criterion is `DFS-024.AC-<n>`.

- **AC-1** `NioFileTreeWalker` emits `WalkEvent.DirectoryEntered` for every directory it descends into, the start location included, and for no skipped link or junction (decision 3).
- **AC-2** `ScanPipeline` sends `DirectoryWalked(directoriesSoFar)` counting from 1 across all locations, and `FileWalked(filesSoFar, bytesSoFar)` where `bytesSoFar` is the running sum of the walked files' sizes (decision 3).
- **AC-3** In `PARTIAL_HASH` and `FULL_HASH`, `HashProgress` carries `filesTotal` and `bytesTotal` equal to the phase input's file count and size sum, unchanged through the phase; `filesDone` rises by one per file, an unreadable file included, and the last event of the phase has `filesDone = filesTotal` and `bytesDone = bytesTotal` (decision 3).
- **AC-4** In `FULL_HASH`, bytes read within a file are reported while it is hashed: `NioContentHasher` reports at least once per 8 MiB read, and `ScanPipeline` sends `HashProgress` for each report with `filesDone` unchanged and `bytesDone` never above the finished files plus the current file's scan-time size; `ContentHasher`'s default `fullHash(Path, LongConsumer)` reports nothing and returns `fullHash(Path)` (decision 4).
- **AC-5** The new events do not change `ScanSnapshot`: a scan's snapshots have the same `phase`, `filesWalked`, `groupsDone` and `groupsTotal` as before (decision 6).
- **AC-6** The progress display renders each phase's line in the decision 7 format, from the events and a clock, with the elapsed time since the scan started (decisions 2, 7, 8).
- **AC-7** The ETA is the remaining bytes divided by the average rate since the full-hash phase began, and reads `--:--` until 3 seconds and 1% of the phase's bytes have passed (decision 7).
- **AC-8** With `--progress=live` (the default) the current line is redrawn in place with `\r`, padded to cover the previous text, also when no event arrived since the last redraw; a phase's line is completed with a newline when the next phase starts (decisions 5, 8).
- **AC-9** With `--progress=log` stderr has no `\r`: one line per phase end and at most one line every 5 seconds in between (decision 5).
- **AC-10** With `--progress=off` stderr has no progress text; errors are still written (decision 5).
- **AC-11** A completed scan ends progress with `Done in <elapsed>: <files> files found, <compared> compared, <sets> duplicate sets` (not with `off`); a failed scan completes the current line with a newline before its `error:` line and writes no `Done` line (decision 7).
- **AC-12** An unknown, empty, wrongly cased or repeated `--progress` is a usage error: message and usage line on stderr, nothing on stdout, no scan started, exit 2 (decision 5).
- **AC-13** Stdout is the same in every progress mode: the JSONL or text report only (DFS-020 decision 4).

## Scenarios

```gherkin
Feature: Scan progress

  @AC-1
  Scenario: The walker reports every directory it enters
    Given a temporary folder "root" holding "a/x.txt", "a/b/y.txt" and an empty folder "a/b/c" (created at test time)
    When NioFileTreeWalker walks "root"
    Then it emits DirectoryEntered for exactly "root", "root/a", "root/a/b" and "root/a/b/c"

  @AC-1
  Scenario: A linked directory is skipped, not entered
    Given a temporary folder "root" holding "data/x.txt" and a symbolic link "link" to "data"
    And the test is skipped when the platform or privileges do not allow creating a symbolic link
    When NioFileTreeWalker walks "root"
    Then it emits DirectoryEntered for "root" and "root/data" only
    And "root/link" is reported as Skipped

  @AC-2
  Scenario: Folders and bytes are counted across locations
    Given a fake walker that yields, for location "L1", the directory L1 and files of 100 and 200 bytes, and for location "L2", the directory L2, its subdirectory L2/s and a file of 50 bytes
    When the pipeline scans L1 and L2
    Then the DirectoryWalked events carry 1, 2, 3 in that order
    And the FileWalked events carry (1, 100), (2, 300), (3, 350)

  @AC-3
  Scenario: Hash progress totals are fixed and reach the end exactly, an unreadable file included
    Given a fake hasher, and size candidates of 3 files of 1,000 bytes each where the second cannot be read
    When the pipeline runs the partial-hash phase
    Then every HashProgress of the phase has filesTotal 3 and bytesTotal 3000
    And filesDone goes 1, 2, 3
    And the last HashProgress has bytesDone 3000

  @AC-4
  Scenario: The real hasher reports bytes within a large file
    Given a 20 MiB file generated in a temporary folder
    When NioContentHasher.fullHash is called on it with a recording callback
    Then the callback is called at least 3 times with rising values, never more than 8 MiB apart, the last being 20,971,520
    And the digest equals fullHash(Path) of the same file

  @AC-4
  Scenario: Progress within a file never passes the file's scan-time size
    Given a fake hasher that reports 4,000 and then 6,000 bytes read for a file whose scan-time size is 5,000, and a second file of 5,000 bytes
    When the pipeline runs the full-hash phase on those two files
    Then the HashProgress events carry (filesDone, bytesDone) = (0, 4000), (0, 5000), (1, 5000), then (2, 10000) after the second file

  @AC-4
  Scenario: A hasher without progress support still works
    Given a ContentHasher that implements only sampleHash and fullHash(Path)
    When fullHash(Path, callback) is called
    Then it returns fullHash(Path) and the callback is never called

  @AC-5
  Scenario: The snapshot ignores the new events
    Given a scan on a temporary folder with 2 duplicate files in a subfolder
    When the scan completes
    Then its snapshot has phase HARD_LINKS, filesWalked 2, groupsDone 1 and groupsTotal 1, as before this ticket

  @AC-6 @AC-7
  Scenario Outline: Phase lines are rendered from events and the clock
    Given the progress display with a fake clock, the scan started at 0 s
    When it receives <events> and the clock reads <now>
    Then its current line is "<line>"

    Examples:
      | events                                                                                  | now  | line                                                               |
      | DirectoryWalked(3), FileWalked(5, 5000000)                                               | 2 s  | Walking:    3 folders, 5 files, 4.8 MB found  0:02                 |
      | PhaseStarted(PARTIAL_HASH), HashProgress(PARTIAL_HASH, 2, 4, 2000, 4000)                  | 3 s  | Sampling:   2 / 4 files  0:03                                      |
      | PhaseStarted(FULL_HASH) at 10 s, HashProgress(FULL_HASH, 1, 4, 1 GiB, 4 GiB)              | 20 s | Hashing:    1 / 4 files, 1.0 GB / 4.0 GB (25%)  ETA 0:30  0:20     |
      | PhaseStarted(FULL_HASH) at 10 s, HashProgress(FULL_HASH, 0, 4, 1 GiB, 4 GiB)              | 12 s | Hashing:    0 / 4 files, 1.0 GB / 4.0 GB (25%)  ETA --:--  0:12    |
      | PhaseStarted(FULL_HASH) at 10 s, HashProgress(FULL_HASH, 0, 400, 4 MiB, 1000 GiB)         | 20 s | Hashing:    0 / 400 files, 4.0 MB / 1000.0 GB (0%)  ETA --:--  0:20 |
      | PhaseStarted(HARD_LINKS), GroupDone(HARD_LINKS, 7, 9)                                     | 3725 s | Hard links: 7 / 9 sets  1:02:05                                  |

  @AC-8
  Scenario: Live mode redraws in place, also without new events, and keeps finished lines
    Given the progress display in live mode with a fake clock
    When it receives PhaseStarted(WALK) and FileWalked(1, 10) at 0 s, then is redrawn at 1 s and 2 s without new events, then receives PhaseStarted(PARTIAL_HASH)
    Then its output has the walk line three times, each starting with "\r", the last ending "0:02"
    And a redraw of a shorter line is padded with spaces to the previous line's length
    And the walk line is completed with "\n" before the first sampling line

  @AC-9
  Scenario: Log mode writes plain lines, throttled
    Given the progress display in log mode with a fake clock
    When it receives FileWalked events every 100 ms for 12 s, then PhaseStarted(PARTIAL_HASH)
    Then its output has no "\r"
    And it has exactly 3 walk lines: one at 5 s, one at 10 s, and the finished walk line when the sampling phase starts

  @AC-10 @AC-13
  Scenario Outline: Stdout is the same in every progress mode
    Given a temporary folder holding "x.txt" and "y.txt", both with content "hello duplicate"
    When the CLI scans that folder with "--format=text" and <option>
    Then stdout is exactly the DFS-023 report: one set of 2 copies and "Summary: 1 sets, 2 files, 15 B reclaimable, 0 skipped"
    And stderr <stderr>

    Examples:
      | option             | stderr                                                         |
      | no option          | contains "\r" and ends with a "Done in " line                  |
      | "--progress=live"  | contains "\r" and ends with a "Done in " line                  |
      | "--progress=log"   | contains no "\r" and ends with a "Done in " line               |
      | "--progress=off"   | is empty                                                       |

  @AC-11
  Scenario: A completed scan ends with the Done line
    Given a temporary folder holding "x.txt" and "y.txt", both with content "hello duplicate", and "z.txt" with content "unique!"
    When the CLI scans that folder with "--progress=log"
    Then the last stderr line matches "Done in \d+:\d\d: 3 files found, 2 compared, 1 duplicate sets"

  @AC-10 @AC-11
  Scenario Outline: A failed scan finishes the line and reports the error
    Given fake ports whose scan reports PhaseStarted(WALK) and FileWalked(1, 10), then fails with "java.io.UncheckedIOException: disk gone"
    When the CLI scans a folder with <option>
    Then stderr <before> "error: scan failed: java.io.UncheckedIOException: disk gone" on a line of its own
    And there is no "Done in" line
    And the exit code is 1

    Examples:
      | option            | before                                                    |
      | "--progress=live" | has the walk line completed with "\n", then               |
      | "--progress=off"  | has only                                                  |

  @AC-12
  Scenario Outline: A bad --progress starts no scan
    When the CLI is run with arguments <args>
    Then stderr has an error naming the problem, and the usage line, which mentions "--progress=live|log|off"
    And stdout is empty
    And no scan was started
    And the exit code is 2

    Examples:
      | args                                          |
      | "<folder> --progress=quiet"                   |
      | "<folder> --progress="                        |
      | "<folder> --progress=LIVE"                    |
      | "<folder> --progress=log --progress=off"      |
```

## Scope

**Touches:**
- `domain.model`: new `ScanEvent` variants (and a `WalkEvent` for directories, if the walker reports them)
- `application`: `ScanPipeline` and the hash stages emit the new events; `ScanService` handles them
- `infra.port`: `WalkEvent`-based `FileTreeWalker` contract gains entered directories; `ContentHasher` gains `fullHash(Path, LongConsumer)` (decision 4)
- `infra.adapter`: `NioFileTreeWalker` reports entered directories; `NioContentHasher` reports bytes read
- `us.pochaev.dfs.cli`: the progress display replacing `ScanCommand.Progress`, and the `--progress` option
- `CLAUDE.md`: "Commands" and "Current state" (the progress option; the launcher if Gradle hides the live line)

**Out of scope:**
- Making the scan faster (deferred, 2026-10-13)
- New fields in `ScanSnapshot` / `GetScanStatus` (decision 6)
- Progress for the delete workflow

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] No files changed outside the paths listed under Touches
- [ ] A real scan in a Windows console shows the updating line through each phase and the `Done` line, through `./gradlew -q :cli:run` or, if Gradle holds back the `\r` line, through the installed launcher (decision 5); the user confirms what they saw
- [ ] The same real scan with `--progress=log` and stderr redirected to a file gives readable lines without `\r`
- [ ] CLAUDE.md "Commands" and "Current state" describe `--progress` (and the launcher, if needed)

## Open questions

None.
