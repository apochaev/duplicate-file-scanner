# DFS-049 learnings

- **L-1 (architect): `component` archetype.md is stale on routing and `package.json`.** "Where files go" still says "routing does not exist yet (DFS-041 decision 2) and comes with the first ticket that needs deep links, which also needs a server-side fallback for its routes"; DFS-049 adds hash routing with no server fallback (decision 1), and adds `@angular/router` to `package.json` although the archetype excludes "the frontend build itself (`angular.json`, `package.json`, ...)" (decision 7). Suggest: routing exists (hash URLs, `app.routes.ts`, screens as routes, lazy `loadComponent` for a heavy screen); a runtime package of the same framework release is allowed in a component ticket, build configuration is not.
- **L-2 (architect): adding `RouterLink` to an existing component breaks every spec that renders it without router providers** ("No provider found for `ActivatedRoute`"), not with the stub's error. The archetype's checklist could add: "a new directive or injected service in an existing component — which existing specs render it and lack its providers? Declare them under Expected breakage." Running `:server:npmTest` once after writing the stubs pinned the exact list cheaply.
- **L-3 (architect): the bundle budget deserves a design-phase measurement, not only a done-item.** An eager result screen (tree, tabs, card, paginator) pushed the initial total from about 563 kB to 896 kB, near the 1 MB warning; lazy `loadComponent` brought it to 697 kB. The design template's "Material components" section asks for the size before; it could also ask for the size after the stubs compile, since the stubs already import every module.
- **L-4 (architect): `MatTreeNodeHarness.getText()` excludes text inside buttons**, so a tree whose node label is a selectable button reads empty through the harness; tests must read the label from the button (`MatButtonHarness`). Worth a line in `test-guide.md` once a second tree exists.
- **L-5 (architect): the Write tool refuses to overwrite a file that was only read through Bash `cat`**, even an untracked stub left by an interrupted attempt; reading with the Read tool (or deleting the file first) is required. A resumed design run should read leftovers with Read.

- **L-6 (test-author): a "nothing is shown" criterion cannot go red alone.** AC-2's negative half (no link on a RUNNING or FAILED row) reaches no stub, since `resultLink()` is read only in the COMPLETED case, and AC-10's progress bar is a plain template element. I folded the negative assertions into a test that first renders a COMPLETED row, and tested the loading bar at screen level (through `ngOnInit`). The guide could name this pattern.
- **L-7 (test-author): helpers shared by specs live in a non-spec file** (`results/result-test-data.ts`) that `tsconfig.app.json` also compiles (`src/**/*.ts`), so it must not use Vitest globals (`expect`); the guide does not say where shared builders go.
- **L-8 (test-author): `scan-screen.spec.ts` and `app.spec.ts` keep their own local `settle`** and no `provideStubSurfacing()` (older than the shared one); I added only the router providers, as the design said.
- **L-9 (test-author): `RouterTestingHarness` needs the route in the spec's own `provideRouter`**; an empty route list gives NG04002, not the stub's error.

- **L-10 (implementer): test defect in `app.routes.spec.ts` "goes back to it from a result".** The TestBed never calls `router.initialNavigation()`, and the router subscribes to location changes only there, so `Location.back()` (mock location) does not navigate and `router.url` stays `/scans/s-1`. Evidence: a temporary copy of the spec with `router.initialNavigation()` added to `beforeEach` passes (83/84 pass, the original still fails); the implementation does not influence this. Fix in the test: call `router.initialNavigation()` in `beforeEach`, or use `RouterTestingHarness`.

- **L-11 (verifier): the implementer replaced every stub but left the design phase's "Stubs (design phase): ... throw" class comments** in `results/result-screen.ts`, `folder-tree.ts`, `sets-tab.ts` and `path-copy.ts`. The `stubs` gate looks for markers such as `not implemented`, not for this prose, so it passed. Suggest: the architect writes the stub note in a form the gate can find (for example a fixed `STUB-NOTE` token), or the implementer's checklist says "remove the design phase's stub notes from doc comments".
- **L-12 (verifier): the checklist item 11 by-hand check can be done without a person, by driving a headless Chrome over the DevTools protocol.** Use the project's Node under `server/.gradle/nodejs` (it has a global `WebSocket` and `fetch`), `chrome --headless=new --remote-debugging-port=<p> --user-data-dir=<scratch>`, and `Runtime.evaluate` to click and read `textContent`. There are two caveats. A scan started through `fetch('/api/scans')` does not appear in an already-loaded list, so reload the page first. And `navigator.clipboard.writeText` rejects in an unfocused headless page ("Document is not focused"), which usefully exercises the rejection path. `Emulation.setFocusEmulationEnabled` plus `Browser.grantPermissions(clipboardReadWrite)` makes the write succeed and lets `readText()` confirm the copied text. Worth a paragraph in the component archetype's test guide.
- **L-13 (verifier): an "loaded directly at the URL (a reload)" criterion is hard to test in TestBed.** `app.routes.spec.ts` navigates in-app after the shell starts, which does not exercise a cold start at `#/scans/<id>`. Setting the mock location's URL before `router.initialNavigation()` (for example `TestBed.inject(Location).go('/scans/s-1')` or `MockPlatformLocation`) would test the cold start; the by-hand reload covered it this time.

## Entries

### DFS-049.L-1: The component archetype says routing does not exist and forbids any `package.json` change

