---
id: DFS-013
title: Run the harness pilot
status: draft
type: spike
scope: [build]
priority: medium
depends_on: [DFS-012]
touches: [docs/tickets/backlog/DFS-013-run-harness-pilot, docs/design/story-orchestration.md]
blocked_reason:
created: 2026-09-21
updated: 2026-09-21
completed:
---

# DFS-013: Run the harness pilot

## Story

As the author deciding whether the harness earns its cost
I want the pipeline run against DFS-001, with a plain single-session run as a control, and then against DFS-002, with the metrics recorded
So that each component is kept or pruned on evidence, not on assumption

## Context

The pilot from D8. Every phase encodes an assumption about what the model cannot do alone, and the pilot exists to remove the assumptions that do not pay.

1. **A/B on DFS-001:** the full pipeline on one branch, and a plain single-session implementation of the same ticket on another. This answers whether the pipeline is worth it at all.
2. **The pipeline on DFS-002:** answers whether the learning loop pays. DFS-002 is still a draft with open questions, so it is refined first, using the `pipeline-stage` archetype's checklist. DFS-002 depends on DFS-001, so one A/B branch is kept and merged before it starts.

Recorded in both runs: tokens and wall-clock time per phase (the source of token figures is to be confirmed); attempts and defects per phase and which gate caught each; the user's interventions; anything the LLM review found that the scripts missed; anything the red gate caught that a compile-only check would have missed; learnings proposed and approved; the user's verdict (acceptable as is, minor edits, or major rework).

Pruning rule: a component stays only if it caught something, or prevented rework the user can name, in at least one of the two runs; otherwise it becomes optional for that archetype. No numeric threshold, since two data points do not support one.

## Scenarios

```gherkin
Feature: Harness pilot

  Scenario: The pipeline run is fully recorded
    Given the story pipeline is built
    When the pipeline runs on DFS-001
    Then every phase has a run record with model, effort, turns and outcome

  Scenario: The control run is comparable
    Given a plain single-session implementation of DFS-001 on a separate branch, with no pipeline artifacts
    When both runs are complete
    Then they are compared on tokens, time, defects and the user's verdict

  Scenario: The second story shows whether learning helped
    Given the pipeline run on DFS-002
    When its attempts, defects and rework are compared with the DFS-001 run
    Then the ticket records whether the approved learnings made a difference

  Scenario: Each component gets a verdict
    Given the recorded metrics
    When the pruning rule is applied
    Then every component is marked keep or optional, with the evidence that decided it

  Scenario: The design log reflects the outcome
    Given the verdicts
    When the pilot is closed
    Then any decision that changes is recorded in `docs/design/story-orchestration.md`
```

## Scope

**Touches:**
- This ticket's folder: the metrics table and the verdicts
- `docs/design/story-orchestration.md`: decisions that change

**Out of scope:**
- The implementation of DFS-001 and DFS-002 themselves (their own tickets; the pilot only runs them)
- Changing the harness beyond recording what should change

## Definition of done

- [ ] Every scenario above is satisfied
- [ ] The metrics table is recorded in this ticket with real numbers
- [ ] Every component has a keep or optional verdict with its evidence
- [ ] The design log is updated with any decision that changes

## Open questions

- Scenarios are a first draft derived from the design log only; review and complete them.
- Where do token figures come from (session usage, the run records, something else)?
- A/B protocol: the same model and effort in both branches? How is the control kept honest (no ticket pipeline artifacts)? Is the quality review blind?
- Which branch is merged, given DFS-002 depends on DFS-001 being done?
- Pre-conditions: DFS-002 refined to `ready`, its open questions (sample sizes, `ContentHasher` signature, failure handling) settled.
- Windows path-length and Gradle behaviour observed during the runs: how are they recorded?
