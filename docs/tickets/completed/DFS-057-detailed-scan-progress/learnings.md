# DFS-057 learnings

- **architect, Expected breakage covers only the red phase.** Running the existing tests against the stubs (step 4) shows which fail at red, and here all of them fail with the stub's error. It cannot show the existing assertions an implementation will break later because the ticket replaces what they check (DFS-041.AC-6 asserts `.scan-phase` holds "Finding files" and the hashing bar's value). I found that only by grepping `scan-screen.spec.ts` for the selectors the design replaces, and then kept `.scan-phase` holding the label so the old test stays valid. The architect guide could name this case: when a ticket changes existing visible behavior, grep the existing specs for the replaced selectors and texts, and either keep them compatible or list the specs the test-author must update.
- **architect, a stub reached from an RxJS `next` callback does not surface.** RxJS rethrows a subscriber's error from a timer, outside Angular's `ErrorHandler`, so `provideStubSurfacing()` would not see it reliably. The row feeds the estimator from an `effect` instead (effect errors reach the `ErrorHandler`). Worth a line in the component archetype's checklist item 3.
- **architect, Vitest's "Unhandled Errors".** In the existing specs without `provideStubSurfacing()` (`scan-row.spec.ts`), the effect's stub error also shows up as 2 "Uncaught Exception" entries at the end of the Vitest run, attributed to that file. Every failing test still names `not implemented: DFS-057` as its error, but the red gate should not count the unhandled-error block as a separate regression.
- **verifier, the live check needs a browser driver and a folder that reaches hashing fast.** Reading the DOM on the built jar worked with Playwright for Java straight from the Gradle cache (`playwright`, `driver`, `driver-bundle`, `gson` jars) and `setChannel("msedge")`, so no Chromium download; in Git Bash the `-cp` needs `C:/` paths, not `/c/`. But a folder of ~100 000 small files (a projects tree) spent minutes in the sample phase from a cold cache, so FULL_HASH and the ETA were never reached within the turn budget. For a progress ticket, the live check should scan a folder of a few large duplicate files (or the done-item should name one), and a ready-made scratch driver in the component archetype would save several verifier turns.
- **verifier, a docs slip the gate cannot see.** The implementer's `CLAUDE.md` sentence says "Comparing samples" shows bytes done and left; AC-2 says it shows none. Doc sentences summarising several phases are worth checking against each criterion.

## Entries

### DFS-057.L-1: The architect guide misses existing specs whose assertions the ticket's new behavior breaks later

- Status: proposed
- Impact: shared
- Target: `.claude/agents/architect.md`
- Change: add a step: when a ticket changes existing visible behavior, grep the existing specs for the selectors and texts being replaced, then either keep them compatible or list the specs the test-author must update under Expected breakage.
- Evidence: DFS-057 notes (architect, first bullet): Expected breakage comes from the red run and shows nothing about assertions the implementation breaks later; DFS-041.AC-6 in `scan-screen.spec.ts` asserted `.scan-phase` and was found only by grepping. Related but different: DFS-049.L-2 (breakage from new providers, which the red run does show).
- Recurrences: 1 (DFS-057)
- Recommendation: apply; a cheap check that prevents breakage the red run cannot show.
- Decision:

### DFS-057.L-2: The component checklist does not say a stub must not be reached from an RxJS `next` callback

- Status: proposed
- Impact: local
- Target: `.claude/archetypes/component/archetype.md` checklist item 3
- Change: add: do not call a stub from an RxJS subscriber callback, since RxJS rethrows its error from a timer outside Angular's `ErrorHandler` and `provideStubSurfacing()` does not see it; feed it from an `effect` instead. Add that Vitest's "Unhandled Errors" block in a spec without `provideStubSurfacing()` is not a separate red regression.
- Evidence: DFS-057 notes (architect, second and third bullets): `ScanRow` feeds the ETA estimator from an `effect`; `scan-row.spec.ts` showed 2 "Uncaught Exception" entries at red while every failing test still named `not implemented: DFS-057`.
- Recurrences: 1 (DFS-057)
- Recommendation: apply; two verified gotchas with a concrete workaround.
- Decision:

### DFS-057.L-3: The component live check has no fast-to-hash folder and no ready browser driver, so a progress ticket's check stays partial

- Status: proposed
- Impact: local
- Target: `.claude/archetypes/component/archetype.md` (checklist item 11 and extra done-items) and a scratch Playwright driver kept with the archetype
- Change: for a progress or ETA ticket, name a folder of a few large duplicate files for the live check instead of a large tree; ship a ready-made scratch Playwright-for-Java driver (Gradle-cache jars, `setChannel("msedge")`, `C:/` paths in `-cp` under Git Bash) so the verifier spends no turns building one.
- Evidence: DFS-057 plan.json verify run: 42 turns, hit the 30-turn cap once and was resumed; the live check saw WALK and PARTIAL_HASH on a 100 000-file tree and never FULL_HASH or the ETA; the done-item stayed unticked and the ticket closed with it open. Related: DFS-051 (verifier turn cap), which raised the cap instead of shortening the check.
- Recurrences: 1 (DFS-057)
- Recommendation: apply; the live check is the only part of the ticket that was not verified.
- Decision:
