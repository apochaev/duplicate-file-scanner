---
id: DFS-001
title: Group files by size
status: in-progress
touches: [us.pochaev.dfs.scanner.application]
---

# DFS-001: Group files by size

## Acceptance criteria

The full ID of each criterion is `DFS-001.AC-<n>`.

- **AC-1** Files of equal size form one candidate group.
- **AC-2** Files of different sizes never share a group.
- **AC-3** Empty files are excluded.

## Scenarios

```gherkin
Feature: Group files by size

  @AC-1
  Scenario: Equal sizes group together
    Given two files of 10 bytes
    Then they share a group

  @AC-2
  Scenario: Different sizes stay apart
    Given files of 10 and 11 bytes
    Then they are in different groups

  @AC-3
  Scenario: Empty files are dropped
    Given a 0-byte file
    Then it is in no group
```
