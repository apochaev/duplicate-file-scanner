# DFS-067 learnings

## Entries

### DFS-067.L-1: `touches` omitted the test package of the new per-scan state

- Status: applied
- Impact: local (one archetype)
- Target: `.claude/archetypes/endpoint/archetype.md` (refinement and design-gate guidance)
- Change: add: when the design puts package-private per-scan state in a package, `touches` lists that package's test directory (here `server/src/test/java/us/pochaev/dfs/server/result`); otherwise the architect flags the mismatch at the design gate.
- Evidence: DFS-067 verifier note: the ticket's `touches` listed the `analysis` test package and `GroupApiTest.java` but not the `result` test package, so the test-author had to drop `GroupStateTest`, leaving build-once, failed-build-retried and refused-request-builds-nothing untested (checked by inspection only); plan.json tests-phase note ("first return failed touches (GroupStateTest outside touches)").
- Recurrences: 1 (DFS-067)
- Recommendation: apply; small, confined to this archetype, and it can recur on any endpoint ticket with lazy state.
- Decision: applied 2026-11-12 by the user; commit `docs: apply DFS-067.L-1`

### DFS-067.L-2: The scale budget the architect fixes is not copied back to the ticket at the design gate

- Status: parked
- Impact: local (one archetype)
- Target: `.claude/archetypes/endpoint/archetype.md` (or the endpoint ticket template)
- Change: when a ticket defers a scale budget to the architect ("within the budget `design.md` fixes"), the ticket records the fixed number at the design gate, not at verify.
- Evidence: DFS-067 ticket done-item; commit b2469fc (verify) added "budget 3 s; measured 529 ms, 49,991 groups"; the design commit c367987 did not copy the budget back.
- Recurrences: 1 (DFS-067)
- Recommendation: park; one occurrence, low impact, and `design.md` already holds the figure.
- Decision: parked 2026-11-12 by the user
