## design (architect, attempt 1)

- use-case `design.md.tmpl`'s "State and transitions" and "Concurrency and atomicity" sections presume an asynchronous lifecycle; for a synchronous use case (DFS-034, like DFS-032/033) they collapse to "None" plus a thread note. A one-line hint in the template ("synchronous use case: say so and skip") would save the architect deliberating over it.
- The use-case archetype still has no `skeletons/`, although DFS-022, DFS-032, DFS-033 and DFS-034 now give a stable shape for a stateless synchronous query (port in `domain.usecases.query`, service with ports-only fields, factory method, `@ConditionalOnMissingBean` bean straight from the factory). Candidate for a skeleton set.
