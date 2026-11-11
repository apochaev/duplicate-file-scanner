# DFS-066 learnings

- Template gap (component archetype): a criterion satisfied by a configuration property alone (AC-1, `server.servlet.session.timeout`) cannot be stubbed, so its test can only fail the red gate on an assertion, not on `not implemented:`. The archetype and the red gate have no rule for a property-only criterion; the ticket's pairing with archetype `component` made it a Java test in a frontend pipeline.
- Stub reach through an HTTP interceptor: a stub called inside an RxJS `catchError` becomes a failed HTTP call, which a screen's own `catch` swallows into its generic message, so the story test fails on an assertion. The archetype's item 3 covers `subscribe` callbacks but not interceptors; the fix used here is a stubbed method called from the shell's template on every render (`App.showNotice()`).
- Refinement gap (verifier): AC-2 says "a 403 from any `/api/**` call", but the server already uses 403 for `PATH_OUTSIDE_ROOTS` (result, overlaps, plan APIs), which is not a session ending. Refinement should check an HTTP status's existing meanings across the API before mapping it to one UI state. Here `isSessionEnded` could skip a 403 whose body carries a `code`.
- Done-item that cannot be reproduced (verifier): "shorten the timeout for the check" does not work once the timeout is "never". Shortening it tests a different value, and the unshortened check needs more than 30 idle minutes. A done-item should name a check that can be run in a verifier's time, or say it is for the user.
- Parallel-ticket overlap (verifier): DFS-065, uncommitted on `main`, also rewrites `app.html` (it moves "Connected" into the toolbar). Two open tickets editing the shell template were not flagged at refinement, so the second to land must merge by hand.

## Entries

### DFS-066.L-1: Property-only criterion has no red-gate rule

- Status: proposed
- Impact: local
- Target: `.claude/archetypes/component/archetype.md` and `test-guide.md`
- Change: a criterion satisfied only by a configuration property is exempt from the "fails on `not implemented:`" red check; test it with a Java test that fails on an assertion, and have the gate accept that without a user exception.
- Evidence: `plan.json` defect 1 (tests, "red: AC-1 test fails on an assertion, not the stub exception"); the first `tests` run was `failed-gate` and needed a user-approved exception (2026-11-11).
- Recurrences: 1
- Recommendation: apply; it caused a failed gate and a manual exception, and recurs for any property-only criterion.
- Decision: pending

### DFS-066.L-2: Stub reach through an HTTP interceptor

- Status: proposed
- Impact: local
- Target: `.claude/archetypes/component/archetype.md`, item 3 (stub placement)
- Change: never place a stub inside an interceptor's `catchError`, because a screen's own `catch` swallows the failure; use a stubbed method called from the shell template on every render (as `App.showNotice()`).
- Evidence: this ticket's design phase; item 3 covers `subscribe` callbacks but not interceptors; the workaround was proven in this story.
- Recurrences: 1
- Recommendation: apply; the workaround is already proven.
- Decision: pending

### DFS-066.L-3: Spec leaves the initial route's HTTP call unflushed

- Status: proposed
- Impact: local
- Target: `.claude/archetypes/component/test-guide.md`
- Change: frontend specs that bootstrap the shell must flush or match the initial route's requests (e.g. `GET /api/scans`) before `http.verify()` in `afterEach`.
- Evidence: `plan.json` defect 2 (3 session-ended specs left `GET /api/scans` open); cost a failed implement attempt (9 turns) and a reopened tests phase (3 turns).
- Recurrences: 1
- Recommendation: apply; it cost a failed implement attempt and a reopened tests phase.
- Decision: pending

### DFS-066.L-4: Refinement does not check an HTTP status's existing meanings

- Status: proposed
- Impact: shared
- Target: `.claude/skills/tickets/SKILL.md` (refine)
- Change: before a ticket maps an HTTP status to one UI state, grep the API for that status's other uses (403 is also `PATH_OUTSIDE_ROOTS`); also flag open tickets that edit the same shell file (DFS-065 and `app.html`).
- Evidence: AC-2 said "a 403 from any `/api/**` call" though 403 already means `PATH_OUTSIDE_ROOTS`; DFS-065, uncommitted on `main`, also rewrites `app.html`.
- Recurrences: 1
- Recommendation: park; one story, one occurrence; confirm a second before changing the skill.
- Decision: pending

### DFS-066.L-5: Done-item must be reproducible in a verifier's time

- Status: proposed
- Impact: shared
- Target: `.claude/skills/tickets/SKILL.md` (refine, done-item guidance)
- Change: each done-item names a check a verifier can run in its time budget, or is marked "for the user"; avoid items like "shorten the timeout" when the value under test is "never".
- Evidence: the verifier could not reproduce "shorten the timeout for the check" once the timeout was "never"; the unshortened check needs more than 30 idle minutes.
- Recurrences: 1
- Recommendation: apply; a small wording rule with no downside.
- Decision: pending
