---
id: DFS-001
status: in-progress
touches: []
---

## Acceptance criteria

- **AC-1** Files of equal size form one candidate group.
- **AC-2 (retired)** Files of different sizes never share a group. Retired 2026-09-21: implied by AC-1.
- **AC-3** Empty files are excluded.

## Scenarios

```gherkin
Feature: Group files by size

  @AC-1
  Scenario: Equal sizes group together

  @AC-3
  Scenario: Empty files are dropped
```
