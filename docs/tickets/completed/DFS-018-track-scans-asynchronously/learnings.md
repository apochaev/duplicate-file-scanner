# DFS-018 learnings

## design (architect)

- L-1 (archetype, stubs): "every stub method throws" does not say whether a record's compact-constructor invariants (`requireNonNull`, range checks) count as logic. I left the new records plain and listed the null checks the implementer adds in `design.md`. The `use-case` archetype (or D2) could state the rule once: data records are either written complete or plain, never stubbed with a throwing constructor (that would break every test fixture building them).
- L-2 (ticket gap): the ticket's `ErrorCode` list maps only `NoSuchFileException` and `NotDirectoryException` from `LocationResolver`, which may throw any `IOException`. Added `LOCATION_UNREADABLE` and flagged it at the design gate. Checklist item 2 / item 5 could ask "is every exception the validation step can throw mapped to a code?".
- L-3 (ticket/seam naming): decision 12 names the seam method `run` while the composed class's method is `scan`, so `ScanPipeline` needs a delegating method (a stub in an otherwise-finished class). Checklist item 13 could suggest naming the seam's method after the collaborator's existing method, so the collaborator only gains `implements`.
- L-4 (test observability): scenarios AC-3/AC-4 assert on "the repository's records", but the state port deliberately has no listing method. The archetype's test guide could say outright that such assertions use a fake of the state port that exposes its contents, not a production listing method.
- L-5 (template): `design.md.tmpl`'s Stubs line cites "D2 / CLAUDE.md decision 5", but CLAUDE.md decision 5 is about factories, not stubs; the stub convention is D2 alone. The same mis-citation is in `archetype.md`'s "Where files go".

- (test-author, attempt 2) The use-case archetype's `test-guide.md` naming section only covers `@DisplayName`. On a `@ParameterizedTest` the display name sits on the container and JUnit names each invocation `[n] VALUE`; RedCheck/TraceabilityCheck read the per-invocation name, so the invocations lose the `<ID>.AC-n` prefix and are classified as regressed pre-existing tests. The guide (and the test-author naming convention) should say: every `@ParameterizedTest` needs `name = "<ID>.AC-n <title> {0}"`.

## verify (verifier)

- (verifier) design.md checklist item 9 said "a `finally` marks FAILED on an Error"; the implementer wrote `catch (Error e) { finish(FAILED); throw e; }`. Same for every declared throwable, but not for a sneaky-thrown checked exception from a misbehaving runner. The use-case impl-guide could say which form it wants (a `finally` guarded by "still RUNNING/CANCELLING" is the airtight one), so the design wording and the code agree.
- (verifier) Behaviour the approved design adds beyond the scenarios (here `LOCATION_UNREADABLE`, a non-rejection `RuntimeException` from `execute`, an unrequested `CancellationException`) ends up untested by construction, because the test-author writes tests only from scenarios. The design gate could ask the human to either add a scenario for each such addition or accept it untested explicitly, so the verifier is not left judging untested branches.

## Entries

### DFS-018.L-1: Parameterized tests lose the criterion-ID prefix

- Status: applied
- Impact: local (archetypes)
- Target: `.claude/archetypes/use-case/test-guide.md`, "Naming convention (D11)", and the same section of `.claude/archetypes/pipeline-stage/test-guide.md`
- Change: every `@ParameterizedTest` sets `name = "<TICKET-ID>.AC-<n> <title> {0}"`. `RedCheck` and `TraceabilityCheck` read each invocation's name, and JUnit's default `[n] VALUE` hides the ID, so correct stub failures are counted as regressions.
- Evidence: `plan.json` defect 1: tests attempt 1 was `failed-gate` with 9 "regression" problems on the AC-9 and AC-12 invocations in `ScanServiceTest`, and attempt 2 escalated from sonnet to opus to fix the names (`1657c99`). The test-author's note above.
- Recurrences: 1 (DFS-018)
- Recommendation: apply. It caused a failed gate and a tier escalation, and the fix is one line in each test guide.
- Decision: applied 2026-10-06 by the user; commit `docs: apply DFS-018.L-1, DFS-018.L-2, DFS-018.L-3 and DFS-018.L-5`

### DFS-018.L-2: The stub convention is cited as "CLAUDE.md decision 5", which is about factories

