# DFS-063 learnings

- component archetype: the architect cannot delete a module whose spec still exists (scan-phase.ts and scan-phase.spec.ts), since one broken import fails the whole Vitest bundle. The archetype could say so: "a module the ticket retires stays until the test-author deletes its spec; the implementer then deletes it".
- component archetype, checklist item 3: a CSS-only criterion (AC-15, reduced motion) needs a spec that first renders something reaching a stub, or the red gate rejects it; worth one line in the checklist.
- test phase: AC-15 (reduced motion) is tested by reading the component's `<style>` elements from `document` after a render; it is unverified that Angular's unit-test builder keeps the media block in those styles (the stub CSS has none), so the implementer may hit a green-phase surprise; if so, the test needs another way to read the stylesheet.
- implement phase, test defect (AC-15, `scan-row.spec.ts` line 434): the stylesheet is readable from `document` (Angular's test builder keeps the media block), but `expect(reduced).not.toMatch(/animation(-name)?\s*:\s*(?!none)/)` can never pass: Angular prints `animation: none`, and `\s*` backtracks to zero spaces so the lookahead sees the space and the regex matches. Any `animation: none` fails it. Fix: `/animation(-name)?\s*:(?!\s*none)/`. The implementer left the real rule `animation: none` inside `@media (prefers-reduced-motion: reduce)` and did not edit the test.
- test phase: the test-guide has no pattern for a CSS-only criterion; the one used here (render first, then read document styles) could be a guide entry once it is proven at green.
- verify phase: AC-12's tests rendered only an entry already COMPLETED (with `finishedAt`), so none caught that a row reaching COMPLETED through the stream has no `finishedAt` (the stream does not carry it) and shows "took" empty. Test-guide lesson: a row whose fields come from both the entry and the stream needs a test that reaches each terminal status through the stream, not only by the initial entry. Only the by-hand run on the jar found it.
- verify phase: the hashing progress arrived about every 3 s in roughly 800 MB steps on a 16 GB scan, so a 500 ms DOM sampler never caught the short "finishing" or hard-link states; the by-hand check needs a slower or larger fixture to see them.
- verify phase, second pass: a CDP `Page.navigate` from `/?token=` to `/#/` changes only the hash and does not reload, so a scan started through the API never appeared in the list; `Page.reload` does. Worth a line in whatever guide the by-hand check gets.
- verify phase, second pass: `scan-finish.ts` is a test helper (it imports `HttpTestingController`) living in `src/app/scans/` without a `.spec` name, placed there to stay inside `touches`. It is not bundled (nothing in `main.ts` reaches it), like `scan-test-data.ts`, but a `testing/` folder in `touches` would have been the cleaner home.

## Entries

### DFS-063.L-1: A module the ticket retires cannot be deleted by the architect

- Status: applied
- Impact: local
- Target: .claude/archetypes/component/archetype.md (architect guidance)
- Change: add a line: "A module the ticket retires stays until the test-author deletes its spec; the implementer then deletes it", since one broken import fails the whole Vitest bundle.
- Evidence: DFS-063 design.md open note 1 and learnings: `scan-phase.ts` and `scan-phase.spec.ts` could not be deleted by the architect; the test-author deleted the spec and the implementer the module.
- Recurrences: 1 (DFS-063)
- Recommendation: apply; a concrete rule that avoids a bundle-wide failure.
- Decision: applied 2026-11-10 by the user; commit `docs: apply DFS-063.L-1, DFS-063.L-2, DFS-063.L-3`

### DFS-063.L-2: The component guides have no pattern for a CSS-only criterion, and a regex trap cost a rework loop

- Status: applied
- Impact: local
- Target: .claude/archetypes/component/test-guide.md and checklist item 3 of archetype.md
- Change: add the pattern: render a component that reaches a stub first (the red gate rejects a test that reaches none), then read the `<style>` elements from `document`, which keep the media block under Angular's test builder. Warn that a negative lookahead after `\s*` backtracks, so use `/animation(-name)?\s*:(?!\s*none)/`, not `/animation(-name)?\s*:\s*(?!none)/`.
- Evidence: DFS-063 AC-15 test in `scan-row.spec.ts`: the first regex matched `animation: none` and could never pass (plan.json defect 1; implement attempt 1 `failed-gate`, tests reopened).
- Recurrences: 1 (DFS-063)
- Recommendation: apply; the pattern is proven at green and a defect cost a rework loop.
- Decision: applied 2026-11-10 by the user; commit `docs: apply DFS-063.L-1, DFS-063.L-2, DFS-063.L-3`

### DFS-063.L-3: Tests must reach each terminal status through the stream, and existing specs must answer a new request

- Status: applied
- Impact: local
- Target: .claude/archetypes/component/test-guide.md
- Change: add a rule: when a row's fields come from both the entry and the stream, tests drive the stream to every terminal status, not only an entry that starts terminal. When a change adds a request, every existing spec that reaches that state through the stream must answer it (a shared helper), or `afterEach` `http.verify()` fails and cascades.
- Evidence: DFS-063 defects 2 and 3: AC-12's tests rendered only an already-COMPLETED entry, so a row completing in view showed "took" empty (found only by the by-hand run); the refetch then left an unanswered request in existing specs (about 33 cascading failures). Related but different: DFS-057.L-1 (grep for replaced selectors).
- Recurrences: 1 (DFS-063)
- Recommendation: apply; it caused two defects and three extra phase runs including a verifier rerun.
- Decision: applied 2026-11-10 by the user; commit `docs: apply DFS-063.L-1, DFS-063.L-2, DFS-063.L-3`

### DFS-063.L-4: By-hand check tips for slow progress and CDP navigation

- Status: parked
- Impact: shared (an agent prompt)
- Target: .claude/agents/verifier.md
- Change: add two notes: use a slower or larger fixture so short states (finishing, hard links) are observable, since progress arrived about every 3 s in roughly 800 MB steps; and use CDP `Page.reload`, not `Page.navigate` to a hash-only change, which does not reload.
- Evidence: DFS-063 verify attempts 1 and 2: a 500 ms sampler missed the finishing and hard-link states on a 16 GB scan; a scan started through the API never appeared after a hash-only navigate. Verify attempt 1 used 38 turns and hit the cap. Related: DFS-057 notes, bullet 6 (fixture of a few large files; a different cause) and DFS-060 notes (CDP script tips, port reuse).
- Recurrences: 1 (DFS-063); related notes in DFS-057 and DFS-060 are not counted
- Recommendation: park; by-hand tips for one UI story type, so confirm they recur before touching a shared prompt.
- Decision: parked 2026-11-10 by the user

### DFS-063.L-5: Test helpers need a home inside `touches`

- Status: parked
- Impact: local
- Target: .claude/archetypes/component/archetype.md (touches skeleton)
- Change: suggest including a `testing/` folder in the default `touches` for frontend stories, so a helper such as `scan-finish.ts` does not sit in the production folder under a non-spec name.
- Evidence: DFS-063 gate: `touches` failed on `src/testing/scan-finish.ts`; the helper moved to `src/app/scans/scan-finish.ts` (not bundled, like `scan-test-data.ts`).
- Recurrences: 1 (DFS-063)
- Recommendation: park; cosmetic, the helper is not bundled.
- Decision: parked 2026-11-10 by the user
