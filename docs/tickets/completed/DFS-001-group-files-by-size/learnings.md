# DFS-001 learnings

## design (architect)

- **Template gap: checklist item 14 is missing from `design.md.tmpl`.** `archetype.md`'s edge-case checklist now has 14 items ("Input/output currency named", added from CCW-005), but the template's "Checklist disposition" section stops at 13. I added item 14 to this `design.md` by hand. The template should list it too.
- **`skeletons/Accumulator.java.tmpl` contradicts L-013.** Its `add` and `merge` stubs call `Objects.requireNonNull(...)` before the `throw`. As a result, a null-argument test (AC-7 here) passes against the stub instead of failing red, which is exactly what L-013 forbids and what `FilterStage.java.tmpl` and `StageResult.java.tmpl` already avoid. I left the checks out of the `SizeIndex` stub. The skeleton should drop them and carry the same "implementer: add requireNonNull" comment as the other two.
- **The skeletons do not cover an accumulator whose constructor takes validated configuration.** `Accumulator.java.tmpl` has only a no-arg constructor that does not throw. For `SizeIndex(long minSize)`, a constructor that does not throw would let the negative-minimum test fail red without the `not implemented:` message, so I made the constructor throw too (the no-arg constructor delegates to it). The skeleton could show this pattern.

## test (test-author)

- **Ambiguity between "the ticket's scenarios" and "the design doc's commitments" for `CandidateGroup`'s null checks.** The ticket's AC-7 Gherkin only exercises `SizeIndex`'s negative-minimum and null-`entry` cases; it never mentions a null `files` list or a null element for `CandidateGroup`. `design.md`'s Public API section and checklist-disposition item 2 do commit to both being rejected (NPE), and the stub's Javadoc says the same. I wrote tests for them anyway, reading `test-guide.md`'s "whatever the design doc's checklist disposition commits to" clause as covering this, but flagging it here since it's a judgment call between two instructions that could point different ways (ticket-scenario literalism vs. design-doc commitment).
- **Same ambiguity, opposite resolution, for `SizeIndex.merge(null)`.** `design.md`'s Public API section and the stub's Javadoc both document `merge(null)` as throwing `NullPointerException`, but that specific case is not in `design.md`'s "Checklist disposition" section (which only discusses `merge`'s minimum-mismatch rejection) and is not named in the ticket's AC-7 scenario ("a negative minimum size, or a null entry" — "entry" reads as `add`'s parameter, not `merge`'s `other`). I left it untested per the instruction against inventing scenarios beyond the ticket, and noted the gap in `test-plan.md`'s "Notes for the implementer" instead of silently adding or silently skipping it. Worth the orchestrator/reviewer confirming this reading is correct, since a reasonable reader could argue the ticket's "or a null entry" was meant loosely to cover any null argument to the index.
- **`SizeIndexTest`'s "merging leaves the other index unchanged" scenario is a weak check as literally written.** The ticket scenario has `index2` hold a single file (`"b.txt" of 10 bytes"`), which alone always produces an empty `groups()` result before and after any merge, regardless of whether `merge` actually shares mutable state with `other`. I implemented it exactly as scenario-written (captures `groups()` before, asserts it is unchanged after more adds to `index1`), rather than substituting a two-file `index2` that would make the check meaningful, since the latter would depart from the scenario's literal given clause. Flagging this in case the reviewer prefers a stronger version; happy to adjust if asked.

## Entries

Structured entries below were migrated from `docs/learnings/log.md` (2026-09-23, CCW-010); the free-text notes above predate that convention and are unchanged. Numbers were global (`L-<n>`) before the move; each entry's own number below is local to this file only.

### DFS-001.L-1: `design.md.tmpl` missing checklist disposition item 14

