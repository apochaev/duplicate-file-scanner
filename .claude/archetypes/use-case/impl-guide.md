# use-case — implementer guide

You are making the architect's stubs pass the tests the test-author already wrote and froze. The tests do not change (the verifier checks this with `git diff`); if a test looks wrong, stop and report it as a defect instead of editing it.

## Before you start

- Read the ticket, `design.md` (this story's filled-in `design.md.tmpl`), and the failing tests. The design doc's "State and transitions", "Concurrency and atomicity" and "Checklist disposition" sections are your spec; do not re-derive them from CLAUDE.md yourself.
- Every stub you touch currently throws `new UnsupportedOperationException("not implemented: <TICKET-ID>")`. Replace the body; do not leave the marker anywhere in production source — `StubsCheck` fails the build if `not implemented:` remains in any file under `src/main`.

## Conventions

- Records for data, sealed interfaces for results with variants (CLAUDE.md, "Code and test conventions"). No preview language features (`StructuredTaskScope` is still preview).
- Keep every layer boundary the design doc states: use-case ports in `domain.usecases`, the types they expose in `domain.model`, the service in `application`, a state port in `infra.port` and its adapter in `infra.adapter`. Do not add a new cross-layer dependency the ArchUnit tests do not already allow — if you need one, that is a design change, report it rather than working around the check.
- No per-request state in a field of the service: it holds only its ports, its seams and the executor (checklist item 6). Anything per request goes through the state port.
- Every state transition is one atomic update that reads the current state and decides inside that update (checklist item 4). Never read the state in one call and write it in another: that is the race the design settled.
- Translate expected failures into `Failure` values at the use-case boundary, exactly as the design doc lists them; let a `null` argument throw `NullPointerException` (checklist item 2).
- Work that runs on the executor ends in a terminal state on every path, including an `Error` (a `finally`), and records its failure where the design doc says (checklist item 9). The core never prints.
- Isolate caller-supplied callbacks as the design doc says (checklist item 8): a throwing listener must not take the work down with it unless the design says so.
- If the ticket's Definition of done requires a measured figure (heap, timing, throughput), write it as a committed, `@Disabled` (or otherwise excluded-from-default-run) benchmark in the test tree, not a throwaway class run outside the build. The figure must be reproducible by anyone later — including the verifier — not rest on a one-off run (L-023).
- If you hit a checklist item the design doc marked "not applicable" but your implementation needs to handle anyway, that is a design gap: report it rather than silently deciding.

## When you are done

- `./gradlew build` is green, including the ArchUnit layering tests and `ModuleExportsTest`.
- No stub markers remain (`StubsCheck`).
- You have not edited a test file.
- You have not edited anything outside the ticket's `touches`.
- Leave the done-items unticked — the verifier ticks each one it has itself verified (CLAUDE.md, "Implementing a ticket").
- If you deviated from `design.md` for a reason the architect did not anticipate, add a line to `learnings.md` (D6) rather than only fixing it silently; that is how the archetype improves.
