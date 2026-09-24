---
id: CCW-009
status: in-progress
touches: []
---

## Acceptance criteria

- **AC-1** A prefixed case is checked against this ticket.

## Scenarios

```gherkin
Feature: Prefixed example

  @AC-1
  Scenario: Prefixed scenario
    Given source with no stubs
    Then the stubs check passes
```
