# pipeline-stage — implementer guide

You are making the architect's stubs pass the tests the test-author already wrote and froze. The tests do not change (the verifier checks this with `git diff`); if a test looks wrong, stop and report it as a defect instead of editing it.

## Before you start

- Read the ticket, `design.md` (this story's filled-in `design.md.tmpl`), and the failing tests. The design doc's "Checklist disposition" section is your spec for the edge cases; do not re-derive them from CLAUDE.md yourself.
- Every stub you touch currently throws `new UnsupportedOperationException("not implemented: <TICKET-ID>")`. Replace the body; do not leave the marker anywhere in production source — `StubsCheck` fails the build if `not implemented:` remains in any file under `src/main`.

## Conventions

- Records for data, sealed interfaces for results with variants (CLAUDE.md, "Code and test conventions"). No preview language features.
- Keep every layer boundary the design doc states: stage logic in `application`; an exported result in `domain.model` with its `module-info.java` `exports` line already added by the architect; a port in `infra.port`, its adapter in `infra.adapter`. Do not add a new cross-layer dependency the ArchUnit tests do not already allow — if you need one, that is a design change, report it rather than working around the check.
- Accumulator-shaped stages (checklist item 1 in `archetype.md`): keep `merge` copying the other instance's entries and leaving it unchanged, and keep the type explicitly not thread-safe (say so in its Javadoc, per DFS-001's convention) rather than adding synchronization the design doc did not ask for.
- Deterministic order and immutability (checklist items 3 and 4) are usually the easiest things to get wrong under time pressure — sort before returning, copy before storing, and return unmodifiable views, not the backing collection.
- If the ticket's Definition of done requires a measured figure (heap, timing, throughput), write it as a committed, `@Disabled` (or otherwise excluded-from-default-run) benchmark in the test tree, not a throwaway class run outside the build. The figure must be reproducible by anyone later — including the verifier — not rest on a one-off run (L-023).
- If you hit a checklist item the design doc marked "not applicable" but your implementation needs to handle anyway, that is a design gap: report it rather than silently deciding.

## When you are done

- `./gradlew build` is green, including the ArchUnit layering tests and `ModuleExportsTest`.
- No stub markers remain (`StubsCheck`).
- You have not edited a test file.
- You have not edited anything outside the ticket's `touches`.
- Leave the done-items unticked — the verifier ticks each one it has itself verified (CLAUDE.md, "Implementing a ticket").
- If you deviated from `design.md` for a reason the architect did not anticipate, add a line to `learnings.md` (D6) rather than only fixing it silently; that is how the archetype improves.
