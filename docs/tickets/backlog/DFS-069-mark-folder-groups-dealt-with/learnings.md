# DFS-069 learnings

- DFS-069.L-1 (component archetype, checklist item 3): a stub reached from a lazily rendered component's template makes specs that never render it fail too. Here `overlaps-tab.spec.ts` failing 22 tests left the shared test environment so bad that `app.routes.spec.ts` and `session-ended.spec.ts` (which pass alone) timed out in the full run. The checklist asks to list specs that "fail with another error"; it should also say to run the whole suite once and compare with a run that excludes the stubbed spec, to tell collateral timeouts from real ones.
- DFS-069.L-2 (component archetype, checklist item 11): `Playwright.create()` downloads every browser (Chromium, headless shell, Firefox, WebKit, about 500 MB, into `%LOCALAPPDATA%\ms-playwright`) on first use even when the script launches with `setChannel("msedge")`. The live check should say to set `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1` in the environment of the `java -cp ...` run. This verifier ran it without the variable once, so those folders now exist on this machine.
- DFS-069.L-3 (test-author): a test for "nothing was written anywhere" with a `null` storage token asserted on a `FakeStorage` that was never provided, so the assertion cannot fail. When the token is null, either drop the assertion or spy on the real `localStorage` (`vi.spyOn(Storage.prototype, 'setItem')`) to prove no fallback write happens.
- DFS-069.L-4 (verifier, live check): selectors like `mat-expansion-panel.X mat-expansion-panel-header` also match the nested panels of every group card inside it. Use the child combinator plus `.first()` (`mat-expansion-panel.X > mat-expansion-panel-header`) in Playwright's strict mode.

## Entries

### DFS-069.L-1: A stubbed spec's failures make unrelated specs time out in the full run

- Status: applied
- Impact: local (one archetype's checklist)
- Target: .claude/archetypes/component/archetype.md (edge-case checklist, item 3)
- Change: add to item 3: run the whole suite once after the stubs compile, and compare it with a run that excludes the stubbed spec, so collateral timeouts in other specs are told apart from real breakage and listed under Expected breakage as such.
- Evidence: DFS-069 design phase: 22 failures in overlaps-tab.spec.ts left the shared test environment so bad that app.routes.spec.ts (2 tests) and session-ended.spec.ts (3 tests) timed out in the full run; both pass alone (commit 0c206d6, design.md "Expected breakage").
- Recurrences: 1 (DFS-069)
- Recommendation: apply. The wording gap is small and the full-run comparison costs one extra command.
- Decision: applied 2026-11-13 by the user; commit `docs: apply DFS-069.L-1, DFS-069.L-2, DFS-069.L-4`

### DFS-069.L-2: Playwright downloads every browser unless told not to

- Status: applied
- Impact: local (one archetype's checklist)
- Target: .claude/archetypes/component/archetype.md (edge-case checklist, item 11)
- Change: add to item 11: set PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 in the environment of the `java -cp ...` live-check run (with setChannel("msedge") or PLAYWRIGHT_BROWSERS_PATH pointing at the Chromium cache).
- Evidence: DFS-069 verifier ran Playwright.create() without it and downloaded about 500 MB of browsers into %LOCALAPPDATA%\ms-playwright. DFS-068's free-text L-6 recorded the same (about 180 MB of Firefox and WebKit) and its retro said to apply the note, but no file under .claude/ mentions the variable.
- Recurrences: 2 (DFS-068: free-text L-6, 2026-11-12; DFS-069: see Evidence)
- Recommendation: apply. It has now cost disk and minutes on two tickets in a row.
- Decision: applied 2026-11-13 by the user; commit `docs: apply DFS-069.L-1, DFS-069.L-2, DFS-069.L-4`

### DFS-069.L-3: A "nothing was written" test asserted on a fake that was never provided

- Status: parked
- Impact: local (one archetype's test guide)
- Target: .claude/archetypes/component/test-guide.md
- Change: add a rule: when a token is provided as null, a "nothing was written" assertion either spies on the real storage (`vi.spyOn(Storage.prototype, 'setItem')`) or is dropped; it never asserts on a fake the component did not receive.
- Evidence: DFS-069 overlaps-tab.spec.ts, AC-6 "no storage" test: `expect(storage.data.size).toBe(0)` checks a FakeStorage that was never injected, so it cannot fail (verifier minor issue, verification.md).
- Recurrences: 1 (DFS-069)
- Recommendation: park until it recurs. One occurrence, and the production code cannot write anyway with a null token; the retro said the same.
- Decision: parked 2026-11-13 by the user

### DFS-069.L-4: A descendant selector on an expansion panel matches the nested panels too

- Status: applied
- Impact: local (one archetype's checklist)
- Target: .claude/archetypes/component/archetype.md (edge-case checklist, item 11)
- Change: add a tip to item 11: in Playwright's strict mode use the child combinator plus `.first()` (`mat-expansion-panel.X > mat-expansion-panel-header`), because the descendant form also matches the panels nested inside each group card.
- Evidence: DFS-069 verifier's live check: `mat-expansion-panel.X mat-expansion-panel-header` matched several elements and broke strict mode (learnings.md note L-4).
- Recurrences: 1 (DFS-069)
- Recommendation: apply. It is one line and saves a verifier retry on any ticket with nested panels.
- Decision: applied 2026-11-13 by the user; commit `docs: apply DFS-069.L-1, DFS-069.L-2, DFS-069.L-4`
