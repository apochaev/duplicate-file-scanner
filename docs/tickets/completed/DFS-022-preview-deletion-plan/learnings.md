## design (architect) — 2026-11-01

- **Wiring criteria pass against stubs.** AC-14 ("the bean backs off when the app declares its own") would pass with only stubs in place, and the red gate rejects a story test that passes. The factory method and the bean can't be stubbed, because a throwing factory fails every context that loads `ScannerAutoConfiguration` in `scanner-spring` and `cli`. Worked around in design.md by telling the test-author to call `preview` through the factory or bean before the back-off assertion. The use-case archetype could say how to handle a criterion whose observable part is pure wiring (a factory method or a Spring bean).
- **Null-element NPE criteria and unchecked records.** The archetype leaves data records unchecked at design. A criterion such as "a null list element throws NPE" therefore stays red only if the test builds the record inside the `assertThrows` lambda along with the stubbed call. Worth a line in `test-guide.md`.
## verify (verifier) — 2026-11-01

- **A content-derived ID with a documented encoding has no test pinning it.** design.md fixes the plan-ID encoding (and even the empty-plan message), but no test asserts a known ID, so the encoding could drift and stay green; DFS-033 will recompute it. The verifier checked it by hand. When design.md commits to an exact encoding or format, the test-author should add one golden-value test (e.g. the empty-plan ID).

## verify after tests rework (verifier) — 2026-11-01

- **Golden test closed the gap, but only for the paths exercised.** The rework pins the empty-plan ID and a rule-sourced operation; the `pick` source and conflict encodings stay unpinned. When asking for a golden test of a variant-bearing encoding, name each variant (here rule, pick, conflict) so one round covers them all.

## Entries

### DFS-022.L-1: use-case archetype lacks the "wiring criterion must still reach a stub" rule

- Status: applied
- Impact: local
- Target: `.claude/archetypes/use-case/archetype.md` ("Where files go") and `.claude/archetypes/use-case/test-guide.md`
- Change: say that a factory method or Spring bean exposing the use case is written for real at design (a throwing factory breaks every context loading the auto-configuration), and that its story test calls the use case through the factory or bean before any wiring assertion (back-off, bean count), so it fails red on the stub; mirrors DFS-031.L-1 in the wiring guide.
- Evidence: DFS-022 design note "Wiring criteria pass against stubs" (AC-14 back-off would pass with stubs; worked around in design.md, design commit af6ade4); repeated in DFS-032's design (AC-9, same workaround).
- Recurrences: 2 (DFS-022: AC-14 back-off test; DFS-032: AC-9 handled the same way)
- Recommendation: apply, two consecutive use-case tickets needed the same hand workaround.
- Decision: applied 2026-11-01 by the user; commit `docs: apply DFS-022.L-1, L-2, L-3, DFS-032.L-1, L-2, L-3, L-4`

### DFS-022.L-2: null-element tests must build the unchecked record inside the try block

- Status: applied
- Impact: local
- Target: `.claude/archetypes/use-case/test-guide.md` (the expected-exception bullet)
- Change: add: data records have no checks at design, so a "null element throws NPE" test builds the record inside the try block together with the stubbed call; built outside, the record's later check never runs at red and the call hits the stub, but at green the NPE is thrown before the try, failing the test.
- Evidence: DFS-022 design note "Null-element NPE criteria and unchecked records"; the test-author followed it (AC-12, test commit a657d69); DFS-032's architect repeated the instruction in its design.
- Recurrences: 2 (DFS-022: AC-12 tests; DFS-032: its architect repeated the instruction in design.md)
- Recommendation: apply, a one-sentence extension of the already-applied DFS-019.L-1 rule.
- Decision: applied 2026-11-01 by the user; commit `docs: apply DFS-022.L-1, L-2, L-3, DFS-032.L-1, L-2, L-3, L-4`

### DFS-022.L-3: a committed exact encoding needs golden-value tests, one per variant

- Status: applied
- Impact: local
- Target: `.claude/archetypes/use-case/test-guide.md` ("Cover, per scenario")
- Change: when design.md commits to an exact ID, hash or format, the test-author adds golden-value tests whose expected value is built independently of the code (a literal, or the encoding assembled by hand), one per variant the encoding has (here the empty plan, a rule source, a pick source, a conflict).
- Evidence: DFS-022 verification Known limits ("no test pins the exact plan-ID encoding"); the user sent the tests back at the done gate (plan.json tests run 2, commit 59b6a4b), costing a rework and a re-verify; the re-verify still found the pick and conflict encodings unpinned.
- Recurrences: 1 (DFS-022)
- Recommendation: apply, the gap reached the user's gate instead of being caught by the pipeline.
- Decision: applied 2026-11-01 by the user; commit `docs: apply DFS-022.L-1, L-2, L-3, DFS-032.L-1, L-2, L-3, L-4`

### DFS-022.L-4: a tests rework replaces the phase's artifacts, so freeze and disabled stop guarding the earlier test files

- Status: proposed
- Impact: shared (touches a skill: the story skill's rework and commit steps)
- Target: `.claude/skills/story/SKILL.md` (`approve <ID> [feedback]`, and resume step 8)
- Change: when a reopened phase is committed again, set its artifacts to the union of the earlier and the new commit's files (or keep a list of commits), so freeze and disabled still judge every test file the phase ever wrote.
- Evidence: DFS-022 plan.json: after the rework (commit 59b6a4b) the tests phase lists only PreviewDeletionPlanServiceTest.java; the implement re-check printed "disabled ... in the 1 test files of the tests phase" and "freeze ... none of the 1 test files", where the first run guarded 3 (PreviewDeletionPlanFactoryTest and ScannerAutoConfigurationTest dropped out).
- Recurrences: 1 (DFS-022)
- Recommendation: apply, a quiet loss of a deterministic guard.
- Decision:
