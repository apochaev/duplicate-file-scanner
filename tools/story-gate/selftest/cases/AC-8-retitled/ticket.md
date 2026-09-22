---
id: DFS-001
status: in-progress
touches: []
---

## Acceptance criteria

- **AC-1** Files of equal size form one candidate group.
- **AC-2** Files of different sizes never share a group.
- **AC-3** Empty files are excluded.

## Scenarios

```gherkin
Feature: Group files by size

  @AC-1
  Scenario: Equal sizes group together

  @AC-2
  Scenario: Different sizes stay apart

  @AC-3
  Scenario: Zero-byte files are ignored entirely
```
