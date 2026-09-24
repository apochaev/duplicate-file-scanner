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
- Discrimination: for any adapter whose job is to distinguish inputs (a hasher, a comparator, any port mapping distinct inputs to distinct outputs), at least one test that feeds it two different real inputs and asserts the outputs actually differ — not only that identical inputs produce matching outputs. A suite proving only the matching case cannot catch a constant-returning or partially-ignoring implementation (found on DFS-002: `NioContentHasher.sampleHash` reached the done gate proven only for identical bytes; a constant-returning implementation would have passed the whole suite).
- A near-duplicate, over-threshold fixture (same size, differs only past the sample boundary) for any stage whose behavior depends on file content past 128 KB — generate it in code (a helper that writes N bytes with one byte changed), never commit it.

## Testing a port method deferred to a later ticket

Some stages stub a port method that is not this ticket's job to implement (for example `ContentHasher.fullHash`, deferred by DFS-002 to DFS-003 — see `archetype.md`, "Where files go"). At the point your tests run, the implementer's permanent replacement message does not exist yet: the stub still reads the generic `not implemented: <TICKET-ID>`. Do not assert the eventual, differently-worded message — checked against the still-generic stub, that turns today's legitimate stub failure into your own `AssertionFailedError`, which `RedCheck` rejects (it requires the raw stub exception, not an assertion failure). A bare `catch (UnsupportedOperationException expected) {}` does not fix this either: it also swallows the stub's own exception and passes vacuously, before the method is ever really implemented.

Instead, catch the exception and re-throw it unchanged only while its message still starts with `not implemented:`:

```java
try {
    hasher.fullHash(file);
    fail("expected fullHash to throw UnsupportedOperationException");
} catch (UnsupportedOperationException expected) {
    if (expected.getMessage() != null && expected.getMessage().startsWith("not implemented:")) {
        throw expected;
    }
}
```

This fails red now (the generic stub message starts with `not implemented:`, so the catch rethrows it and `RedCheck` recognizes it as the stub failure), and turns green on its own once the implementer's permanent message lands (the message no longer starts with `not implemented:`, so the catch no longer rethrows). Leave the exact final wording for the verifier to confirm by eye against the ticket's Definition of done, not an automated assertion.

## What not to do

- Do not write a test that only restates the implementation ("calls method X"); assert on behavior and output.
- Do not test the port's real adapter from `application`-level tests, or vice versa.
- Do not touch a stub's production code — that is the implementer's job, done after your tests are frozen.
- Do not use `assertThrows` against a stub whose precondition check runs after its `UnsupportedOperationException` (the L-013 ordering): the type mismatch reports as `assertThrows`'s own `AssertionFailedError`, masking the stub's exception, and the red gate reads only the top-level JUnit XML failure type — so this misreports as "not a stub failure." Use `try { ...; fail(...); } catch (ExpectedType expected) {}` instead, so the stub's exception propagates uncaught.
