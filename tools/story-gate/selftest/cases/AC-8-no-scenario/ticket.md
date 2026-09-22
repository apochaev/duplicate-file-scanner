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

  @AC-1 @AC-2
  Scenario: Sizes decide the groups
```
