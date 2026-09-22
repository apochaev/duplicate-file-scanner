# pipeline-stage — test-author guide

You write every test for this stage against the architect's stubs, before the implementer writes any real logic. Every story test must fail against the stubs for exactly one reason: the stub's `UnsupportedOperationException("not implemented: <TICKET-ID>")`. That is the red gate (D2); a test that passes against a stub, or fails for another reason, is a vacuous or broken test and is sent back to you.

## Naming convention (D11)

Every test's `@DisplayName` starts with the full ID(s) of the criteria it proves, then a readable title:

```java
@Test
@DisplayName("DFS-001.AC-3 Files of equal size form one candidate group")
void filesOfEqualSizeFormOneGroup() { ... }
```

A test that proves more than one criterion lists every ID, comma-separated, at the very start: `DFS-001.AC-1, DFS-001.AC-2 <title>`. Nothing may come before the ID(s) — `TraceabilityCheck` reads the run of IDs at the very start of the display name and stops at the first token that is not `<TICKET-ID>.AC-<n>`. Fill the same mapping into `test-plan.md` (from `test-plan.md.tmpl`) as you write each test; the traceability gate cross-checks both.

## What to test against

- The stage's own logic (`application`): a fake or in-memory implementation of any port it depends on (for example a `ContentHasher` fake returning fixed digests for fixed paths), never the real filesystem. Follow CLAUDE.md's fixture rules if you use the shared fixtures (`Fixtures.copyInto` into a `@TempDir`).
- A new adapter (`infra.adapter`): real temporary files (`@TempDir`), because that is the one layer whose job is to talk to the real filesystem.

## Cover, per scenario in the ticket

- The happy path and the stated edge cases, exactly as scenarios describe them — do not invent new scenarios; if you find a gap, report it rather than silently adding one (the ticket's scenarios are what was reviewed and approved at `ready`).
- Whatever the design doc's checklist disposition commits to: if it says "merge leaves the other instance unchanged", write that test; if it says "not applicable: this stage has nothing to merge", do not invent one.
- Determinism: same input in a different order (or arrival order, for an accumulating stage) produces the same output.
- Immutability: a value returned by the stage is unaffected by later mutation of the stage's internal state.
- Invalid input: whatever the design doc's public API section says is rejected (a null, a negative size, a construction invariant) has a test that expects rejection.
- A near-duplicate, over-threshold fixture (same size, differs only past the sample boundary) for any stage whose behavior depends on file content past 128 KB — generate it in code (a helper that writes N bytes with one byte changed), never commit it.

## What not to do

- Do not write a test that only restates the implementation ("calls method X"); assert on behavior and output.
- Do not test the port's real adapter from `application`-level tests, or vice versa.
- Do not touch a stub's production code — that is the implementer's job, done after your tests are frozen.
