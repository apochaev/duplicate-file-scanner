# DFS-061 learnings

- **L-1 (architect, use-case archetype)** The archetype has no guidance for a ticket that changes an existing secondary port's signature on the scan's hot path. Every story test must be red on a stub, including the "switch off" cases (AC-3, AC-5), so the stub had to sit on the walk itself, reached by every scan: 173 existing tests went red and 7 classes had to be declared. The archetype's "leave the existing consumer untouched" (DFS-032.L-2) did not fit either: had `ScanPipeline` kept calling the old walk, AC-3's tests would have passed at red, so the one-line wiring was written at design. Worth a checklist item or a "where files go" note for port-signature changes.
- **L-2 (architect)** Adding an abstract method to a port breaks the compile of test fakes the architect may not edit (checklist item 15 only says to list them). Used here: the new method as a stub `default` on the port and the old one turned into a `default` delegating to it, so the fakes compile through design and test; the implementer makes the new method abstract. Could go into the archetype as the standard transition.
- **L-3 (test-author)** A scan through `ScanCommand` reports a stub failure as exit 1 plus `error: scan failed: ...not implemented`, so CLI-level tests fail with `AssertionFailedError`, not the stub exception; the red gate cannot be satisfied for them as written (same for every existing `ScanCommandTest` test). The core-level tests (`SystemFolderWalkerTest`, `SystemFolderEndToEndTest`) carry the pure stub-red proof.
- **L-4 (verifier)** L-3's workaround (`ScanCommandTest.sysScan`, which rethrows the stub text found in CLI output as `UnsupportedOperationException`) stays in the test tree after green as dead scaffolding, and the test freeze stops the implementer from removing it. If the red gate keeps requiring the stub's exception type for CLI-level tests, the archetype should either accept "exit 1 with the stub message" as stub-red for tests that go through `ScanCommand`, or give one shared helper, so each ticket does not leave its own copy behind.

## Entries

### DFS-061.L-5: Checklist note for port-signature changes on the scan's hot path

- Status: applied
- Impact: local
- Target: `.claude/archetypes/use-case/archetype.md` (edge-case checklist and "where files go")
- Change: add a note for a ticket that changes an existing secondary port's signature on the scan's hot path: the stub sits on the walk, the one-line consumer wiring is written at design (an exception to "leave the consumer untouched"), and the affected test classes are declared under `## Expected breakage`.
- Evidence: DFS-061 note L-1: 173 existing tests went red, 7 classes were declared, and the "leave the consumer untouched" rule (DFS-032.L-2) would have let AC-3's tests pass at red.
- Recurrences: 1
- Recommendation: apply. Any port change on the main path hits the same conflict.
- Decision: applied 2026-11-09 by the user; commit `docs: apply DFS-061.L-5, DFS-061.L-6`

### DFS-061.L-6: Standard transition for adding an abstract port method

- Status: applied
- Impact: local
- Target: `.claude/archetypes/use-case/archetype.md` (checklist item 15)
- Change: document the pattern: the new method is a stub `default` on the port, the old method becomes a `default` delegating to it, and the implementer makes the new method abstract. Also tell the architect to check each declared class's failure type and root cause (not only that the message contains the stub text) before listing Expected breakage.
- Evidence: DFS-061 note L-2; plan.json defect 1: the tests phase failed the red gate because CliApplicationTest, LaunchScriptTest, OverlapApiTest, PlanApiTest and ResultApiTest were not declared, and the test-author's 42-turn first attempt was wasted.
- Recurrences: 1 (related: DFS-055.L-12a, which only says to list fakes)
- Recommendation: apply. It would have prevented the defect 1 round trip.
- Decision: applied 2026-11-09 by the user; commit `docs: apply DFS-061.L-5, DFS-061.L-6`

### DFS-061.L-7: Shared test helper for CLI-level story tests under the red gate

- Status: applied
- Impact: local
- Target: `.claude/archetypes/use-case/test-guide.md` and a shared helper in the `cli` test tree
- Change: option (a), chosen by the user 2026-11-09: provide one shared helper that rethrows the stub text found in CLI output as `UnsupportedOperationException`, so CLI-level story tests meet the red gate without a per-ticket copy left behind after green. `RedCheck` and `tools/story-gate` stay unchanged.
- Evidence: DFS-061 notes L-3 and L-4; plan.json defect 2: 10 new ScanCommandTest story tests failed with AssertionFailedError, then `sysScan` was added and stays as dead scaffolding that the freeze stops the implementer removing.
- Recurrences: 1
- Recommendation: apply, as the smaller change and one that leaves the gate alone.
- Decision: held 2026-11-09 by the user (option 2): stays proposed until the shared helper exists in the `cli` test tree, so the guide never points at something missing. Then applied 2026-11-09 by the user; commit `docs: apply DFS-061.L-7`.