- Status: applied
- Impact: local
- Target: `.claude/archetypes/component/archetype.md`, "Where files go" (page-shell bullet), the exclusions list and checklist item 5
- Change: state that routing exists since DFS-049 (hash URLs with `withHashLocation()` and no server fallback, routes in `app.routes.ts`, screens as routes, lazy `loadComponent` for a heavy screen); allow a component ticket to add a runtime package of the same framework release (for example `@angular/router`), while build configuration (`angular.json`, the Gradle tasks) stays out.
- Evidence: DFS-049 note L-1 and `progress.md` (design): the archetype still reads "routing does not exist yet (DFS-041 decision 2)", while decisions 1 and 7 added hash routing and `@angular/router` 22.2.4.
- Recurrences: 1 (DFS-049)
- Recommendation: apply; DFS-050, the next component ticket, would meet the same contradiction.
- Decision: applied 2026-11-05 by the user; commit `docs: apply DFS-049.L-1, L-2, L-3, L-4 and L-5`

### DFS-049.L-2: The component design checklist misses breakage from new providers and the bundle size after stubs

- Status: applied
- Impact: local
- Target: `.claude/archetypes/component/archetype.md` checklist; `.claude/archetypes/component/design.md.tmpl`, "Material components" and "Expected breakage"
- Change: add two items: (a) when a directive or injected service is added to an existing component, list under Expected breakage every existing spec that renders it without its providers (run `:server:npmTest` once after writing the stubs to get the exact list); (b) record the initial bundle size after the stubs compile, since they already import every module, and consider lazy `loadComponent` for a heavy screen.
- Evidence: DFS-049 notes L-2 and L-3: adding `RouterLink` broke `app.spec.ts` and `scan-screen.spec.ts` with "No provider found for ActivatedRoute"; an eager result screen took the initial bundle from about 563 kB to 896 kB, lazy loading brought it to 697 kB.
- Recurrences: 1 (DFS-049)
- Recommendation: apply; both are cheap and both paid off in this run.
- Decision: applied 2026-11-05 by the user; commit `docs: apply DFS-049.L-1, L-2, L-3, L-4 and L-5`

### DFS-049.L-3: The component test guide misses router and harness patterns, including the one behind defect 1

- Status: applied
- Impact: local
- Target: `.claude/archetypes/component/test-guide.md`
- Change: add short notes: (1) a "nothing is shown" criterion cannot go red alone, so fold its negative assertion into a test that first renders the positive case; (2) builders shared by specs go in a non-spec file, which `tsconfig.app.json` compiles, so it must not use Vitest globals; (3) `RouterTestingHarness` needs the route in the spec's own `provideRouter` (an empty list gives NG04002); (4) call `router.initialNavigation()` in `beforeEach`, or `Location.back()` does not navigate; (5) a cold-start-at-URL criterion sets the location (`Location.go(...)`) before `initialNavigation()`.
- Evidence: DFS-049 notes L-6, L-7, L-9, L-10 and L-13; defect 1 (implement green failure routed to test-author, fixed in 29d24ff) was exactly item 4.
- Recurrences: 1 (DFS-049)
- Recommendation: apply; item 4 would have prevented the only defect.
- Decision: applied 2026-11-05 by the user; commit `docs: apply DFS-049.L-1, L-2, L-3, L-4 and L-5`

### DFS-049.L-4: The implementer leaves the architect's stub notes in doc comments, and the stubs gate misses them

- Status: applied
- Impact: shared
- Target: `.claude/agents/implementer.md`, its checklist
- Change: add "remove the design phase's stub notes from doc comments (for example 'Stubs (design phase): ... throw')". Parked part: the architect could write those notes with a fixed token the `stubs` gate finds (changes `tools/story-gate`).
- Evidence: DFS-049 note L-11: stale comments in `results/result-screen.ts`, `folder-tree.ts`, `sets-tab.ts` and `path-copy.ts`; the `stubs` check still passed ("no stub markers in 176 production files").
- Recurrences: 1 (DFS-049)
- Recommendation: apply the checklist line; park the gate token.
- Decision: applied 2026-11-05 by the user; commit `docs: apply DFS-049.L-1, L-2, L-3, L-4 and L-5`

### DFS-049.L-5: The by-hand check on a component ticket can run in headless Chrome

- Status: applied
- Impact: local
- Target: `.claude/archetypes/component/test-guide.md`, by-hand verification
- Change: add a paragraph: the project's Node under `server/.gradle/nodejs`, `chrome --headless=new --remote-debugging-port=<p> --user-data-dir=<scratch>`, and `Runtime.evaluate` to click and read `textContent`; caveats: reload before reading a scan list started through `fetch`, `clipboard.writeText` rejects in an unfocused page, and `Emulation.setFocusEmulationEnabled` plus `Browser.grantPermissions(clipboardReadWrite)` makes it succeed.
- Evidence: DFS-049 note L-12: the verifier ran the jar check this way, with no person involved.
- Recurrences: 1 (DFS-049)
- Recommendation: apply.
- Decision: applied 2026-11-05 by the user; commit `docs: apply DFS-049.L-1, L-2, L-3, L-4 and L-5`

### DFS-049.L-6: Older specs keep a local `settle` and no `provideStubSurfacing()`

- Status: proposed
- Impact: local
- Target: `.claude/archetypes/component/test-guide.md`, or a cleanup ticket
- Change: either migrate `scan-screen.spec.ts` and `app.spec.ts` to the shared helpers, or tell test authors to leave them as they are.
- Evidence: DFS-049 note L-8.
- Recurrences: 1 (DFS-049)
- Recommendation: park; it caused no failure.
- Decision:
