---
id: CCW-003
status: in-progress
touches: []
---

## Acceptance criteria

- **AC-1** The example check reports a pass.
- **AC-2** The example check reports a failure.

## Scenarios

```gherkin
Feature: Example

  @AC-1
  Scenario: Example scenario
    Given source with no stubs
    Then the stubs check passes

  @AC-2
  Scenario: A scenario nobody wrote a case for
    Given source with a stub
    Then the stubs check fails

  @AC-2
  Scenario: A scenario claimed by a case named after another criterion
    Given anything
    Then nothing
```
