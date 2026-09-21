---
id: DFS-001
title: Group scanned files by size
status: draft
type: feature
scope: [file-scanner]
priority: high
depends_on: []
touches: [us.pochaev.dfs.scanner.application, us.pochaev.dfs.scanner.domain.model]
blocked_reason:
created: 2026-09-21
updated: 2026-09-21
completed:
---

# DFS-001: Group scanned files by size

## Story

As a user scanning a location for duplicates
I want files grouped by size, dropping any file whose size is unique
So that only files that could possibly be identical are examined by the more expensive stages

## Context

Stage 1 of the detection pipeline (CLAUDE.md design decision 3): group by size, drop groups of one. Input is the `FileEntry` values the walker emits as `WalkEvent.FileFound`. The walker yields empty files on purpose; size filtering belongs in `application` (see "`FileTreeWalker` is built" in CLAUDE.md). Empty (0-byte) files are excluded by default and a minimum-size setting generalizes this. Test against fakes or in-memory adapters, not the real disk.

## Scenarios

```gherkin
Feature: Size grouping

  Background:
    Given default settings (empty files excluded)

  Scenario: Files with a unique size are dropped
    Given "a.txt" of 10 bytes, "b.txt" of 20 bytes and "c.txt" of 30 bytes
    When the size-grouping stage runs
    Then no candidate groups are produced

  Scenario: Files of equal size form one candidate group
    Given "a.txt" and "b.txt" of 10 bytes each and "c.txt" of 20 bytes
    When the size-grouping stage runs
    Then one candidate group of size 10 is produced containing "a.txt" and "b.txt"

  Scenario: Files are grouped across folders
    Given "folder1/a.txt" and "folder2/b.txt" of 10 bytes each
    When the size-grouping stage runs
    Then one candidate group is produced containing both paths

  Scenario: Empty files are excluded by default
    Given two files of 0 bytes
    When the size-grouping stage runs
    Then no candidate groups are produced

  Scenario: A minimum size excludes smaller files
    Given a minimum size of 100 bytes
    And two files of 99 bytes and two files of 100 bytes
    When the size-grouping stage runs
    Then one candidate group is produced containing only the two 100-byte files
```

## Scope

**Touches:**
- `us.pochaev.dfs.scanner.application`: the size-grouping stage
- `us.pochaev.dfs.scanner.domain.model`: candidate-group type and the minimum-size setting, if they live here

**Out of scope:**
- Hashing of any kind, hard-link detection
- Scan orchestration (`StartScan`, executor, progress) and any CLI work

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] No files changed outside the paths listed under Touches
- [ ] The empty-file default and the minimum-size setting are each covered by a test

## Open questions

- Scenarios are a first draft derived from CLAUDE.md only; review and complete them.
- Stage shape: does it consume walker events as a stream into a size map or take a collected list, and what type does it produce (a candidate-group record in `domain.model`, or internal to `application`)?
- Where does the minimum-size setting live? The scan config record listed under the Spring-friendliness rules does not exist yet; create it here or in DFS-005?
- Are paths sorted within a group here, or is that left to the hashing stages?
- Is an in-memory map of the whole tree acceptable for the first version?
