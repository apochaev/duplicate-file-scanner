# DFS-019 learnings

- **Architect: the use-case checklist mostly does not fit a wiring-only ticket.** 9 of 14 items (1, 3, 4, 5, 7, 8, 9, 10, 12) are not applicable, because a factory has no lifecycle, ID or query of its own. The items that matter here are 2, 6, 11, 13 and 14. There is also a wiring concern the checklist does not name: which instances are shared within one build, and which are fresh across builds (AC-2 and AC-3). This supports the retro question in ticket decision 1. A `wiring` archetype could ask about:
  - the shared versus fresh instance graph;
  - a null or missing executor;
  - default adapters versus overrides;
  - the exported return type.
- **Architect: no stage owns an edit to an existing guard test that has no acceptance criterion.** The ticket's `touches` include `ArchitectureTest.java` (remove `allowEmptyShould(true)`, decision 5), but the architect does not write tests, and the archetype says nothing about who changes an existing guard test. I assigned it to the test-author in `design.md` and checked locally that the change stays green. An archetype line such as "edits to existing guard tests named in `touches` belong to the test-author" would remove the guesswork.
- **Architect: the use-case archetype still has no `skeletons/`.** A data record's stub form was taken from the "where files go" rule: an empty compact constructor, with checks listed in `design.md`. An empty compact constructor holding only a comment looks odd. A skeleton would settle whether to omit the compact constructor entirely instead.
- **Test-author: the `assertThrows` rule was easy to miss because its trigger condition did not obviously match.** test-guide.md line 71 frames the ban as applying to a stub "whose precondition check runs after its `UnsupportedOperationException` (the L-013 ordering)". Here the stub has no precondition check at all, so on first read the rule seemed not to apply, and attempt 1 reasoned that the stub exception showing as the `assertThrows` cause was enough. It is not: the red gate reads only the top-level failure type. The rule's true trigger is broader: any expected-exception test against a stub (the stub always throws first). Stating it that way, and moving it out of "What not to do" into the section on writing red tests, would have avoided the rework.
- **Verifier: stub-phase notes in `design.md` go stale once the story is implemented.** Under "Open design notes", `design.md` says `ScanUseCases`'s compact constructor "is left without checks", while its Summary and Public API sections, and the code, have the `requireNonNull` checks. "Stubs: Commit: to be filled in" was also never filled in. Neither the implementer nor the orchestrator owns refreshing these lines. One fix would be for the archetype to label stub-phase notes as "(stub phase)". Another would be for the implementer to strike them through when the stub is replaced.

## Entries

### DFS-019.L-1: The assertThrows-against-stub rule is worded too narrowly

