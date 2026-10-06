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
