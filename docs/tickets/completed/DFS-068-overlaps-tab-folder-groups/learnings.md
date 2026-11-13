# DFS-068 learnings

- **L-1 (refinement gap): `touches` missed the host component.** The ticket changes a child tab that takes `roots` (for `displayPath`) and `density` from its host, and removes the requests an existing host spec answers, yet neither `result-screen.html` nor `result-screen.spec.ts` is in `touches`. A component ticket that adds an input to a child, or changes the requests a child makes, should list the host's template and spec at refinement: grep the host's spec for the child's request helpers (`expectSummaries`, `openOverlaps`) and add both files.
- **L-2 (test guide): the Playwright layout flow has no red gate.** The component test guide asks for a `BrowserFlowTest` flow checking `isVisible()` and bounding-box widths for a layout-bound component, but an e2e flow runs only in `:server:e2eTest`, cannot fail on the stub in `npmTest`, and `BrowserFlowTest`'s existing flows break only once the implementation lands. The test-author left it to the implementer and listed it in the test plan; the guide should say which phase owns it.
- **L-3 (test guide): a "DFS-060.AC-1 ... loads Overlaps only when selected" test in `result-screen.spec.ts` reaches no stub** (the tab is not rendered), so it passes at red; it is a pre-existing guard whose `expectNone` now also covers `/groups`.
- **L-4 (test-author, architect): build test data that obeys the API's own invariants, or a derived figure can be wrong yet pass.** The "in all" test used one-folder groups whose `sharedBytes` did not match their copies, so summing `sharedBytes` over folders (double counting every copy) passed; live, a 3 MB + 2 MB group in two folders read "10.0 MB in all". When a frontend derives a number from several API fields, give at least one test realistic multi-folder data built the way the server computes it (here `sharedBytes` = copies in the folder × size), and check the derived figure against the mockup's meaning, not only the design's formula. The test-author flagged the formula but the flag reached only the verifier. Retro (2026-11-12): the architect should also check a derived figure's formula against the mockup's meaning and carry any test-author flag about it into `design.md`.
- **L-5 (verifier): a scratch Playwright script needs no Gradle task.** `java -cp <playwright, driver, driver-bundle, gson jars from ~/.gradle/caches> Live.java` with `PLAYWRIGHT_BROWSERS_PATH=server/.gradle/playwright-browsers` drives the built jar from the scratch directory: log in through the token link, `page.request().post` the scan with an `Origin` header, open `#/scans/<id>`, read the DOM and bounding boxes in both densities. A reusable live-check helper under `tools/` would save rewriting it each component ticket.
- **L-6 (verifier): set `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1` for the L-5 script.** In the second verify run, `Playwright.create()` found only Chromium in `server/.gradle/playwright-browsers` and downloaded Firefox and WebKit (about 180 MB) into the worktree's git-ignored cache before launching Chromium. `BrowserFlowTest` installs Chromium only; a scratch script must ask for the same, or a live check costs minutes and disk space. Retro (2026-11-12): apply this env-var note to the verifier prompt now; park the reusable live-check helper under `tools/` until a second component ticket needs it.
- **L-7 (rework routing): a done-gate formula change leaves `design.md` stale.** The rework went to tests and then implement, which may not edit `design.md`, so its "sum of `sharedBytes`" lines stay superseded, recorded only in `progress.md` and `test-plan.md`. When gate feedback changes a rule that `design.md` states, either route through the architect for a one-line edit or have the story skill add a "superseded by gate feedback" note, so the design file a later reader trusts does not contradict the code.

## Entries

### DFS-068.L-1: Ticket touches list missed the result screen host files

- Status: proposed
- Impact: local
- Target: .claude/archetypes/component/archetype.md (edge-case checklist) or the refine guidance for frontend component tickets
- Change: when a ticket changes a tab component's inputs or the endpoints it calls, refinement lists the host component's template and spec in `touches` and checks which host tests stub the replaced endpoints (grep the host spec for the child's request helpers).
- Evidence: commit 84efb21 added `result-screen.html` and `result-screen.spec.ts` to `touches` after the ticket was `ready`, found at the design gate; commit d84bedc then changed `result-screen.html`. Related but different omissions: DFS-067.L-1 (test package of new state), DFS-030.L-1 (accumulator file). Same as the free-text note L-1 above.
- Recurrences: 1 (DFS-068: see Evidence)
- Recommendation: park until it recurs on another component ticket; apply if it does. The design gate caught it at the cost of one design rework (47 and 16 turns).
- Decision:

### DFS-068.L-2: Test data did not follow the API's rules, so a wrong derived figure passed

- Status: proposed
- Impact: local
- Target: .claude/archetypes/component/test-guide.md, with a matching line for the architect in design.md.tmpl
- Change: require at least one test with realistic multi-field data built the way the server computes it whenever the frontend derives a number from several API fields, and have the architect check the design's formula against the mockup's meaning and carry any test-author flag about it into `design.md`.
- Evidence: the verifier found the "in all" size doubled (commit 757f69b's design said "sum of sharedBytes"; the tests used one-folder groups whose `sharedBytes` did not match their copies, so they passed). The done gate then sent the ticket back: commits 1d72379, b724a06, a6c1295, a2901ae. Same as the free-text note L-4 above.
- Recurrences: 1 (DFS-068: see Evidence)
- Recommendation: park until it recurs, then apply. It caused the most rework in this story, but it is one occurrence.
- Decision:
