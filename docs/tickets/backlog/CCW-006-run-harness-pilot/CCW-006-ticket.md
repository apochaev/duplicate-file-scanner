---
id: CCW-006
title: Run the harness pilot
status: in-progress
type: spike
scope: [build]
archetype: none
priority: medium
depends_on: [CCW-005]
touches: [docs/tickets/backlog/CCW-006-run-harness-pilot, docs/design/story-orchestration.md]
blocked_reason:
created: 2026-09-21
updated: 2026-09-22
completed:
---

# CCW-006: Run the harness pilot

*(Formerly DFS-013; relabeled 2026-09-22 by DFS-016, which introduced the CCW namespace.)*

## Story

As the author deciding whether the harness earns its cost
I want the pipeline run against DFS-001, with a plain single-session run as a control, and then against DFS-002, with the metrics recorded
So that each component is kept or pruned on evidence, not on assumption

## Context

The pilot from D8. Every phase encodes an assumption about what the model cannot do alone, and the pilot exists to remove the assumptions that do not pay.

1. **A/B on DFS-001:** the full pipeline on one branch, and a plain single-session implementation of the same ticket on another. This answers whether the pipeline is worth it at all.
2. **The pipeline on DFS-002:** answers whether the learning loop pays. DFS-002 is still a draft with open questions, so it is refined first, using the `pipeline-stage` archetype's checklist. DFS-002 depends on DFS-001, so one A/B branch is kept and merged before it starts.

Recorded in both runs: tokens and wall-clock time per phase (tokens read by hand from each agent invocation's own result/usage report, copied into this ticket's metrics table — no `plan.json` or harness change to automate it); attempts and defects per phase and which gate caught each; the user's interventions; anything the LLM review found that the scripts missed; anything the red gate caught that a compile-only check would have missed; learnings proposed and approved; the user's verdict (acceptable as is, minor edits, or major rework).

Pruning rule: a component stays only if it caught something, or prevented rework the user can name, in at least one of the two runs; otherwise it becomes optional for that archetype. No numeric threshold, since two data points do not support one.

**A/B protocol:** the control branch runs the same model/effort tier as the pipeline's implementer (sonnet, medium — D9's default), not opus, so token and time figures are comparable rather than cheap-pipeline-vs-expensive-session. The control session sees only the ticket and `CLAUDE.md`, no `plan.json` or archetype material, matching a plain "implement this ticket" request. The quality review is best-effort blind: both diffs are read in one sitting before either is labeled pipeline or control, then the labels are revealed; this is not rigorous blinding (the user may recognize their own single-session style) and the ticket records the result as such, not as a controlled study.

**Which branch merges:** the pipeline branch always merges into `main` after the DFS-001 A/B, regardless of the quality verdict; the control branch is discarded (or kept unmerged as a reference). This holds the pilot's own starting point constant for the DFS-002 run — DFS-002 builds on the pipeline's DFS-001 output either way, so its results measure the pipeline building on its own prior work, not on foreign code. The DFS-001 verdict is still recorded and still counts toward the pruning rule.

**Environment friction:** Windows path-length errors, Gradle daemon quirks, or other platform-specific friction observed during a run are recorded as a separate note per run in the metrics table, apart from phase defects and the user's interventions — it is a platform signal, not a pipeline-component signal, and conflating the two would misattribute friction that would hit a plain session equally.

## Acceptance criteria

The full ID of each criterion is `CCW-006.AC-<n>`.

