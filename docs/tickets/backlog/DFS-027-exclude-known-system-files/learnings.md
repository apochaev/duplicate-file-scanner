## design (architect)

- The architect definition says the red gate accepts a failure "on its own only when the failure, or its root cause, is `not implemented: <ID>`", but does not say how it treats a *new* story test that fails red by assertion without reaching a stub (for example a missing-validation test where nothing throws). Because the architect may not read `tools/story-gate`, I designed defensively so every new behavior reaches a stub (stubbed the whole `ScanResult` compact constructor and `ScanConfig.defaults()`, DFS-005 style), which turns most existing scan tests red on the stub too. One sentence in `architect.md` or `archetype.md` stating the rule for new tests would let a later design stub more narrowly.
- A test class that calls a stubbed method from a `static` field initializer (`ScanServiceTest.CONFIG = ScanConfig.defaults()`) fails every test after the first with `NoClassDefFoundError`, whose root cause is no longer the stub's `UnsupportedOperationException`. The archetype's Expected breakage guidance names CLI and end-to-end tests but not this case; worth a line in `archetype.md` (or a test convention to avoid static fixtures that call domain factories).

## tests (test-author)

- `ScanServiceTest`'s static `CONFIG = ScanConfig.defaults()` made a stubbed `defaults()` kill the whole class with `NoClassDefFoundError`; turning it into a method fixed it. The architect's "expected breakage" note could say to do that in the tests phase instead of leaving it to the red gate.
