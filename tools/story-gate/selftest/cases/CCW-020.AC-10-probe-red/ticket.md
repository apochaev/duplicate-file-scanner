---
id: PROBE-001
title: Throwaway ticket for CCW-020's gate run
status: in-progress
archetype: component
lane: frontend
---

# PROBE-001: Throwaway ticket for CCW-020's gate run

Never merged: the branch is deleted after the run (CCW-020.AC-10).

## Acceptance criteria

The full ID of each criterion is `PROBE-001.AC-<n>`.

- **AC-1** The label shows the name the template renders.
- **AC-2** Pressing the button shows "pressed".
- **AC-3** Once the session loads, the page shows "loaded".

## Scenarios

```gherkin
Feature: Probe

  @AC-1
  Scenario: The template renders the label
    When the label is shown
    Then it reads "probe"

  @AC-2
  Scenario: The button reacts
    When Press is clicked
    Then the page shows "pressed"

  @AC-3
  Scenario: The session loads
    When /api/session answers
    Then the page shows "loaded"
```
