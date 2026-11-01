## design (architect) — 2026-11-01

- **Wiring criteria pass against stubs.** AC-14 ("the bean backs off when the app declares its own") would pass with only stubs in place, and the red gate rejects a story test that passes. The factory method and the bean can't be stubbed, because a throwing factory fails every context that loads `ScannerAutoConfiguration` in `scanner-spring` and `cli`. Worked around in design.md by telling the test-author to call `preview` through the factory or bean before the back-off assertion. The use-case archetype could say how to handle a criterion whose observable part is pure wiring (a factory method or a Spring bean).
- **Null-element NPE criteria and unchecked records.** The archetype leaves data records unchecked at design. A criterion such as "a null list element throws NPE" therefore stays red only if the test builds the record inside the `assertThrows` lambda along with the stubbed call. Worth a line in `test-guide.md`.
## verify (verifier) — 2026-11-01

- **A content-derived ID with a documented encoding has no test pinning it.** design.md fixes the plan-ID encoding (and even the empty-plan message), but no test asserts a known ID, so the encoding could drift and stay green; DFS-033 will recompute it. The verifier checked it by hand. When design.md commits to an exact encoding or format, the test-author should add one golden-value test (e.g. the empty-plan ID).

## verify after tests rework (verifier) — 2026-11-01

- **Golden test closed the gap, but only for the paths exercised.** The rework pins the empty-plan ID and a rule-sourced operation; the `pick` source and conflict encodings stay unpinned. When asking for a golden test of a variant-bearing encoding, name each variant (here rule, pick, conflict) so one round covers them all.