- Status: applied
- Impact: local (archetypes)
- Target: `.claude/archetypes/use-case/archetype.md` "Where files go" and `use-case/design.md.tmpl` "Stubs", and the same lines in `.claude/archetypes/pipeline-stage/`
- Change: cite "D2" alone, without "CLAUDE.md decision 5". Also state that data records with constructor checks are left plain in the stub phase, with `design.md` listing the checks for the implementer.
- Evidence: the architect's notes L-1 and L-5 above; CCW-011's coverage check reported the same wrong reference (`docs/tickets/completed/CCW-011-add-use-case-archetype/CCW-011-ticket.md`, implementation notes). DFS-018's architect left `Result`, `ScanSnapshot` and `ScanRecord` plain and listed their checks in `design.md`; the implementer added them in `0608c04`. Related but distinct: CCW-004.L-1 and DFS-004.L-1 (skeleton classes' null checks before the throw).
- Recurrences: 1 (DFS-018)
- Recommendation: apply. A factual error copied into both archetypes.
- Decision: applied 2026-10-06 by the user; commit `docs: apply DFS-018.L-1, DFS-018.L-2, DFS-018.L-3 and DFS-018.L-5`

### DFS-018.L-3: Two gaps in the use-case checklist (exception mapping, seam method name)

- Status: applied
- Impact: local (`use-case` archetype)
- Target: `.claude/archetypes/use-case/archetype.md`, edge-case checklist items 5 and 13
- Change: item 5 asks whether every exception the validation step can throw maps to an `ErrorCode`, not only the expected ones; item 13 suggests naming the seam method after the collaborator's existing method, so the collaborator needs no delegating stub.
- Evidence: the architect's notes L-2 (`ErrorCode.LOCATION_UNREADABLE` added because `IOException`s other than not-found and not-a-directory had no code) and L-3 (`ScanRunner.run` versus `ScanPipeline.scan`, so `ScanPipeline` needed a delegating `run`).
- Recurrences: 1 (DFS-018)
- Recommendation: apply. Cheap checklist lines.
- Decision: applied 2026-10-06 by the user; commit `docs: apply DFS-018.L-1, DFS-018.L-2, DFS-018.L-3 and DFS-018.L-5`

### DFS-018.L-4: Behaviour the design adds beyond the scenarios goes untested

- Status: parked
- Impact: shared (`.claude/skills/story/SKILL.md`, a skill)
- Target: `.claude/skills/story/SKILL.md`, "Human gate"
- Change: at the design gate, list each behaviour `design.md` adds that no scenario covers, and ask the user to add a scenario or accept it untested.
- Evidence: the verifier's note above and `verification.md`: `ErrorCode.LOCATION_UNREADABLE`, a failing executor other than a rejection, and an unrequested `CancellationException` were approved at the design gate and are untested. Related but distinct: CCW-005.L-4.
- Recurrences: 1 (DFS-018)
- Recommendation: park. The design summary named all three this time and the user accepted them knowingly; it changes the shared gate flow, so wait for a recurrence.
- Decision: parked 2026-10-06 by the user

### DFS-018.L-5: Tests cannot see the state port's contents

- Status: applied
- Impact: local (`use-case` archetype)
- Target: `.claude/archetypes/use-case/test-guide.md`, "What to test against"
- Change: a scenario that checks what the state port holds uses a test fake of the port that exposes its records; no listing method is added to the production port for tests.
- Evidence: the architect's note L-4 above (AC-3 and AC-4 assert "the repository holds no scan record", but `ScanRepository` has no listing method); the test-author's `ListingRepository` in `ScanServiceTest` (`1657c99`).
- Recurrences: 1 (DFS-018)
- Recommendation: apply.
- Decision: applied 2026-10-06 by the user; commit `docs: apply DFS-018.L-1, DFS-018.L-2, DFS-018.L-3 and DFS-018.L-5`

### DFS-018.L-6: The guides do not say which form marks the work FAILED

- Status: parked
- Impact: local (`use-case` archetype)
- Target: `.claude/archetypes/use-case/impl-guide.md`, and checklist item 9 in `archetype.md`
- Change: require a `finally` that marks the work FAILED if it is still RUNNING or CANCELLING, which covers an `Error` and an undeclared checked exception alike, instead of a `catch (Error)`.
- Evidence: the verifier's note above and `verification.md`: `design.md` item 9 said a `finally` marks FAILED; the implementer wrote `catch (Error e)` (`0608c04`), which leaves a scan RUNNING if a runner throws an undeclared checked exception.
- Recurrences: 1 (DFS-018)
- Recommendation: park. One story, recorded as a minor note.
- Decision: parked 2026-10-06 by the user
