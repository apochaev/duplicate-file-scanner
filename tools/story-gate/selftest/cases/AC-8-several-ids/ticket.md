---
id: DFS-001
status: in-progress
touches: []
---

## Acceptance criteria

- **AC-1** Files of equal size form one candidate group.
- **AC-2** Files of different sizes never share a group.
- **AC-3** Empty files are excluded.
- **AC-10** Sizes are compared exactly.

## Scenarios

```gherkin
Feature: Group files by size

  @AC-1
  Scenario: Equal sizes group together

  @AC-2 @AC-3
  Scenario: Sizes and empty files

  @AC-10
  Scenario: Sizes are exact
```
