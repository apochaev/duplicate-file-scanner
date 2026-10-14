---
id: DFS-026
title: Detect hard links in linear time per duplicate set
status: draft
type: fix
scope: [file-scanner, cli]
archetype:
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
priority: high
depends_on: [DFS-024]
touches: []
blocked_reason:
created: 2026-10-13
updated: 2026-10-13
completed:
---

# DFS-026: Detect hard links in linear time per duplicate set

## Story

As a user scanning large media drives
I want the hard-link phase to finish in time proportional to the number of files
So that a scan with large sets of identical files does not stall for hours after hashing is done

## Context

`HardLinkDetector.regroup` (DFS-004, DFS-017) takes the files of a set in path order and checks each with `FileIdentity.isSameFile(file, firstLink)` against the first link of every copy formed so far. When the files are ordinary copies (no hard links, the usual case), every file starts a new copy, so a set of n files costs about n²/2 calls, and `NioFileIdentity` (`Files.isSameFile`) opens both files on every call on Windows. The pairwise port exists because `BasicFileAttributes.fileKey()` is null on Windows (CLAUDE.md, "Hard links are not duplicates").

Observed on 2026-10-13 (`C:/Media D:/Media E:/Media F:/Media`, 7.3 TB, 700,488 files, 11,590 duplicate sets): hashing finished at 3:00:29; the `Hard links:` line reached `7,163 / 11,590 sets` by 3:02:31 and then stopped moving for at least 5 minutes. A thread dump showed the scan inside `HardLinkDetector.regroup` → `Files.isSameFile`, and the process was making about 323,000 file-system operations per second with no data reads: one very large set of identical files.

The progress line shows nothing during such a set, because `GroupDone` is sent only per finished set (DFS-024), so the scan looks frozen.

A likely fix: read each file's identity once (volume serial number and file index, plus the link count) and group by it. On Windows this needs `GetFileInformationByHandle` through the FFM API (final in Java 25), since NIO does not expose the file index; a file whose link count is 1 cannot be a hard link of anything, so most files need no grouping at all.

## Acceptance criteria

The full ID of each criterion is `DFS-026.AC-<n>`.

- **AC-1** <to be written during refinement>

## Scenarios

```gherkin
Feature: Linear-time hard-link detection

  @AC-1
  Scenario: <a set of many identical files, none hard-linked>
    Given <concrete state>
    When <action>
    Then <observable outcome>
```

## Scope

**Touches:**
- `infra.port` (`FileIdentity`): <to confirm: a key per path instead of, or besides, the pairwise check>
- `infra.adapter` (`NioFileIdentity`, possibly an FFM-based Windows adapter): <to confirm>
- `application` (`HardLinkDetector`): <to confirm>
- `us.pochaev.dfs.cli` (`ProgressDisplay`): <only if progress within a set is in scope>

**Out of scope:**
- Hashing and sampling speed (the user has deferred speed work).
- The ETA and percentage of the `Hashing:` line (DFS-025).

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] No files changed outside the paths listed under Touches
- [ ] <ticket-specific check, e.g. the Media scan's hard-link phase finishes in seconds to minutes>

## Open questions

1. The identity source: (a) FFM `GetFileInformationByHandle` on Windows (file index + link count), with `fileKey()` on platforms that have it; (b) keep `isSameFile` but compare only files whose link count is above 1 (still needs the link count, so FFM on Windows); (c) another approach. Trade-offs: native code in a Spring-free core and how it is tested; the `--enable-native-access` warning.
2. The port shape: replace `isSameFile` with a per-path key, or add the key next to it.
3. Whether the `Hard links:` line should show progress inside a large set (files rather than sets), and if so whether that belongs here or in its own ticket.
4. Archetype: `none` or a template set.