- Status: applied
- Impact: shared (an archetype guide every use-case story's test-author reads)
- Target: `.claude/archetypes/use-case/test-guide.md`
- Change: restate the rule's trigger as "any expected-exception test against a stub (the stub always throws first, so `assertThrows` reports its own `AssertionFailedError` and the red gate rejects it)", not only the L-013 ordering, and move it from "What not to do" into the section on writing red tests.
- Evidence: plan.json defect 1: tests attempt 1 failed the red gate on the AC-5 null-executor test written with `assertThrows`, and attempt 2 was escalated to opus; the test-author's note above says the stub had no precondition check, so the rule (CCW-005.L-3's wording) looked inapplicable.
- Recurrences: 1 (DFS-019)
- Recommendation: apply; one failed gate and an escalated retry for a wording problem. `pipeline-stage/test-guide.md:61` has the same wording and should get the same change (a second entry or the same apply).
- Decision: applied 2026-10-07 by the user (also applied to `pipeline-stage/test-guide.md`, version 11 → 12, at the user's request); commit `docs: apply DFS-019.L-1, DFS-019.L-2 and DFS-019.L-3`

### DFS-019.L-2: No phase owns an edit to an existing guard test named in touches

- Status: applied
- Impact: shared (`.claude/archetypes/`)
- Target: `.claude/archetypes/use-case/archetype.md`
- Change: add under "Where files go": "An edit to an existing test named in `touches` (a guard such as `ArchitectureTest`) belongs to the test-author; the architect specifies it in `design.md`."
- Evidence: DFS-019 decision 5 put `ArchitectureTest.java` in touches; the architect, who writes no tests, had to decide on its own to assign it to the test-author (design.md, plan.json design-run note).
- Recurrences: 1 (DFS-019)
- Recommendation: apply; one line, and the architect's improvised answer worked.
- Decision: applied 2026-10-07 by the user; commit `docs: apply DFS-019.L-1, DFS-019.L-2 and DFS-019.L-3`

### DFS-019.L-3: Stub-phase notes in design.md go stale after implementation

- Status: applied
- Impact: shared (`.claude/archetypes/`)
- Target: `.claude/archetypes/use-case/design.md.tmpl`
- Change: mark the "Open design notes" and "Stubs" sections "(as of the design phase)", so a later reader knows they describe the stubs, not the finished code.
- Evidence: verification.md: design.md still says `ScanUseCases`'s constructor "is left without checks" and "Commit: to be filled in" after implementation added the checks.
- Recurrences: 1 (DFS-019)
- Recommendation: apply; labelling in the template is cheaper than adding implementer work.
- Decision: applied 2026-10-07 by the user; commit `docs: apply DFS-019.L-1, DFS-019.L-2 and DFS-019.L-3`

### DFS-019.L-4: The use-case archetype has no skeleton for a data-record stub

- Status: parked
- Impact: shared (`.claude/archetypes/`)
- Target: `.claude/archetypes/use-case/skeletons/` (new)
- Change: add a record skeleton that settles whether the compact constructor is omitted or left empty with its checks listed in design.md.
- Evidence: the architect's note above: `ScanUseCases` got an empty compact constructor holding only a comment, which "looks odd".
- Recurrences: 1 (DFS-019)
- Recommendation: park; stylistic, no phase failed. Revisit when a second use-case ticket shows what skeletons it needs (archetype.md already says so).
- Decision: parked 2026-10-07 by the user

### DFS-019.L-5: Wiring-only tickets fit the use-case checklist poorly

- Status: applied
- Impact: shared (`.claude/archetypes/`)
- Target: a new `.claude/archetypes/wiring/` (a CCW ticket), or a wiring variant of the use-case checklist
- Change: a checklist for factory tickets: shared versus fresh instances per build, a null or missing executor, default adapters versus overrides, the exported return type.
- Evidence: design.md marked 9 of 14 use-case checklist items not applicable (1, 3, 4, 5, 7, 8, 9, 10, 12); the shared-versus-fresh question (AC-2, AC-3) is in no checklist; DFS-019 decision 1 left this to the retro.
- Recurrences: 2 (DFS-019: see Evidence; DFS-020: `ScannerFactories.scanUseCases` returns one `ScanService` behind all four ports, so Spring injection by type failed ("expected single matching bean but found 2"); found while implementing, after ready; each `@Bean` now wraps its port in a delegate of that interface alone, and the AC-1 scenario was reworded from object identity to shared behavior, commit 73d444f (2026-10-08))
- Recommendation: apply, through a CCW ticket for a `wiring` archetype. The second wiring ticket (DFS-020, built by hand) hit a gap this checklist would have caught: "does the factory's returned object implement more than the port it is returned as?" There are now two real examples to draw the archetype from: DFS-019 through the pipeline and DFS-020 by hand. Add that question, and "how the consumer's container (Spring) will see the returned types", to the checklist.
- Decision: parked 2026-10-07 by the user (history); re-proposed 2026-10-09 on the DFS-020 recurrence; applied 2026-10-11 by the user; commit `feat: add the wiring archetype [CCW-013, DFS-019]`; the change is the checklist of the new `wiring` archetype (`.claude/archetypes/wiring/archetype.md`, items 1-5), created at version 1 by CCW-013, so no version bump
