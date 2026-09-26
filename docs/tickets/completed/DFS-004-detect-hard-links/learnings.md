## design (architect, attempt 1)

- Ticket ambiguity (AC-8): "a file whose same-file check throws" does not say which file of a pairwise check is at fault. The design attributes it to the candidate being placed. If a copy's first link is the unreadable one, readable candidates are dropped instead (conservative: fewer copies, never more). Worth a sentence in the ticket or the archetype's item 10 for pairwise ports.
- Skeleton gap: `FilterStage.java.tmpl` keeps a real `requireNonNull` in the stub constructor, so a null-constructor test passes against the stub (L-013). The Accumulator skeleton already advises a throwing constructor. The design follows that advice here. The FilterStage skeleton could say the same.

## test (test-author, attempt 1)

- Gap, not silently worked around: `design.md`'s Public API section documents `NullPointerException` on a null `identity`, a null `sets` list, and a null element of `sets`, but no live acceptance criterion (AC-1..AC-5, AC-8, AC-9; AC-6/AC-7 retired) tags a null-rejection scenario. Per the test-author's instruction not to invent scenarios beyond what the ticket states, no null-argument tests were written. The archetype's `test-guide.md` ("Invalid input") would otherwise call for one; worth deciding, when this ticket is next refined or a similar one is planned, whether such construction-invariant checks should get their own AC even when "the constructor throws NPE" is not really a user-facing scenario.
- The existing `Fixtures.createHardLinkOrSkip` helper in `infra.adapter`'s test sources (from an earlier ticket) already does exactly what design.md's AC-9 test needs, so no new fixture helper was written for `NioFileIdentityTest`.

## Entries

### DFS-004.L-1: FilterStage skeleton's constructor still null-checks before any throw

- Status: applied
- Impact: local
- Target: .claude/archetypes/pipeline-stage/skeletons/FilterStage.java.tmpl
- Change: Make the stub constructor throw `new UnsupportedOperationException("not implemented: <TICKET-ID>")`, with an implementer comment to put `Objects.requireNonNull(hasher, "hasher")` back after the throw. This matches the Accumulator skeleton's advice and finishes CCW-004.L-1, which fixed only `filter`.
- Evidence: DFS-004 design phase (commit 4c79d75): the skeleton's constructor keeps a real `requireNonNull` (FilterStage.java.tmpl:16), so a null-constructor test would pass against the stub. The architect noticed and made `HardLinkDetector`'s constructor throw instead, following Accumulator.java.tmpl.
- Recurrences: 1
- Recommendation: apply. It is a one-line skeleton fix that finishes an already-applied entry and makes every pipeline-stage stub consistently red.
- Decision: applied 2026-09-26 by the user; commit `docs: apply DFS-004.L-1`

### DFS-004.L-2: Pairwise-port failure attribution is left to each design

- Status: parked
- Impact: local
- Target: .claude/archetypes/pipeline-stage/archetype.md (edge-case checklist)
- Change: Add a sentence for stages that call a pairwise port (compare A with B): the ticket must say which path is dropped when the check throws. The conservative default is the candidate being placed, since that can only report fewer copies, never more.
- Evidence: DFS-004 design phase (commit 4c79d75): AC-8 said "a file whose same-file check throws" without saying which of the two. The architect chose the candidate and raised it at the design gate. The untested consequence, an unreadable first link dropping readable candidates, is noted in verification.md.
- Recurrences: 1
- Recommendation: park. So far only DFS-004 has a pairwise port. Revisit when the ContentComparator deletion-time check is refined, since it will face the same question.
- Decision: parked 2026-09-26 by the user