- **AC-1** Every phase of the DFS-001 pipeline run has a run record (model, effort, turns, outcome) captured in this ticket's metrics table.
- **AC-2** Token figures for every phase of both pipeline runs (DFS-001, DFS-002) are read by hand from each agent invocation's own usage report and recorded in the metrics table.
- **AC-3** The DFS-001 control run uses the same model/effort tier as the pipeline's implementer (sonnet, medium) and its session contains no pipeline artifacts (no `plan.json`, no archetype material).
- **AC-4** The DFS-001 pipeline and control runs are compared on tokens, wall-clock time, defects and the user's verdict, recorded in the metrics table.
- **AC-5** The quality review of the DFS-001 branches is best-effort blind — both diffs are read in one sitting before either is labeled pipeline or control — and the ticket records that this is not a rigorous/controlled blind.
- **AC-6** The pipeline branch merges into `main` after the DFS-001 A/B regardless of the quality verdict; the control branch is discarded or left unmerged.
- **AC-7** The DFS-002 pipeline run's attempts, defects and rework are compared against the DFS-001 pipeline run, and the ticket records whether the approved learnings made a measurable difference.
- **AC-8** Windows path-length errors or Gradle-specific friction observed during any run are recorded as a separate note per run, apart from phase defects and the user's interventions.
- **AC-9** Every pipeline component (each gate, the LLM review, the red gate, etc.) has a keep-or-optional verdict, with the evidence from at least one of the two runs that decided it.
- **AC-10** Any design decision that changes as a result of the pilot is recorded in `docs/design/story-orchestration.md`.

## Scenarios

