# CCW-005 learnings

Migrated from `docs/learnings/log.md` (2026-09-23, CCW-010) — see that file for the full migration note. Numbers were global (`L-<n>`) before the move; each entry's own number below is local to this file only.

## Entries

### CCW-005.L-1: StageResult.java.tmpl skeleton contradicts architect contract and sibling skeleton

- Status: applied
- Impact: shared (an archetype skeleton every pipeline-stage story's architect reads)
- Target: `.claude/archetypes/pipeline-stage/skeletons/StageResult.java.tmpl`
- Change: drop the pre-throw `Objects.requireNonNull(members, "members")` (the same ordering issue L-013 fixed in `FilterStage.java.tmpl`, left unfixed here) and reword the architect-directed comment from instructing the architect to enforce invariants and take a defensive copy itself, to "architect: state the invariants in javadoc; implementer enforces them" — matching the actual architect/implementer contract (the architect writes stubs only, never real logic).
- Evidence: `docs/tickets/completed/CCW-005-story-orchestrator-skill/CCW-005-ticket.md`, "Demonstration evidence" — running the pipeline live against fixture ticket FIX-002, the architect found the skeleton's own comments telling it to "enforce this record's own invariants ... and take an immutable defensive copy ... before assigning the field," which contradicts the architect contract and repeats the pre-throw ordering L-013 already fixed in the sibling skeleton. Worked around manually (invariants moved to javadoc, `requireNonNull` dropped) rather than following the skeleton as written.
- Recurrences: 1 (CCW-005)
- Recommendation: apply. The architect had to manually detect and route around a documented contradiction in the skeleton during this run; fixing it removes that rediscovery cost for every future pipeline-stage ticket that uses a stage-result-shaped skeleton, and keeps the L-013 fix consistent across all of `pipeline-stage`'s skeletons rather than just `FilterStage.java.tmpl`.
- Decision: applied 2026-09-22 by the user, as recommended; commit `docs: apply L-014, L-015 and L-016` (the stub's `requireNonNull` moved to a guiding comment after the throw in `StageResult.java.tmpl`; `pipeline-stage`'s `archetype.md` `version` bumped 2 → 3).

### CCW-005.L-2: A pipeline-stage ticket should name its stage's input/output currency types

- Status: applied
- Impact: shared (the `pipeline-stage` edge-case checklist every ticket's refinement walks through)
- Target: `.claude/archetypes/pipeline-stage/archetype.md`, edge-case checklist
- Change: add a checklist item requiring a `pipeline-stage` ticket's refinement to name the stage's input and output currency types explicitly, or say the architect is free to choose them, so the architect never has to invent an unspecified type at design time.
- Evidence: `docs/tickets/completed/CCW-005-story-orchestrator-skill/CCW-005-ticket.md`, "Demonstration evidence" — fixture ticket FIX-002 named the stage class (`SmallGroupFilter`) and a member accessor (`long size()`) but not the type the stage filters, so the architect had to invent `CandidateGroup` at design time with no ticket guidance.
- Recurrences: 1 (CCW-005)
- Recommendation: apply. Low-risk clarification to the refinement checklist; likely to recur on the next `pipeline-stage` ticket refined without this fixture's pattern in mind.
- Decision: applied 2026-09-22 by the user, as recommended; commit `docs: apply L-014, L-015 and L-016` (new checklist item 14 in `archetype.md`; `version` bumped 2 → 3).

### CCW-005.L-3: test-guide.md should warn against assertThrows for L-013-style stubs

- Status: applied
- Impact: shared (an archetype guide every pipeline-stage story's test-author reads)
- Target: `.claude/archetypes/pipeline-stage/test-guide.md`
- Change: add a note that `assertThrows(ExpectedType.class, ...)` against a stub whose precondition throw comes after its `UnsupportedOperationException` (the L-013 ordering) misreports under the red gate: `assertThrows` wraps the mismatch as its own `AssertionFailedError` instead of letting the stub's exception propagate, and `RedCheck` reads only the top-level JUnit XML failure type. Recommend a `try { ...; fail(...); } catch (ExpectedType expected) {}` block instead.
- Evidence: `docs/tickets/completed/CCW-005-story-orchestrator-skill/CCW-005-ticket.md`, "Demonstration evidence" — FIX-002's test-author discovered this while writing the AC-3 null-argument test against `SmallGroupFilter`'s L-013-ordered stub, and flagged it explicitly as a general trap for any L-013-style stub.
- Recurrences: 1 (CCW-005)
- Recommendation: apply. The test-author explicitly flagged this as a general trap worth documenting, and it cost extra gate iterations to discover; a one-line addition to `test-guide.md` prevents every future pipeline-stage test-author from rediscovering it.
- Decision: applied 2026-09-22 by the user, as recommended; commit `docs: apply L-014, L-015 and L-016` (new bullet in `test-guide.md`'s "What not to do"; `archetype.md` `version` bumped 2 → 3).

### CCW-005.L-4: Live demonstration found integration bugs invisible at design/refinement time

- Status: applied
- Impact: shared (`.claude/skills/tickets/SKILL.md`, `refine` mode)
- Target: `.claude/skills/tickets/SKILL.md`, `refine` mode
- Change: at refinement, make sure the scenarios test the ticket's own purpose (its "So that") against the real state of the repository and environment it will run in, not only against a clean setup; and when the Definition of done needs a live run across already-built components, name the assumptions between them that the run must exercise (shared names, who performs a step, which status a downstream check expects).
- Evidence: `docs/tickets/completed/CCW-005-story-orchestrator-skill/CCW-005-ticket.md`, "Real bugs found only by running this for real" — three integration bugs found only by live demonstration: the `test`/`tests` phase-id spelling mismatch between `pipeline-stage/archetype.md` (CCW-002) and `FreezeCheck` (CCW-003); no agent prompt (CCW-004) included a commit step though `plan.json`/`FreezeCheck` expected one; Finishing omitted `tickets review <ID>` so `consistency` failed at the finish line. Two follow-up fix commits (`5cc00ea`, `7079a6b`) were needed after the initial build commit (`3a28e3d`).
- Recurrences: 2 (CCW-005: see Evidence; CCW-008: first version of the parallel-work feature assumed a tree always has `main` checked out, false in the repo it was built in; fixed by decision 4, AC-19 to AC-21 (2026-09-22))
- Recommendation: re-proposed on a second ticket. Apply a broader form: at refinement, turn the ticket's stated purpose and the real repo/environment state into at least one scenario, not only the seams between components.
- Decision: parked 2026-09-22 by the user, as recommended; re-proposed with CCW-008; applied 2026-09-22 by the user; commit `docs: apply L-017`.

### CCW-005.L-5: A ticket's design-time assumption about another component's capability was wrong until it was run for real

- Status: parked
- Impact: shared (`.claude/skills/tickets/SKILL.md`, `refine` mode)
- Target: `.claude/skills/tickets/SKILL.md`, `refine` mode
- Change: when refining a criterion that assumes another already-built component's capability (for example, what tools an agent has), check that component's real definition rather than carrying forward an earlier design-doc sketch.
- Evidence: `docs/tickets/completed/CCW-005-story-orchestrator-skill/CCW-005-ticket.md`'s `AC-11` changed from "the retro agent... appends proposed entries to `docs/learnings/log.md`" to "...which returns proposals in its report (it never writes)" — the ticket's own text names the cause: the design doc's D6 sketch and this ticket's own Context predate CCW-004 actually building `retro.md`, whose tool list (`Read, Grep, Glob, Bash`, no `Write`) was only discovered by launching it for real.
- Recurrences: 1 (CCW-005)
- Recommendation: park. One occurrence; the underlying cause (a design doc describing agents before they were built) is specific to this harness-bootstrapping phase and may not recur once agents are stable.
- Decision: parked 2026-09-22 by the user, as recommended.

### CCW-005.L-6: A ticket's touches list widened after it became ready

- Status: rejected
- Impact: local
- Target: none (recorded for pattern-watching only)
- Change: none proposed; watch whether a ticket's touches list needing a mid-implementation addition recurs.
- Evidence: commit `f4b4e94` set `CCW-005` `ready` with `touches: [.claude/skills/story, .claude/agents, docs/learnings]`; commit `3a28e3d` (the same commit as the initial build, the first thing done once `in-progress`) added `.claude/settings.json`, needed because the pipeline's background subagents needed pre-approved commands — not anticipated at refinement.
- Recurrences: 1 (CCW-005)
- Recommendation: reject. Single, minor, and already self-corrected within the same commit with no downstream cost; not distinct enough from ordinary scope discovery to warrant a rule.
- Decision: rejected 2026-09-22 by the user, as recommended.
