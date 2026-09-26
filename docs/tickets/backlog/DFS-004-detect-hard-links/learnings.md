## design (architect, attempt 1)

- Ticket ambiguity (AC-8): "a file whose same-file check throws" does not say which file of a pairwise check is at fault. The design attributes it to the candidate being placed. If a copy's first link is the unreadable one, readable candidates are dropped instead (conservative: fewer copies, never more). Worth a sentence in the ticket or the archetype's item 10 for pairwise ports.
- Skeleton gap: `FilterStage.java.tmpl` keeps a real `requireNonNull` in the stub constructor, so a null-constructor test passes against the stub (L-013). The Accumulator skeleton already advises a throwing constructor. The design follows that advice here. The FilterStage skeleton could say the same.

## test (test-author, attempt 1)

- Gap, not silently worked around: `design.md`'s Public API section documents `NullPointerException` on a null `identity`, a null `sets` list, and a null element of `sets`, but no live acceptance criterion (AC-1..AC-5, AC-8, AC-9; AC-6/AC-7 retired) tags a null-rejection scenario. Per the test-author's instruction not to invent scenarios beyond what the ticket states, no null-argument tests were written. The archetype's `test-guide.md` ("Invalid input") would otherwise call for one; worth deciding, when this ticket is next refined or a similar one is planned, whether such construction-invariant checks should get their own AC even when "the constructor throws NPE" is not really a user-facing scenario.
- The existing `Fixtures.createHardLinkOrSkip` helper in `infra.adapter`'s test sources (from an earlier ticket) already does exactly what design.md's AC-9 test needs, so no new fixture helper was written for `NioFileIdentityTest`.