```gherkin
Feature: Harness pilot

  @AC-1 @AC-2
  Scenario: The pipeline run is fully recorded
    Given the story pipeline is built
    When the pipeline runs on DFS-001
    Then every phase has a run record with model, effort, turns and outcome
    And the tokens for each phase are read from the agent invocation's own usage report

  @AC-3 @AC-4
  Scenario: The control run is comparable
    Given a plain single-session implementation of DFS-001 on a separate branch, with no pipeline artifacts, at the same model and effort tier as the pipeline's implementer
    When both runs are complete
    Then they are compared on tokens, time, defects and the user's verdict

  @AC-5
  Scenario: The quality review is best-effort blind
    Given both branches' diffs for DFS-001
    When their quality is judged
    Then both diffs are read unlabeled before either is identified as pipeline or control
    And the ticket records that this is best-effort, not rigorous, blinding

  @AC-6
  Scenario: The pipeline branch is the one that advances
    Given the DFS-001 A/B is complete
    When the branches are reconciled
    Then the pipeline branch merges into main and the control branch is discarded or left unmerged, regardless of the quality verdict

  @AC-7
  Scenario: The second story shows whether learning helped
    Given the pipeline run on DFS-002
    When its attempts, defects and rework are compared with the DFS-001 run
    Then the ticket records whether the approved learnings made a difference

  @AC-8
  Scenario: Environment friction is recorded separately
    Given a run on Windows
    When path-length errors or Gradle daemon quirks are observed
    Then they are recorded as a separate note per run, apart from phase defects and the user's interventions

  @AC-9
  Scenario: Each component gets a verdict
    Given the recorded metrics
    When the pruning rule is applied
    Then every component is marked keep or optional, with the evidence that decided it

  @AC-10
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

## Metrics

### DFS-001 A/B (2026-09-22)

Pipeline branch: `tickets/DFS-001-group-files-by-size`. Control branch: `pilot/DFS-001-control`, off the same starting commit (`main` at `c46c7e2`, before this ticket's own `L-021`/`L-022` apply — those postdate the pipeline run and are unrelated to DFS-001's diff).

Tokens are read from each agent invocation's own usage report (the `<usage>` block on its completion notification), per AC-2 — not estimated or taken from memory.

| Phase | Model | Effort | Turns | Wall-clock | Tokens | Outcome |
|---|---|---|---|---|---|---|
| design (architect) | opus | high | 18 | 128s | 41,724 | passed, attempt 1 |
| tests (test-author) | sonnet | medium | 27 | 205s | 65,491 | passed, attempt 1 |
| implement (implementer) | sonnet | medium | 33 | 131s | 54,708 | passed, attempt 1 |
| verify (verifier) | opus | medium | 10 | 103s | 51,041 | passed, attempt 1 |
| **Pipeline total (4 phases)** | | | **88** | **567s (9.5 min)** | **212,964** | 0 defects |
| retro (learn, separate from the above) | sonnet | low | 4 | 16s | 17,612 | 2 findings applied (L-021, L-022) |
| **Control (single session)** | sonnet | *(effort not controllable via the `Agent` tool — see caveat below)* | 26 | 236s (3.9 min) | 87,735 | passed, one pass, no retries |

**Defects:** 0 in the pipeline run (every phase passed attempt 1, `plan.json.defects` empty), 0 in the control run (single pass).

**Environment friction:** none observed in either run (no Windows path-length or Gradle daemon issues).

**Methodology caveats, recorded plainly rather than overclaiming rigor (per the ticket's own A/B protocol note):**
- The `Agent` tool exposes `model` but not `effort` for an ad-hoc subagent, so the control's model tier matches (sonnet) but its effort tier could not be explicitly pinned to "medium" the way the pipeline's per-agent frontmatter does.
- The pipeline total above counts only the four phase agents' own token/time usage — it excludes the orchestrator's (this session's) own overhead running gate checks, editing `plan.json`, and committing each phase, which is real cost the control run doesn't have an equivalent of (the control's only overhead was launching one agent and independently re-verifying its build).
- A subagent in this repo still sees the project's skill listing (`tickets`, `story`, `learn` names/descriptions) as ambient environment context — not a rigorous blind on "no pipeline artifacts," though neither session was given story-folder or archetype material directly.

**Quality (best-effort blind read of both branches' `SizeIndex`/`CandidateGroup` diffs):** the two implementations converged closely — same accumulator shape, same `Map<Long, List<FileEntry>>` internal structure, same path-collapse/dedup logic, same largest-first ordering. Differences are mostly javadoc phrasing, a named `DEFAULT_MIN_SIZE` constant (control) vs. inline literal (pipeline), and an explicit `merge(this)` early-return (both). Heap-measurement figures landed independently within 2% of each other (control ~210 bytes/entry, pipeline ~213 bytes/entry), which is external cross-validation of the same result by two independent implementations.

One substantive difference found during the blind read: the control's `merge` has no `other == this` guard — `bySize.computeIfAbsent(key,...).addAll(sameList)` would duplicate every entry if `merge(this)` were ever called (dormant; nothing in the codebase calls it, and `groups()`'s path-dedup likely masks it for identical entries, but it contradicts "merging leaves the other index unchanged" as a general contract). The pipeline's `SizeIndex` has an explicit `if (other == this) return;` guard, traceable to the design gate: the architect's `design.md` flagged "`merge(this)` is a no-op" as a checklist item 1 ("Accumulation and merging") disposition, before any code was written. Neither branch's tests exercise this case — DFS-001's own scenarios don't name it — so it was caught by the design phase's structured checklist walk, not by either test suite.

**User's verdict (2026-09-22, best-effort blind read, revealed after):** both branches acceptable as-is. Branch A = control, Branch B = pipeline.

**Evidence toward the pruning rule (AC-9):** this is one occurrence of the design phase's edge-case checklist catching a real gap the control run didn't — satisfies the pruning rule's "caught something... in at least one of the two runs" bar for keeping the design phase / checklist-driven design gate for `pipeline-stage`. Recorded here as evidence; the full per-component verdict table (AC-9) is filled in after DFS-002, per the ticket's own two-data-point framing.

**Branch reconciliation (AC-6):** the pipeline branch merged into `main` (merge commit `merge: DFS-001 group-files-by-size into main [DFS-001] [CCW-006]`), regardless of the quality verdict, per the pre-agreed protocol — build verified green on `main` afterward (`./gradlew build`, independently re-run, not just the merge agent's own claim). The control branch (`pilot/DFS-001-control`) was not merged; its work was committed to that branch alone (`feat(file-scanner): plain single-session implementation of DFS-001 (pilot control, not merged) [DFS-001] [CCW-006]`) and kept as a reference rather than discarded, so the comparison above remains checkable later.

## Definition of done

- [ ] Every scenario above is satisfied (verification is by reading this ticket's own metrics table and verdicts against each scenario — there is no automated test for a pilot's own record-keeping; `./gradlew build` staying green is a precondition of the DFS-001/DFS-002 runs themselves, not of this ticket)
- [ ] The metrics table is recorded in this ticket with real numbers
- [ ] Every component has a keep or optional verdict with its evidence
- [ ] The design log is updated with any decision that changes
- [ ] No files changed outside `touches`

## Open questions

None.
