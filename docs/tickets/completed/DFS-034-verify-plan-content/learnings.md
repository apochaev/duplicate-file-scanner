## design (architect, attempt 1)

- use-case `design.md.tmpl`'s "State and transitions" and "Concurrency and atomicity" sections presume an asynchronous lifecycle; for a synchronous use case (DFS-034, like DFS-032/033) they collapse to "None" plus a thread note. A one-line hint in the template ("synchronous use case: say so and skip") would save the architect deliberating over it.
- The use-case archetype still has no `skeletons/`, although DFS-022, DFS-032, DFS-033 and DFS-034 now give a stable shape for a stateless synchronous query (port in `domain.usecases.query`, service with ports-only fields, factory method, `@ConditionalOnMissingBean` bean straight from the factory). Candidate for a skeleton set.

## verify (verifier, attempt 1)

- Chunked comparators that check `cancelled` at the top of the loop and detect end-of-file only by a short read see the cancel before EOF when the file size is an exact multiple of the chunk, so a fully compared entry can be dropped as "interrupted" (`NioContentComparator`, 3 MiB = 3 chunks). Harmless here, but a test-author could pin the intended semantics with a case that cancels in the last chunk's progress callback; the implementer could check `position == size` before the cancel.
- The AC-10 Spring test reused DFS-033's one-entry `driftPlan` instead of "the plan of the first scenario"; fine for wiring, but scenario wording that names a specific plan in another test class invites this drift. Saying "a plan" for wiring scenarios would match what is practical.

## Entries

### DFS-034.L-1: use-case archetype has no skeletons for the stateless synchronous query

- Status: proposed
- Impact: shared (touches `.claude/archetypes/`, read by the architect on every use-case ticket)
- Target: `.claude/archetypes/use-case/skeletons/` (new)
- Change: add a skeleton set for a stateless synchronous query: the port in `domain.usecases.query`, a service in `application` whose fields are ports only, the `ScannerFactories` method, and the `@ConditionalOnMissingBean` bean that calls it; update `archetype.md`'s "It has no `skeletons/` yet" line.
- Evidence: DFS-034 learnings.md (architect note): DFS-022, DFS-032, DFS-033 and DFS-034 share this shape; `archetype.md` says "a second use-case ticket will show what they should look like". Only `pipeline-stage` and `wiring` have `skeletons/` today.
- Recurrences: 1 (DFS-034)
- Recommendation: apply; four tickets have settled the shape, and the architect spent 28 turns partly rebuilding it.
- Decision:

### DFS-034.L-2: a chunked compare checks cancel before end-of-file and drops a finished entry

- Status: parked
- Impact: shared (touches `.claude/archetypes/`, read by the implementer and test-author)
- Target: `.claude/archetypes/use-case/impl-guide.md` and `test-guide.md`
- Change: impl-guide: a chunked read loop checks end-of-file (position == size) before reading the cancel signal; test-guide: pin a cancel raised in the last chunk's progress callback.
- Evidence: DFS-034 verification.md "Known limits" and learnings.md (verifier note): `NioContentComparator` checks `cancelled` first and detects EOF by a short read, so a file whose size is an exact multiple of 1 MiB is dropped as interrupted when the cancel arrives in its last callback.
- Recurrences: 1 (DFS-034)
- Recommendation: park; harmless (a cancelled run reports one entry fewer) and one adapter only; reconsider in DFS-042's refinement, which reuses this comparator.
- Decision: parked 2026-11-01 by the user
