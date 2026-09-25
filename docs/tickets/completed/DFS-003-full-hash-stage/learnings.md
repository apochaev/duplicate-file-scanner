# DFS-003 learnings

- (architect, design) The `FilterStage.java.tmpl` skeleton assumes a stage that filters one currency into itself (`filter(List<In>) : List<Result>`, "dropping members that no longer match"). A terminal stage that converts currency (`CandidateGroup` in, exported `DuplicateSet` out) fits only loosely; I named it `FullHashConfirmer.confirm` and recorded the deviation in `design.md`. The archetype could say the method name is free when the stage changes currency.
- (architect, design) `archetype.md`'s "deferred port method" rule tells the deferring ticket's implementer to swap in a permanent non-`not implemented:` message, but not that the *implementing* ticket's architect must re-stub it back to `not implemented: <ID>`, nor that the deferring ticket's stand-in test then has to be retired by the implementing ticket's test-author. DFS-003's refinement found this by hand (CCW-005.L-5); the archetype and `test-guide.md` could state both steps so the next deferred method does not rediscover them.

## Entries

### DFS-003.L-1: The FilterStage skeleton assumes a same-type filter; a stage that changes type renames its method

- Status: parked
- Impact: shared (the `pipeline-stage` archetype every architect reads)
- Target: `.claude/archetypes/pipeline-stage/FilterStage.java.tmpl` and `archetype.md`, "Where files go"
- Change: say that when a stage consumes one type and produces another (for example `CandidateGroup` in, `DuplicateSet` out), the method is not a same-type filter and the architect names it for what it does (for example `confirm`), recording the choice in `design.md`.
- Evidence: DFS-003, `learnings.md` architect note and `design.md` "Open design notes": the stage was named `FullHashConfirmer.confirm` instead of the skeleton's `filter`, recorded as a deviation. No gate failed and no rework followed.
- Recurrences: 1 (DFS-003)
- Recommendation: park. One occurrence, and it cost nothing: the architect handled it correctly on its own and recorded it. Worth applying if a second stage that changes type hits it; DFS-004 (hard links) is a likely candidate.
- Decision: parked 2026-09-25 by the user, as recommended.

### DFS-003.L-2: The deferred-port convention does not say what the implementing ticket must do

- Status: applied
- Impact: shared (the `pipeline-stage` archetype and its test guide, read by every architect and test-author)
- Target: `.claude/archetypes/pipeline-stage/archetype.md`, "Where files go" (the deferred-port bullet) and `test-guide.md`, "Testing a port method deferred to a later ticket"
- Change: complete the convention DFS-002.L-1 started with the implementing ticket's two steps: its architect re-stubs the deferred method back to `not implemented: <ID>` (and names the deferring ticket's test file in `design.md`, since the test-author reads only files the design names); its test-author removes the deferring ticket's stand-in test, which otherwise fails `RedCheck` as a regression and later fails `green`.
- Evidence: DFS-003, architect note in `learnings.md`; the ticket's Context decision "DFS-003's test-author deletes DFS-002's `fullHash` stand-in test", found at refinement only by reading `NioContentHasherTest` and `tools/story-gate/RedCheck.java` (CCW-005.L-5). Both steps were then done correctly (`design.md` "Open design notes"; commit `519bbd3`), so no gate failed on this. Origin: DFS-002.L-1 documented only the deferring ticket's side.
- Recurrences: 1 (DFS-003)
- Recommendation: apply. It completes a convention that is already half documented, so the next deferred method does not depend on refinement rediscovering it. If refinement misses it, the pipeline blocks twice (a red-gate regression, then a green failure).
- Decision: applied 2026-09-25 by the user; commit `docs: apply DFS-003.L-2 and DFS-003.L-3`.

### DFS-003.L-3: A criterion stated only as something being absent cannot be traced to a test

- Status: applied
- Impact: shared (`.claude/skills/tickets/SKILL.md`, the `ready` gate every refinement checks)
- Target: `.claude/skills/tickets/SKILL.md`, "Acceptance criteria"
- Change: add that every criterion must be observable by a test that can carry its ID; a criterion about removing or retiring something (a test, a stub, a behavior) is stated as the behavior the removal enables (for example "`fullHash` returns a digest instead of throwing"), not as the absence itself.
- Evidence: DFS-003, `plan.json` defect 1: traceability failed with "AC-9: no test display name starts with DFS-003.AC-9". AC-9 as refined read "No test asserts that `fullHash` throws: DFS-002's stand-in test ... is removed"; the tests phase's first attempt treated it as proven by the deletion, and a second attempt, escalated to opus, added `DFS-003.AC-9 fullHash returns a digest instead of throwing` (commit `519bbd3`).
- Recurrences: 1 (DFS-003)
- Recommendation: apply. One sentence in the `ready` gate; it cost a failed gate and an escalated retry, and refinement is where it is cheapest to catch.
- Decision: applied 2026-09-25 by the user; commit `docs: apply DFS-003.L-2 and DFS-003.L-3`.
