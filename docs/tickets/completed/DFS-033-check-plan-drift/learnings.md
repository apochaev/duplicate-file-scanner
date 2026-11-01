# DFS-033 learnings

- design (architect): the use-case `design.md.tmpl` has no section for the behavior contract or for test-author guidance (how to build fixtures, which fake reaches which criterion), though the orchestrator asks for both and DFS-022/DFS-032 added a "Behavior"/"Algorithm" section and put the test guidance under "Open design notes". Consider adding a "Behavior" section and a "For the test-author" subsection to the template.
- verify (verifier): when a scenario says a second surface "checks the same plan" (here the AC-9 Spring bean), the test-author built a smaller one-set fixture in the other test class (checked 2, not the first scenario's 4), because the shared fixture helpers are private to `CheckPlanDriftTest`. Harmless here, but test-author guidance could say: when a scenario reuses another scenario's data across test classes, either reproduce it exactly or record the deviation in `test-plan.md`.
- verify (verifier): a junction test cannot tell "not followed" from "followed": a followed junction is a directory, which is NOT_A_REGULAR_FILE too. The symlink test carries the no-follow proof (same-size, same-mtime target), so designs that want a junction no-follow proof need a different observable (e.g. a recording reader asserting `isOther()`).

## Entries

### DFS-033.L-1: use-case design template lacks Behavior and test-author sections

- Status: applied
- Impact: shared (touches `.claude/archetypes/`, read by the architect on every use-case ticket)
- Target: `.claude/archetypes/use-case/design.md.tmpl`
- Change: add a "Behavior (the contract the implementer follows)" section after "Layer placement", and a "For the test-author" subsection (fixture construction, which fake or stub reaches which criterion); let "State and transitions" and "Concurrency and atomicity" be answered with one line "Not applicable: synchronous, stateless query" instead of being filled out.
- Evidence: DFS-033 learnings.md (architect note): the template has no section for the behavior contract or test guidance; `design.md` of DFS-022 (`## Algorithm (the contract the implementer follows)`), DFS-032 and DFS-033 (`## Behavior (the contract the implementer follows)`) each added one by hand, with test guidance under "Open design notes". DFS-034's architect noted the State/Concurrency sections reduce to "None" for a synchronous query.
- Recurrences: 3 (DFS-022: added an Algorithm section; DFS-032: added a Behavior section; DFS-033: added a Behavior section, note in learnings.md)
- Recommendation: apply; three stories in a row worked around the same gap, and the section names are already settled by practice.
- Decision: applied 2026-11-01 by the user; commit `docs: apply DFS-033.L-1`

### DFS-033.L-2: scenario data reused across test classes is rebuilt smaller without saying so

- Status: applied
- Impact: shared (touches `.claude/archetypes/`, read by the test-author on every use-case ticket)
- Target: `.claude/archetypes/use-case/test-guide.md`
- Change: add: when a scenario reuses another scenario's data in a different test class (for example "the bean checks the same plan"), reproduce that data exactly or record the deviation in `test-plan.md`; and note that a junction test cannot prove "not followed" by outcome alone (a followed junction is a directory, also not a regular file), so a no-follow proof needs another observable, such as a recording reader asserting `isOther()`.
- Evidence: DFS-033 learnings.md (verifier notes): the AC-9 bean test built a one-set fixture (checked 2) instead of the first scenario's plan (checked 4) because the fixture helpers are private to `CheckPlanDriftTest`; the junction test's outcome matches both followed and not followed, and the symlink test carried the no-follow proof.
- Recurrences: 2 (DFS-033: see Evidence; DFS-034: AC-10 Spring test reused DFS-033's one-entry `driftPlan` instead of the first scenario's plan (2026-11-01))
- Recommendation: apply; recurred in the next story, so the rule earns its place; also make wiring scenarios say "a plan" rather than naming one from another test class.
- Decision: applied 2026-11-01 by the user; commit `docs: apply DFS-033.L-2 and DFS-027.L-5`