- Status: applied
- Impact: shared (archetype template every pipeline-stage architect writes from)
- Target: `.claude/archetypes/pipeline-stage/design.md.tmpl`
- Change: add item 14 ("Input/output currency named") to the template's "Checklist disposition" list, matching `archetype.md`'s 14-item checklist (added by L-015).
- Evidence: `docs/tickets/backlog/DFS-001-group-files-by-size/learnings.md`, design section — "Template gap: checklist item 14 is missing from `design.md.tmpl`... I added item 14 to this `design.md` by hand. The template should list it too."
- Recurrences: 1 (DFS-001)
- Recommendation: apply — direct fallout from L-015 not being propagated to the template that actually renders it; will recur on every pipeline-stage ticket until fixed.
- Decision: applied 2026-09-22 by the user, as recommended; commit `docs: apply L-021 and L-022`.

### DFS-001.L-2: `Accumulator.java.tmpl` contradicts L-013 and lacks a validated-configuration constructor pattern

- Status: applied
- Impact: shared (archetype skeleton every accumulator-shaped pipeline-stage story's architect reads)
- Target: `.claude/archetypes/pipeline-stage/skeletons/Accumulator.java.tmpl`
- Change: (1) drop the pre-throw `Objects.requireNonNull(...)` in `add`/`merge`'s stub bodies — the same L-013/L-014 ordering bug, now found on a third skeleton — replacing with the same "implementer: add requireNonNull" guiding comment used in the other two; (2) add an example of a constructor taking validated configuration (e.g. `SizeIndex(long minSize)`) that itself throws the not-implemented exception, with a no-arg constructor delegating to it, so a validation-bearing constructor fails red correctly.
- Evidence: `docs/tickets/backlog/DFS-001-group-files-by-size/learnings.md`, design section — both `Accumulator.java.tmpl` bullets: "`skeletons/Accumulator.java.tmpl` contradicts L-013... I left the checks out of the `SizeIndex` stub. The skeleton should drop them and carry the same 'implementer: add requireNonNull' comment as the other two." and "The skeletons do not cover an accumulator whose constructor takes validated configuration... I made the constructor throw too... The skeleton could show this pattern."
- Recurrences: 1 (DFS-001) — third skeleton file independently found with the L-013-shaped bug (after `FilterStage.java.tmpl`, `StageResult.java.tmpl`); worth a glance at whether `pipeline-stage`'s remaining skeletons need the same audit rather than waiting for each to surface one at a time.
- Recommendation: apply, consistent with L-013/L-014.
- Decision: applied 2026-09-22 by the user, as recommended; commit `docs: apply L-021 and L-022`.

### DFS-001.L-3: A DoD item requiring a measured figure should get a committed, reproducible benchmark, not a throwaway class

- Status: applied
- Impact: shared (`pipeline-stage`'s `impl-guide.md`, the archetype every implementer reads)
- Target: `.claude/archetypes/pipeline-stage/impl-guide.md`
- Change: when a ticket's Definition of done requires a measured figure (heap, timing, throughput) recorded before closing, the implementer writes it as a committed, `@Disabled` (or otherwise excluded-from-default-run) benchmark in the test tree, not a throwaway class run outside the build — so the figure is reproducible by anyone later, including the verifier or a human closing the ticket, instead of resting on an unverifiable one-off run.
- Evidence: `docs/tickets/completed/DFS-001-group-files-by-size/DFS-001-ticket.md` — the implementer's heap measurement used "a throwaway `HeapMeasure` class run directly against `file-scanner`'s compiled classes (not committed, not part of the build)"; the verifier explicitly could not reproduce it and left the done-item unticked for that reason. The closing session (2026-09-22) had to write `SizeIndexHeapBenchmark.java` after the fact and re-run it to actually tick the item. Independently, the pilot's control-branch implementation of the same ticket made the identical choice (its own throwaway, uncommitted `HeapMeasure`-equivalent) — two separate implementations converged on the same non-reproducible pattern with no guidance against it.
- Recurrences: 1 (DFS-001) — though the pattern recurred within one ticket, in both the pipeline and control implementations independently; not counted twice per the log's own rule (repeats within one ticket are noted, not double-counted).
- Recommendation: apply. Two independent implementations made the same non-reproducible choice with no template guidance either way; a one-line addition to `impl-guide.md` (or wherever the archetype's benchmark guidance best fits) closes the gap before it recurs on DFS-002 or any future pipeline-stage ticket with a measured DoD item.
- Decision: applied 2026-09-22 by the user, as recommended; commit `docs: apply L-023`.
