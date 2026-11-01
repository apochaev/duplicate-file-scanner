## design (architect, attempt 1)

- use-case `design.md.tmpl`'s "State and transitions" and "Concurrency and atomicity" sections presume an asynchronous lifecycle; for a synchronous use case (DFS-034, like DFS-032/033) they collapse to "None" plus a thread note. A one-line hint in the template ("synchronous use case: say so and skip") would save the architect deliberating over it.
- The use-case archetype still has no `skeletons/`, although DFS-022, DFS-032, DFS-033 and DFS-034 now give a stable shape for a stateless synchronous query (port in `domain.usecases.query`, service with ports-only fields, factory method, `@ConditionalOnMissingBean` bean straight from the factory). Candidate for a skeleton set.

## verify (verifier, attempt 1)

- Chunked comparators that check `cancelled` at the top of the loop and detect end-of-file only by a short read see the cancel before EOF when the file size is an exact multiple of the chunk, so a fully compared entry can be dropped as "interrupted" (`NioContentComparator`, 3 MiB = 3 chunks). Harmless here, but a test-author could pin the intended semantics with a case that cancels in the last chunk's progress callback; the implementer could check `position == size` before the cancel.
- The AC-10 Spring test reused DFS-033's one-entry `driftPlan` instead of "the plan of the first scenario"; fine for wiring, but scenario wording that names a specific plan in another test class invites this drift. Saying "a plan" for wiring scenarios would match what is practical.
