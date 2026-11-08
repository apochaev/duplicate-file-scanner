---
id: CCW-030
title: Decide the verifier's model by the risk of the ticket
status: draft
type: chore
scope: [docs]
archetype:
priority: low
lane: harness
depends_on: []
touches: []
blocked_reason:
created: 2026-11-07
updated: 2026-11-07
completed:
---

# CCW-030: Decide the verifier's model by the risk of the ticket

## Story

As the user paying for every verification
I want the verifier to run on a cheaper model where the ticket's risk allows it
So that review keeps catching defects on risky tickets without paying the top model's price on every ticket

## Context

Filed 2026-11-07 by dfs-harness after closing CCW-024, at the user's request to log tickets that further reduce cost.

- **What exists.** `.claude/agents/verifier.md`: `model: opus`, `effort: medium`, `maxTurns: 30`; every archetype's `models:` repeats `verifier: {model: opus, ...}` (`maxTurns` 30 for component, endpoint and pipeline-stage, 20 for use-case and wiring). The test-author and implementer run on Sonnet.
- **Measured** (subagent transcripts since 2026-10-25): the verifier is 22% of subagent cost (19 runs, 337 requests, all Opus; about 22M cache-read tokens), in input-equivalent tokens before the price difference between models, which makes its real share larger.
- **Why it might stay on Opus:** the verifier is the last check before the product-owner review, and CLAUDE.md's safety invariants (never delete the last copy, re-verify at execution time) are judged there. A ticket's `models` field can already override per ticket.
- **Related:** DFS-057's verifier hit its 30-turn cap partly building a browser driver; CCW-027 addresses that part.
- Options for refinement: Sonnet by default with Opus set in refinement for tickets that plan or execute deletion (the same rule as keeping the design gate); Sonnet for frontend-only `component` tickets; or keep Opus and measure nothing.

## Acceptance criteria

<!-- Filled during refinement. -->

The full ID of each criterion is `CCW-030.AC-<n>`.

- **AC-1** <one testable statement>

## Scenarios

```gherkin
Feature: The verifier's model follows the ticket's risk

  @AC-1
  Scenario: <a frontend component ticket is verified on the cheaper model>
    Given <concrete state>
    When <action>
    Then <observable outcome>
```

## Scope

**Touches:**
- <likely `.claude/agents/verifier.md`, the archetypes' `models:`, and the tickets skill's refine step>

**Out of scope:**
- The architect's model.

## Definition of done

- [ ] Every scenario above is checked (mechanically or by reading, as recorded per scenario)
- [ ] `tools/story-gate/story-gate selftest` passes
- [ ] No files changed outside the paths listed under Touches
- [ ] One verification run on the cheaper model is compared with a recent Opus run of the same archetype (defects found, requests, tokens)

## Open questions

1. Which tickets keep Opus: deletion-related only, every backend ticket, or chosen in refinement each time?
2. How do we know a cheaper verifier still catches defects: compare against past `verification.md` findings, or run both once on one ticket?
